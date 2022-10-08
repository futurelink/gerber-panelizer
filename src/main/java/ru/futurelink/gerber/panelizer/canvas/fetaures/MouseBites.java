package ru.futurelink.gerber.panelizer.canvas.fetaures;

import ru.futurelink.gerber.panelizer.Layer;
import ru.futurelink.gerber.panelizer.canvas.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MouseBites extends RoundFeature {

    private final static double drillDistance = 1.5;
    private final static double drillDiameter = 0.6;
    private final static Logger log = Logger.getLogger("MouseBites");
    private final HashMap<Geometry, Range> intersections;
    private final Layer.Type[] affectedLayerTypes = { Layer.Type.EdgeCuts, Layer.Type.TopDrill };

    public MouseBites(UUID id, Point center, double radius) {
        super(id, center, radius);
        this.intersections = new HashMap<>();
    }

    @Override
    public final boolean isValid() {
        // Mouse bites feature must have at least 2 intersections, which means
        // that mouse bites connect at least two panels. Each intersection must have
        // both points.
        if (intersections.size() < 2) return false;
        for (var i : intersections.values()) {
            if ((i.getStart() == null) || (i.getEnd() == null)) return false;
        }
        return true;
    }

    @Override
    public Iterator<Geometry> buildGeometry() {
        log.log(Level.FINE, "Building feature geometry...");
        var g = new ArrayList<Geometry>();
        if (isValid()) {
            for (Geometry l1 : intersections.keySet()) {
                var i1 = intersections.get(l1);
                var iter2 = intersections.keySet().iterator();
                Point closest = null;
                while (iter2.hasNext()) {
                    var i2 = intersections.get(iter2.next());
                    if (!i1.equals(i2)) {
                        if (closest == null) {
                            closest = i1.getStart().closestOf(i2.getStart(), i2.getEnd());
                        } else {
                            closest = i1.getStart().closestOf(closest, i1.getStart().closestOf(i2.getStart(), i2.getEnd()));
                        }
                    }
                }
                if (closest == null) continue;

                var i = i1.getStart().getX().subtract(closest.getX()).doubleValue() / 2;
                var j = i1.getStart().getY().subtract(closest.getY()).doubleValue() / 2;
                log.log(Level.FINE, "Arc from {0} to {1}, I = {2}, J = {3}",
                        new Object[] { i1.getStart(), closest, i, j }
                );
                g.add(new Arc(closest, i1.getStart(), i, j, Geometry.Interpolation.CCW, l1.getAperture(), Geometry.QuadrantMode.MULTI));
            }
        }
        return g.iterator();
    }

    @Override
    public Iterator<Hole> buildHoles() {
        log.log(Level.FINE, "Building feature through holes...");
        var h = new ArrayList<Hole>();
        if (isValid()) {
            for (Geometry l1 : intersections.keySet()) {
                var i = intersections.get(l1);
                var c = i.length() / 2;
                h.add(new Hole(i.pointAtDistance(c), drillDiameter));

                // Add other holes up to the edge
                var co = drillDistance;
                while ((c - co) > drillDistance) {
                    h.add(new Hole(i.pointAtDistance(c + co), drillDiameter));
                    h.add(new Hole(i.pointAtDistance(c - co), drillDiameter));
                    co += drillDistance;
                }
            }
        }
        return h.iterator();
    }

    @Override
    public final void clean() {
        intersections.clear();
    }

    @Override
    public final boolean affects(Geometry g) {
        return intersections.containsKey(g);
    }

    @Override
    public final void cleanAffectedGeometry(Layer.Type type) {
        if (type == Layer.Type.EdgeCuts) intersections.clear();
    }

    @Override
    public final void calculateAffectedGeometry(Layer.Type type, Geometry g) {
        if (type == Layer.Type.EdgeCuts) {
            if (g instanceof Line l) {
                var intersection = calcIntersectionWithLine(l, getCenter(), getRadius());
                if (intersection != null) {
                    intersections.put(l, intersection);
                    if ((intersection.getStart() != null) && (intersection.getEnd() != null)) {
                        var p1 = l.getStart().closestOf(intersection.getStart(), intersection.getEnd());
                        var p2 = l.getEnd().closestOf(intersection.getStart(), intersection.getEnd());
                        addAffectedGeometry(new Line(l.getStart(), p1, l.getAperture()));
                        addAffectedGeometry(new Line(p2, l.getEnd(), l.getAperture()));
                        addPiercing(l, intersection);
                    }
                }
            }
        }
    }

    @Override
    public Set<Layer.Type> affectedLayerTypes() {
        return new HashSet<>(List.of(affectedLayerTypes));
    }

    private Range calcIntersectionWithLine(Line l, Point center, double r) {
        var p1 = l.getStart();
        var p2 = l.getEnd();
        var p1x = p1.getX().doubleValue();
        var p1y = p1.getY().doubleValue();
        var p2x = p2.getX().doubleValue();
        var p2y = p2.getY().doubleValue();
        var a = Math.pow(p2x - p1x, 2) + Math.pow(p2y - p1y, 2);
        var b = 2 * ((p2x - p1x) * (p1x - center.getX().doubleValue()) + (p2y - p1y) * (p1y - center.getY().doubleValue()));
        var c = Math.pow(center.getX().doubleValue(), 2) + Math.pow(center.getY().doubleValue(), 2) + Math.pow(p1x, 2) + Math.pow(p1y, 2) -
                2 * (center.getX().doubleValue() * p1x + center.getY().doubleValue() * p1y) - Math.pow(r, 2) ;
        var d = Math.pow(b, 2) - 4 * a * c;

        // Find roots of ax^2 + bx + c = 0
        if (d > 0) {
            var res = new Range(
                    checkIntersectionPoint(p1, p2, (-b + Math.sqrt(d)) / (2 * a)),
                    checkIntersectionPoint(p1, p2, (-b - Math.sqrt(d)) / (2 * a))
            );
            if ((res.getStart() != null) && (res.getEnd() != null)) {
                log.log(Level.FINE, "Got intersection, checked {0}", new Object[] { res });
                return res;
            }
        }
        return null;
    }

    /**
     * Checks the intersection point is in the range p1-p2. If point is between p1 and p2
     * returns that point, otherwise returns null.
     * @param i - line quotient (discriminate of square equation)
     */
    private Point checkIntersectionPoint(Point p1, Point p2, double i) {
        var ix = p1.getX().add(p2.getX().subtract(p1.getX()).multiply(BigDecimal.valueOf(i)));
        var iy = p1.getY().add(p2.getY().subtract(p1.getY()).multiply(BigDecimal.valueOf(i)));
        if (inRange(ix.doubleValue(), p1.getX().doubleValue(), p2.getX().doubleValue()) &&
                inRange(iy.doubleValue(), p1.getY().doubleValue(), p2.getY().doubleValue())) {
            log.log(Level.FINE, "Valid ({0},{1})", new Object[] { ix.doubleValue(), iy.doubleValue() });
            return new Point(ix, iy);
        }
        log.log(Level.FINE, "Invalid ({0},{1})", new Object[] { ix.doubleValue(), iy.doubleValue() });
        return null;
    }

    /**
     * Checks that:
     * - a <= v <= b, if a < b
     * - b <= v <= a, if b < a
     */
    private boolean inRange(double v, double a, double b) {
        return (a < b) ? ((a <= v) && (v <= b)) : ((b <= v) && (v <= a));
    }

    @Override
    public String toString() {
        return String.format("MouseBites at (%s), R=%f", getCenter(), getRadius());
    }
}
