package ru.futurelink.gerber.panelizer.gui.widgets.merger;

import earcut4j.Earcut;
import io.qt.core.QPointF;
import io.qt.core.QRectF;
import io.qt.gui.QPolygonF;
import io.qt.gui.QVector2D;
import lombok.Getter;
import ru.futurelink.gerber.panelizer.Layer;
import ru.futurelink.gerber.panelizer.canvas.Aperture;
import ru.futurelink.gerber.panelizer.canvas.Geometry;
import ru.futurelink.gerber.panelizer.canvas.Macro;
import ru.futurelink.gerber.panelizer.drl.Excellon;
import ru.futurelink.gerber.panelizer.drl.holes.HoleRound;
import ru.futurelink.gerber.panelizer.drl.holes.HoleRouted;
import ru.futurelink.gerber.panelizer.gbr.Gerber;
import ru.futurelink.gerber.panelizer.gbr.cmd.d.D01To03;
import ru.futurelink.gerber.panelizer.gbr.cmd.d.DAperture;
import ru.futurelink.gerber.panelizer.gbr.cmd.g.GCode;

import java.util.ArrayList;
import java.util.HashMap;

public class GerberGraphics {
    @Getter private final ArrayList<QPointF[]> triangles;
    @Getter private final ArrayList<QPolygonF> polygons;
    private final ArrayList<GerberGraphicsUtils.Line> lines;

    public GerberGraphics() {
        polygons = new ArrayList<>();
        triangles = new ArrayList<>();
        lines = new ArrayList<>();
    }

    public final void processExcellon(Layer layer, int precision) {
        if (layer instanceof Excellon e) {
            var hi = e.holes();
            while (hi.hasNext()) {
                var h = hi.next();
                var radius = h.getDiameter() / 2;
                if (h instanceof HoleRouted r) {
                    var iter = r.points();
                    ellipse(r.getX(), r.getY(), radius, radius, precision);
                    while (iter.hasNext()) {
                        var p = iter.next();
                        ellipse(p.getX(), p.getY(), radius, radius, precision);
                    }
                } else if (h instanceof HoleRound) {
                    ellipse(h.getX(), h.getY(), radius, radius, precision);
                }
            }
        }
    }

    /**
     * This is a special simplified procedure to process edge cuts.
     * Actually it creates a set polygons instead of lines that describe a contour of a board.
     *
     * @param g - Gerber
     * @param precision - lines per circle
     */
    public final void processEdgeCutsGerber(Gerber g, int precision) {
        var currentInterpolation = Geometry.Interpolation.LINEAR;
        var currentPoint = new QPointF(0, 0);
        for (var cmd : g.getContents()) {
            if (cmd instanceof D01To03 d) {
                var p = new QPointF(Math.round(d.getX() * 1000.0) / 1000.0, Math.round(d.getY() * 1000.0) / 1000.0);
                if (d.getCode() == 1) {
                    if (currentInterpolation == Geometry.Interpolation.LINEAR) {
                        lines.add(new GerberGraphicsUtils.Line(currentPoint, p, 0));
                    } else {
                        linearizeArc(currentPoint, p, d.getI(), d.getJ(), currentInterpolation, 0, precision);
                    }
                }
                currentPoint = p;
            } else if (cmd instanceof GCode gcode) {
                if (gcode.getCode() <= 3) {
                    currentInterpolation = Geometry.interpolationByCode(gcode.getCode());
                }
            }
        }

        // Close contours and add all polygons
        for (var p : GerberGraphicsUtils.closeContours(lines)) {
            triangulateAndAdd(p);
        }
    }

