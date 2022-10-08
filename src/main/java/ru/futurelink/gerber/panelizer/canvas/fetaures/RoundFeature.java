package ru.futurelink.gerber.panelizer.canvas.fetaures;

import lombok.Getter;
import ru.futurelink.gerber.panelizer.canvas.Point;

import java.util.UUID;

abstract public class RoundFeature extends Feature {
    @Getter private Point center;
    @Getter private final double radius;
    public RoundFeature(UUID id, Point center, double radius){
        super(id,
            new Point(center.getX().doubleValue() - radius, center.getY().doubleValue() - radius),
            new Point(center.getX().doubleValue() + radius, center.getY().doubleValue() + radius));
        this.center = center;
        this.radius = radius;
    }

    public void moveOffset(double x, double y) {
        this.center = center.offset(x, y);
        super.moveOffset(x, y);
        clean();
    }
}
