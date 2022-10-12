package ru.futurelink.gerber.panelizer.canvas;

import java.util.ArrayList;
import java.util.List;

public class Line extends Geometry {
    public List<Range> pierces;

    public Line(Point start, Point end, int aperture) {
        super(start, end, Interpolation.LINEAR, aperture);
        pierces = new ArrayList<>();
    }

    public static Line fromRange(Range r, int aperture) {
        return new Line(r.getStart(), r.getEnd(), aperture);
    }

    public final void clean() {
        pierces.clear();
    }

    public final void addPierces(List<Range> pierces) {
        if (pierces != null) {
            this.pierces.addAll(pierces);
            // Here we need to apply sorting, pierces that are closer to line's first
            // point should go first. That order is used when sub-lines are generated
            // and makes life easier.
            this.pierces.sort((c1, c2) -> {
                var dp1 = getStart().distanceTo(getStart().closestOf(c1.getStart(), c1.getEnd()));
                var dp2 = getStart().distanceTo(getStart().closestOf(c2.getStart(), c2.getEnd()));
                return Double.compare(dp1, dp2);
            });
        }
    }

    public final void removePierce(int index) {
        this.pierces.remove(index);
    }

    public final List<Line> subLines() {
        var list = new ArrayList<Line>();
        if (pierces.size() == 0) {
            list.add(new Line(getStart(), getEnd(), getAperture()));
        } else {
            Point p1 = getStart();
            Point p2;
            for (var p : pierces) {
                p2 = p1.closestOf(p.getStart(), p.getEnd());
                list.add(new Line(p1, p2, getAperture()));
                p1 = p2.equals(p.getStart()) ? p.getEnd() : p.getStart();
            }
            p2 = getEnd();
            list.add(new Line(p1, p2, getAperture()));
        }
        return list;
    }

    @Override
    public String toString() {
        return String.format("Line (%s) to (%s) aperture %d", getStart(), getEnd(), getAperture());
    }
}