    /**
     * General Gerber processor.
     *
     * @param g Gerber
     * @param apertures layer apertures list
     * @param macros layer macros list
     * @param precision lines per circle approximation ratio
     */
    public final void processGerber(Gerber g,
                               final HashMap<Integer, Aperture> apertures,
                               final HashMap<String, Macro> macros,
                               int precision
    ) {
        Aperture currentAperture = null;
        var currentInterpolation = Geometry.Interpolation.LINEAR;
        var currentPoint = new QPointF(0, 0);
        QPolygonF polygon = null;
        for (var cmd : g.getContents()) {
            if (cmd instanceof D01To03 d) {
                var p = new QPointF(d.getX(), d.getY());
                switch (d.getCode()) {
                    case 1:
                        if (polygon != null) {
                            polygon.add(p);
                        } else {
                            if (currentInterpolation == Geometry.Interpolation.LINEAR) {
                                if (currentAperture != null) {
                                    lines.add(new GerberGraphicsUtils.Line(currentPoint, p, currentAperture.getMeasures().get(0)));
                                }
                            } else {
                                if (currentAperture != null) {
                                    apertureArc(currentPoint, currentInterpolation, currentAperture, d, precision);
                                }
                            }
                        }
                        break;
                    case 2:
                        if (polygon != null) polygon.add(p);
                        break;
                    case 3:
                        if (apertures != null) aperture(p, currentAperture, macros, precision);
                        break;
                    default: break;
                }
                currentPoint = p;
            } else if (cmd instanceof GCode gcode) {
                if (gcode.getCode() <= 3) {
                    currentInterpolation = Geometry.interpolationByCode(gcode.getCode());
                } else if (gcode.getCode() == 36) {
                    polygon = new QPolygonF();
                } else if (gcode.getCode() == 37) {
                    if (polygon != null) {
                        triangulateAndAdd(polygon);
                        polygon = null;
                    }
                }
            } else if ((apertures != null) && (cmd instanceof DAperture a)) {
                currentAperture = apertures.get(a.getCode());
            }
        }

        // Triangulate all lines
        for (var l : lines) {
            lineToTriangles(l.getStart(), l.getEnd(), l.getWidth());
        }
    }

    private void triangulateAndAdd(QPolygonF polygon) {
        var size = polygon.size() * 2;
        var points = new double[size];
        var n = 0;
        for (var i = 0; i < size; i+=2) {
            points[i] = polygon.get(n).x();
            points[i+1] = polygon.get(n).y();
            n++;
        }
        var tri = Earcut.earcut(points, null, 2);
        for (var i = 0; i < tri.size(); i += 3) {
            var p = new QPointF[3];
            p[0] = polygon.get(tri.get(i));
            p[1] = polygon.get(tri.get(i + 1));
            p[2] = polygon.get(tri.get(i + 2));
            triangles.add(p);
        }
    }

    public final int getPolygonsPointsCount() {
        var c = 0;
        for (var p : polygons) {
            c += p.length();
        }
        return c;
    }

    public final int getTrianglesCount() {
        return triangles.size();
    }

    public final void ellipse(QPointF center, double radiusX, double radiusY, int precision) {
        ellipse(center.x(), center.y(), radiusX, radiusY, precision);
    }

    public final void ellipse(double x, double y, double radiusX, double radiusY, int precision) {
        var ang = 0.0;
        var polygon = new QPolygonF();
        var step = Math.PI * 2 / precision;
        polygon.add(new QPointF(Math.cos(0) * radiusX + x, Math.sin(0) * radiusY + y));
        while (ang <= 2 * Math.PI) {
            polygon.add(new QPointF(Math.cos(ang) * radiusX + x, Math.sin(ang) * radiusY + y));
            ang += step;
        }
        polygons.add(polygon);
    }

    public final void apertureArc(QPointF currentPoint, Geometry.Interpolation interpolation,
                                  Aperture aperture, D01To03 d, int precision
    ) {
        linearizeArc(currentPoint,
                new QPointF(d.getX(), d.getY()),
                d.getI(), d.getJ(),
                interpolation,
                aperture.getMeasures().get(0),
                precision
        );
    }

