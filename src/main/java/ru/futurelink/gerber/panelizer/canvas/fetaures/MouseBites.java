package ru.futurelink.gerber.panelizer.canvas.fetaures;

import ru.futurelink.gerber.panelizer.Layer;
import ru.futurelink.gerber.panelizer.canvas.*;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MouseBites extends RoundFeature {

    record PointGeometry(Point point, Geometry geometry) {}

    private final static double drillDistance = 1.5;
    private final static double drillDiameter = 0.6;
    private final static Logger log = Logger.getLogger("MouseBites");
    private final HashMap<Geometry, Range> intersections = new HashMap<>();
    private final ArrayList<PointGeometry> points = new ArrayList<>();
    private final Layer.Type[] affectedLayerTypes = { Layer.Type.EdgeCuts, Layer.Type.TopDrill };

    public MouseBites(UUID id, Point center, double radius) {
        super(id, center, radius);
    }

    @Override
    public final boolean isValid() {
        // Mouse bites feature must have at least 2 intersections, which means
        // that mouse bites connect at least two panels. Each intersection must have
        // both points.
        if (intersections.size() != 2) return false;
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
            // As points are sorted counter-clockwise, then
            // if next point is on the same line - then start with next point
            int start = (points.get(0).geometry == points.get(1).geometry) ? 1 : 0;
            for (var n = start; n < points.size(); n += 2) {
                var p1 = points.get(n).point;
                var p2 = points.get(((n+1) >= points.size()) ? 0 : n+1).point;
                var i = -(p1.getX() - p2.getX()) / 2;
                var j = -(p1.getY() - p2.getY()) / 2;
                g.add(new Arc(p1, p2, i, j, Geometry.Interpolation.CCW, 10, Geometry.QuadrantMode.MULTI));
                g.add(new Line(p1, p2, 10));
                log.log(Level.FINE, "Arc from {0} to {1}, I = {2}, J = {3}", new Object[]{ p1, p2, i, j });
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
        points.clear();
    }

    @Override
    public final boolean affects(Geometry g) {
        return intersections.containsKey(g);
    }

    @Override
    public final void cleanAffectedGeometry(Layer.Type type) {
        if (type == Layer.Type.EdgeCuts) {
            intersections.clear();
            points.clear();
        }
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

                        // Add both points to common array
                        points.add(new PointGeometry(intersection.getStart(), g));
                        points.add(new PointGeometry(intersection.getEnd(), g));
                    }
                }
            }

            // Sort points counter-clockwise
            points.sort((a, b) -> {
                var aAng = Math.atan2(a.point.getY() - getCenter().getY(), a.point.getX() - getCenter().getX());
                var bAng = Math.atan2(b.point().getY() - getCenter().getY(), b.point.getX() - getCenter().getX());
                return Double.compare(bAng, aAng);
            });
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
