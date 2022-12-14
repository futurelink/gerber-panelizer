package ru.futurelink.gerber.panelizer.drl.holes;

import lombok.Getter;
import ru.futurelink.gerber.panelizer.canvas.Point;

abstract public class Hole extends Point {
    @Getter private final Double diameter;

    public Hole(Point center, Double diameter) {
        this(center.getX(), center.getY(), diameter);
    }

    public Hole(double x, double y, Double diameter) {
        super(x, y);
        this.diameter = diameter;
    }

    abstract public Hole offset(double xOffset, double yOffset);
}