    public final void linearizeArc(QPointF currentPoint, QPointF endPoint, double i, double j,
                                   Geometry.Interpolation interpolation, double width, int precision
    ) {
        var center = new QPointF(currentPoint.x() + i, currentPoint.y() + j);
        var radius = Math.sqrt(Math.pow(currentPoint.x() - center.x(), 2) + Math.pow(currentPoint.y() - center.y(), 2));
        var angStart = Math.atan2(currentPoint.y() - center.y(), currentPoint.x() - center.x());
        var angEnd = Math.atan2(endPoint.y() - center.y(), endPoint.x() - center.x());
        if ((interpolation == Geometry.Interpolation.CCW) && (angEnd < 0)) angEnd = angEnd + 2 * Math.PI;

        // Create array of points for arc
        var radianStep = 2 * Math.PI / precision;
        var point = currentPoint;
        var ang = angStart;
        while (ang < angEnd + radianStep) {
            if (ang > angEnd) ang = angEnd;
            var line = new GerberGraphicsUtils.Line(
                    point,
                    new QPointF(Math.cos(ang) * radius + center.x(), Math.sin(ang) * radius + center.y()),
                    width
            );
            lines.add(line);
            point = line.getEnd();
            ang += radianStep;
        }
        if (!point.equals(endPoint)) lines.add(new GerberGraphicsUtils.Line(point, endPoint, width)); // End up the line
    }

    private void aperture(final QPointF p, final Aperture a, final HashMap<String, Macro> macros, int precision) {
        if (a == null) return;

        switch (a.getMacro()) {
            case "C" -> ellipse(p, a.getMeasures().get(0) / 2, a.getMeasures().get(0) / 2, precision);
            case "O" -> ellipse(p, a.getMeasures().get(0) / 2, a.getMeasures().get(1) / 2, precision);
            case "R" -> {   // Rectangle
                var rx = a.getMeasures().get(0) / 2;
                var ry = a.getMeasures().get(1) / 2;
                polygons.add(new QPolygonF(new QRectF(p.x() - rx, p.y() - ry, rx * 2, ry * 2)));
            }
            default -> {
                if (macros != null) drawMacro(p, macros.get(a.getMacro()), a.getMeasures().toArray(Double[]::new), precision);
            }
        }
    }

    public final void drawMacro(final QPointF p, Macro macro, Double[] measures, int precision) {
        var result = macro.eval(measures);
        for (var r : result) {
            switch (r.getType()) {
                case Circle -> {
                    var radius = r.getValue(0) / 2;
                    var center = new QPointF(r.getValue(1), r.getValue(2)).add(p);
                    ellipse(center, radius, radius, precision);
                }
                case Outline -> {
                    var poly = new QPolygonF();
                    for (var i = 0; i < r.getValue(0) * 2; i += 2) {
                        poly.add(new QPointF(r.getValue(i+1), r.getValue(i+2)).add(p));
                    }
                    polygons.add(poly);
                }
                case VectorLine ->
                    lines.add(new GerberGraphicsUtils.Line(
                            new QPointF(r.getValue(1), r.getValue(2)).add(p),
                            new QPointF(r.getValue(3), r.getValue(4)).add(p),
                            r.getValue(0)
                    ));
            }
        }
    }

    public void lineToTriangles(QPointF p1, QPointF p2, double width) {
        var dirV = new QVector2D((float)(p2.x() - p1.x()), (float)(p2.y() - p1.y())).normalized();
        var offset = new QVector2D(dirV.y(), -dirV.x()).multiply((float) width / 2);

        var tri = new QPointF[3];
        tri[0] = new QPointF(p2.x() - offset.x(), p2.y() - offset.y());
        tri[1] = new QPointF(p1.x() + offset.x(), p1.y() + offset.y());
        tri[2] = new QPointF(p2.x() + offset.x(), p2.y() + offset.y());
        triangles.add(tri);

        tri = new QPointF[3];
        tri[0] = new QPointF(p1.x() - offset.x(), p1.y() - offset.y());
        tri[1] = new QPointF(p1.x() + offset.x(), p1.y() + offset.y());
        tri[2] = new QPointF(p2.x() - offset.x(), p2.y() - offset.y());
        triangles.add(tri);
    }
}
