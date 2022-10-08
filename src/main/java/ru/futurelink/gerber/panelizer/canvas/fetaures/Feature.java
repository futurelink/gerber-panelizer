package ru.futurelink.gerber.panelizer.canvas.fetaures;

import lombok.Getter;
import ru.futurelink.gerber.panelizer.Layer;
import ru.futurelink.gerber.panelizer.canvas.Geometry;
import ru.futurelink.gerber.panelizer.canvas.Hole;
import ru.futurelink.gerber.panelizer.canvas.Point;
import ru.futurelink.gerber.panelizer.canvas.Range;

import java.util.*;

abstract public class Feature {
    @Getter private final UUID id;
    private final ArrayList<Geometry> affectedGeometry;
    @Getter private final HashMap<Geometry, List<Range>> pierces;
    @Getter protected Point topLeft;
    @Getter protected Point bottomRight;

    public Feature(UUID id, Point topLeft, Point bottomRight) {
        this.id = id;
        this.topLeft = topLeft;
        this.bottomRight = bottomRight;
        this.affectedGeometry = new ArrayList<>();
        this.pierces = new HashMap<>();
    }

    public Iterator<Geometry> affectedGeometry() { return affectedGeometry.iterator(); }

    protected void addAffectedGeometry(Geometry g) {
        affectedGeometry.add(g);
    }

    protected void addPiercing(Geometry g, Range r) {
        if (!pierces.containsKey(g)) pierces.put(g, new ArrayList<>());
        pierces.get(g).add(r);
    }

    public void moveOffset(double x, double y) {
        this.topLeft = new Point(topLeft.getX().doubleValue() + x, topLeft.getY().doubleValue() + y);
        this.bottomRight = new Point(bottomRight.getX().doubleValue() + x, bottomRight.getY().doubleValue() + x);
    }

    abstract public void clean();
    abstract public boolean affects(Geometry g);
    abstract public void cleanAffectedGeometry(Layer.Type type);
    abstract public void calculateAffectedGeometry(Layer.Type type, Geometry g);
    abstract public Set<Layer.Type> affectedLayerTypes();
    abstract public boolean isValid();
    abstract public Iterator<Geometry> buildGeometry();
    abstract public Iterator<Hole> buildHoles();
}
