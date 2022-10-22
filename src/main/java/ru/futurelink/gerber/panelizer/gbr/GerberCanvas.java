package ru.futurelink.gerber.panelizer.gbr;

import lombok.Getter;
import ru.futurelink.gerber.panelizer.Layer;
import ru.futurelink.gerber.panelizer.canvas.*;
import ru.futurelink.gerber.panelizer.drl.holes.Hole;
import ru.futurelink.gerber.panelizer.drl.holes.HoleRound;
import ru.futurelink.gerber.panelizer.canvas.fetaures.Feature;
import ru.futurelink.gerber.panelizer.drl.Excellon;
import ru.futurelink.gerber.panelizer.exceptions.MergerException;
import ru.futurelink.gerber.panelizer.gbr.cmd.a.AD;
import ru.futurelink.gerber.panelizer.gbr.cmd.d.D01To03;
import ru.futurelink.gerber.panelizer.gbr.cmd.d.DAperture;
import ru.futurelink.gerber.panelizer.gbr.cmd.g.GCode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GerberCanvas {
    @Getter private final ArrayList<AD> apertures;
    @Getter private final ArrayList<Geometry> geometry;
    @Getter private final ArrayList<Hole> holes;
    @Getter private final Point size;
    @Getter private Point currentCoordinate;
    @Getter private int currentAperture;
    private Geometry.Interpolation currentInterpolation;
    private Geometry.QuadrantMode currentQuadrantMode;

    private final ArrayList<Feature> features;

    private final static Logger log = Logger.getLogger("GerberCanvas");

    public GerberCanvas() {
        this.size = new Point(300, 300);
        this.apertures = new ArrayList<>();
        this.geometry = new ArrayList<>();
        this.holes = new ArrayList<>();
        this.features = new ArrayList<>();
    }

    public final void draw(Layer layer) {
        if (layer instanceof Gerber g) drawGerber(g);
        else if (layer instanceof Excellon e) drawExcellon(e);
        recalculate(layer.getLayerType());
    }

    public final void drawExcellon(Excellon e) {
        holes.clear();
        var iter = e.holes();
        while (iter.hasNext()) {
            var h = iter.next();
            holes.add(h);
        }
    }

    public final void drawGerber(Gerber gerber) {
        log.log(Level.INFO, "Drawing Gerber in canvas...");

        apertures.clear();
        geometry.clear();
        currentCoordinate = new Point(0, 0);
        currentAperture = 0;

        for (var aperture : gerber.getApertures()) {
            apertures.add((AD)aperture);
        }
        for (var cmd : gerber.getContents()) {
            if (cmd instanceof GCode g) {
                if ((g.getCode() > 0) && (g.getCode() <= 3)) {
                    currentInterpolation = switch (g.getCode()) {
                        case 1 -> Geometry.Interpolation.LINEAR;
                        case 2 -> Geometry.Interpolation.CW;
                        case 3 -> Geometry.Interpolation.CCW;
                        default -> null;
                    };
                } else if (g.getCode() == 75) {
                    currentQuadrantMode = Geometry.QuadrantMode.MULTI;
                } else if (g.getCode() == 74) {
                    currentQuadrantMode = Geometry.QuadrantMode.SINGLE;
                }
            } else if (cmd instanceof DAperture d) {
                if (d instanceof D01To03 d2) {
                    switch (d2.getCode()) {
                        case 2 -> move(new Point(d2.getX(), d2.getY()));
                        case 1 -> {
                            if (currentInterpolation == Geometry.Interpolation.LINEAR)
                                drawLine(new Point(d2.getX(), d2.getY()));
                            else
                                drawArc(new Point(d2.getX(), d2.getY()), d2.getI(), d2.getJ(), currentQuadrantMode);
                        }
                    }
                } else {
                    currentAperture = d.getCode();
                }
            }
        }
        // Reset state
        currentInterpolation = null;
        currentAperture = 0;
        currentQuadrantMode = Geometry.QuadrantMode.SINGLE;
        currentCoordinate = new Point(0, 0);
    }

    public final void move(Point p) {
        currentCoordinate = p;
    }

    public final void drawLine(Point p) {
        geometry.add(new Line(currentCoordinate, p, currentAperture));
        move(p);
    }

    public  final void drawArc(Point p, double i, double j, Geometry.QuadrantMode quadrantMode) {
        geometry.add(new Arc(currentCoordinate, p, i, j, currentInterpolation, currentAperture, quadrantMode));
        move(p);
    }

    public final void clear() {
        //cleanFeatures();
        //features.clear();
    }

    public final void addFeature(Feature f) {
        features.add(f);
    }

    public final void recalculate(Layer.Type type) {
        log.log(Level.INFO, "Refreshing canvas...");
        for (var f : features) {
            log.log(Level.FINE, "Calculating feature {0}", new Object[] { f });
            f.cleanAffectedGeometry(type);
            for (var line : geometry) {
                f.calculateAffectedGeometry(type, line);
            }
        }
    }

    private boolean isFeaturedGeometry(Geometry g) {
        for (var f : features) {
            if (f.affects(g)) return true;
        }
        return false;
    }

    private Set<Feature> featuresAffectGeometry(Geometry g) {
        var l = new HashSet<Feature>();
        for (var f : features) {
            if (f.affects(g)) l.add(f);
        }
        return l;
    }

    public final void writeToExcellon(Excellon e) {
        for (var f : features) {
            if (f.isValid()) {
                var i = f.buildHoles();
                while (i.hasNext()) {
                    var g = i.next();
                    log.log(Level.FINE, "Adding feature hole at {0} diameter {1}",
                            new Object[] { g, g.getDiameter() });
                    e.addHole(new HoleRound(g.getX(), g.getY(), g.getDiameter()));
                }
            }
        }
    }

    public final void writeToGerber(Gerber gerber) throws MergerException {
        currentAperture = 0;
        currentInterpolation = null;
        currentQuadrantMode = Geometry.QuadrantMode.SINGLE;
        currentCoordinate = new Point(0, 0);

        log.log(Level.INFO, "Writing from canvas to Gerber...");
        if (apertures.size() == 0) log.log(Level.WARNING, "No apertures defined");
        if (geometry.size() == 0) log.log(Level.WARNING, "No geometry objects defined - canvas is empty");

        // Write apertures
        for (var aperture : apertures) gerber.add(aperture);

        // Output all geometry
        for (var g : geometry) {
            // Write command
            if (isFeaturedGeometry(g)) {
                // Get all features that affect the line and get all pierces (interruptions) in line.
                // That interruptions we need to add to a line object.
                log.log(Level.INFO, "{0} is affected by features", new Object[] { g });
                if (g instanceof Line l) {
                    l.clean();
                    var features = featuresAffectGeometry(l);
                    for (var f : features) {
                        l.addPierces(f.getPierces().get(l));
                    }
                } else if (g instanceof Arc) {
                    throw new MergerException("Arcs modified by features are not supported yet");
                }
            }
            // Write modified geometry
            writeGeometryToGerber(gerber, g);
        }

        // Output all features
        for (var f : features) {
            if (f.isValid()) {
                writeFeatureToGerber(gerber, f);
            }
        }
    }

    private void writeFeatureToGerber(Gerber gerber, Feature f) {
        var i = f.buildGeometry();
        while (i.hasNext()) {
            writeGeometryToGerber(gerber, i.next());
        }
    }

    private void writeGeometryToGerber(Gerber gerber, Geometry g) {
        // Output aperture
        if (currentAperture != g.getAperture()) {
            gerber.add(new DAperture(g.getAperture()));
            currentAperture = g.getAperture();
        }

        // Output interpolation
        if (currentInterpolation != g.getInterpolation()) {
            var str = switch(g.getInterpolation()) {
                case LINEAR -> 1;
                case CW -> 2;
                case CCW -> 3;
            };
            gerber.add(new GCode(str, null));
            currentInterpolation = g.getInterpolation();
        }

        // Quadrant mode can be applied to arcs only
        if (g instanceof Arc a) {
            if (currentQuadrantMode != a.getQuadrantMode()) {
                var str = switch (a.getQuadrantMode()) {
                    case MULTI -> 75;
                    case SINGLE -> 74;
                };
                gerber.add(new GCode(str, null));
                currentQuadrantMode = a.getQuadrantMode();
            }
        }

        // Write D1-D3 commands
        if (g instanceof Line l) {
            for (var sl : l.subLines()) {
                moveToPoint(gerber, sl.getStart());
                gerber.add(new D01To03(1, sl.getEnd().getX(), sl.getEnd().getY()));
            }
            currentCoordinate = l.getEnd();
        } else if (g instanceof Arc a) {
            moveToPoint(gerber, g.getStart());
            gerber.add(new D01To03(1, a.getEnd().getX(), a.getEnd().getY(), a.getI(), a.getJ()));
            currentCoordinate = a.getEnd();
        }
    }

    private void moveToPoint(Gerber gerber, Point p) {
        if (!currentCoordinate.equals(p)) {
            gerber.add(new D01To03(2, p.getX(), p.getY()));
            currentCoordinate = p;
        }
    }
}
