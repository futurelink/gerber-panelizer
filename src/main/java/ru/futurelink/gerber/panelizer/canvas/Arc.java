package ru.futurelink.gerber.panelizer.canvas;

import lombok.Getter;

import java.math.BigDecimal;

public class Arc extends Geometry {
    @Getter final private BigDecimal i;
    @Getter final private BigDecimal j;
    @Getter private final QuadrantMode quadrantMode;

    public Arc(Point start, Point end, BigDecimal i, BigDecimal j, Interpolation interpolation, int aperture, QuadrantMode quadrantMode) {
        super(start, end, interpolation, aperture);
        this.i = i;
        this.j = j;
        this.quadrantMode = quadrantMode;
    }

    public Arc(Point start, Point end, double i, double j, Interpolation interpolation, int aperture, QuadrantMode quadrantMode) {
        this(start, end, BigDecimal.valueOf(i), BigDecimal.valueOf(j), interpolation, aperture, quadrantMode);
    }

    @Override
    public String toString() {
        return String.format("Arc to (%s) with I=%f, J=%f aperture %d", getStart(), i, j, getAperture());
    }
}
