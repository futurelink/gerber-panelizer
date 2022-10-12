package ru.futurelink.gerber.panelizer.canvas.fetaures;

import ru.futurelink.gerber.panelizer.Layer;
import ru.futurelink.gerber.panelizer.canvas.*;

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

                var i = (i1.getStart().getX() - closest.getX()) / 2;
                var j = (i1.getStart().getY() - closest.getY()) / 2;
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
        var p1x = l.getStart().getX();
        var p1y = l.getStart().getY();
        var p2x = l.getEnd().getX();
        var p2y = l.getEnd().getY();
        var a = Math.pow(p2x - p1x, 2) + Math.pow(p2y - p1y, 2);
        var b = 2 * ((p2x - p1x) * (p1x - center.getX()) + (p2y - p1y) * (p1y - center.getY()));
        var c = Math.pow(center.getX(), 2) + Math.pow(center.getY(), 2) + Math.pow(p1x, 2) + Math.pow(p1y, 2) -
                2 * (center.getX() * p1x + center.getY() * p1y) - Math.pow(r, 2) ;
        var d = Math.pow(b, 2) - 4 * a * c;

        // Find roots of ax^2 + bx + c = 0
        if (d > 0) {
            var res = new Range(
                    checkIntersectionPoint(l.getStart(), l.getEnd(), (-b + Math.sqrt(d)) / (2 * a)),
                    checkIntersectionPoint(l.getStart(), l.getEnd(), (-b - Math.sqrt(d)) / (2 * a))
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
        var ix = p1.getX() + ((p2.getX() - p1.getX()) * i);
        var iy = p1.getY() + ((p2.getY() - p1.getY()) * i);
        if (inRange(ix, p1.getX(), p2.getX()) && inRange(iy, p1.getY(), p2.getY())) {
            log.log(Level.FINE, "Valid ({0},{1})", new Object[] { ix, iy });
            return new Point(ix, iy);
        } else {
            log.log(Level.FINE, "Invalid ({0},{1})", new Object[]{ix, iy});
            return null;
        }
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
