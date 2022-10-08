package ru.futurelink.gerber.panelizer.canvas;

import lombok.Getter;

public class Hole {
    @Getter private final Point center;
    @Getter private final Double diameter;

    public Hole(Point center, Double diameter) {
        this.center = center;
        this.diameter = diameter;
    }
}
