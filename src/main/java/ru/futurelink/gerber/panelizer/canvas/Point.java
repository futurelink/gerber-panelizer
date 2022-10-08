package ru.futurelink.gerber.panelizer.canvas;

import lombok.Getter;

import java.math.BigDecimal;

public class Point {
    @Getter private final BigDecimal x;
    @Getter private final BigDecimal y;

    public Point(double x, double y) {
        this.x = BigDecimal.valueOf(x);
        this.y = BigDecimal.valueOf(y);
    }

    public Point(BigDecimal x, BigDecimal y) {
        this.x = x;
        this.y = y;
    }

    public Point offset(double x, double y) {
        return new Point(getX().add(BigDecimal.valueOf(x)), getY().add(BigDecimal.valueOf(y)));
    }

    public Point closestOf(Point p1, Point p2) {
        if (p2 == null) return p1;
        if (p1 == null) return p2;
        return (distance(this, p1) < distance(this, p2)) ? p1 : p2;
    }

    public Point farthestOf(Point p1, Point p2) {
        if (p2 == null) return p1;
        if (p1 == null) return p2;
        return (distance(this, p1) > distance(this, p2)) ? p1 : p2;
    }

    public double distanceTo(Point a) {
        return distance(this, a);
    }

    public static double distance(Point a, Point b) {
        return Math.sqrt(Math.pow(a.getX().doubleValue() - b.getX().doubleValue(), 2) +
                Math.pow(a.getY().doubleValue() - b.getY().doubleValue(), 2));
    }

    @Override
    public String toString() {
        return String.format("%f,%f", x, y);
    }
}
