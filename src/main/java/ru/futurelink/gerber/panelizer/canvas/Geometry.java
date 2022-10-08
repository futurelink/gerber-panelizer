package ru.futurelink.gerber.panelizer.canvas;

import lombok.Getter;

public abstract class Geometry extends Range {
    public enum Interpolation { LINEAR, CW, CCW }
    public enum QuadrantMode { SINGLE, MULTI }
    @Getter private final int aperture;
    @Getter private final Interpolation interpolation;

    public Geometry(Point start, Point end, Interpolation interpolation, int aperture){
        super(start, end);
        this.aperture = aperture;
        this.interpolation = interpolation;
    }
}
