package ru.futurelink.gerber.panelizer.drl;

import ru.futurelink.gerber.panelizer.Layer;
import ru.futurelink.gerber.panelizer.canvas.Hole;
import ru.futurelink.gerber.panelizer.canvas.Point;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

public class Excellon extends Layer {
    private final ArrayList<Hole> holes;

    private final static Logger log = Logger.getLogger("Excellon");

    public Excellon(String name) {
        super(name);
        this.holes = new ArrayList<>();
    }

    public final void addHole(Point center, double diameter) {
        holes.add(new Hole(center, diameter));
    }

    public final Iterator<Hole> holes() {
        return holes.iterator();
    }

    public final List<Hole> holesOfDiameter(double diameter) {
        return holes.stream().filter(a -> (a.getDiameter() == diameter)).toList();
    }

    @Override
    public final Type getLayerType() {
        return Type.TopDrill;
    }

    @Override
    public final void clean() {
        super.clean();
        holes.clear();
    }
}
