package ru.futurelink.gerber.panelizer.drl.holes;

import ru.futurelink.gerber.panelizer.canvas.Point;

import java.util.ArrayList;
import java.util.Iterator;

public class HoleRouted extends Hole {

    private final ArrayList<Point> points = new ArrayList<>();

    public HoleRouted(Point center, Double diameter) {
        super(center, diameter);
    }

    public HoleRouted(double x, double y, Double diameter) {
        super(x, y, diameter);
    }

    public void addPoint(double x, double y) {
        points.add(new Point(x, y));
    }

    public Iterator<Point> points() {
        return points.iterator();
    }

    @Override
    public Hole offset(double xOffset, double yOffset) {
        var h = new HoleRouted(getX() + xOffset, getY() + yOffset, getDiameter());
        for (var p : points) {
            h.addPoint(p.getX() + xOffset, p.getY() + yOffset);
        }
        return h;
    }

    @Override
    public String toString() {
        return String.format("Routed hole %s, points %s", super.toString(), points);
    }
}
