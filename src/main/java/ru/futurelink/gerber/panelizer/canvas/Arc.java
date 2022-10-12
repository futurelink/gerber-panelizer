package ru.futurelink.gerber.panelizer.canvas;

import lombok.Getter;

public class Arc extends Geometry {
    @Getter final private Double i;
    @Getter final private Double j;
    @Getter private final QuadrantMode quadrantMode;

    public Arc(Point start, Point end, Double i, Double j, Interpolation interpolation, int aperture, QuadrantMode quadrantMode) {
        super(start, end, interpolation, aperture);
        this.i = i;
        this.j = j;
        this.quadrantMode = quadrantMode;
    }

    @Override
    public String toString() {
        return String.format("Arc to (%s) with I=%f, J=%f aperture %d", getStart(), i, j, getAperture());
    }
}
