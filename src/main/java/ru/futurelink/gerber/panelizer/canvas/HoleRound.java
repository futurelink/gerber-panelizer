package ru.futurelink.gerber.panelizer.canvas;

public class HoleRound extends Hole {
    public HoleRound(Point center, Double diameter) {
        super(center, diameter);
    }

    public HoleRound(double x, double y, Double diameter) {
        super(x, y, diameter);
    }

    @Override
    public Hole offset(double xOffset, double yOffset) {
        return new HoleRound(getX() + xOffset, getY() + yOffset, getDiameter());
    }

    @Override
    public String toString() {
        return String.format("Round hole %s", super.toString());
    }
}
