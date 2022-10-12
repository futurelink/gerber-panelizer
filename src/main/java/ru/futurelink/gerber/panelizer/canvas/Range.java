package ru.futurelink.gerber.panelizer.canvas;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Range {
    @Getter private final Point start;
    @Getter private final Point end;

    public Range(Point start, Point end) {
        this.start = start;
        this.end = end;
    }

    public final boolean isVertical() {
        if ((getEnd() != null) && (getStart() != null)) {
            return (getStart().getX() == getEnd().getX());
        }
        return false;
    }

    public final boolean isHorizontal() {
        if ((getEnd() != null) && (getStart() != null)) {
            return (getStart().getY() == getEnd().getY());
        }
        return false;
    }

    public double length() {
        if (isHorizontal()) {
            return Math.abs(width());
        } else if (isVertical()) {
            return Math.abs(height());
        } else {
            return Math.sqrt(Math.pow(width(),2) + Math.pow(height(), 2));
        }
    }

    public double width() {
        return getEnd().getX() - getStart().getX();
    }

    public double height() {
        return getEnd().getY() - getStart().getY();
    }

    public Point pointAtDistance(double distance) {
        if (isHorizontal()) {
            return new Point(
                    getStart().getX() < getEnd().getX() ?
                            getStart().getX() + distance :
                            getStart().getX() - distance,
                    getStart().getY());
        } else if (isVertical()) {
            return new Point(
                    getStart().getX(),
                    getStart().getY() < getEnd().getY() ?
                            getStart().getY() + distance :
                            getStart().getY() - distance
            );
        } else {
            var w = width();
            var h = height();
            var ang = Math.atan(w / h);
            // Tricky way of preserving the sign of offset...
            // The reason is when angle is extremely close to -2*pi - the atan() function returns -0.000000 that is
            // considered as non-negative value. That breaks the logic.
            var xOffset = Math.abs(distance * Math.sin(ang)) * ((w < 0) ? -1 : 1);
            var yOffset = Math.abs(distance * Math.cos(ang)) * ((h < 0) ? -1 : 1);
            return getStart().offset(xOffset, yOffset);
        }
    }

    @Override
    public String toString() {
        return String.format("Range (%s) - (%s)", start, end);
    }
}
