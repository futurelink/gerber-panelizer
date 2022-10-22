package ru.futurelink.gerber.panelizer.drl;

import ru.futurelink.gerber.panelizer.Layer;
import ru.futurelink.gerber.panelizer.drl.holes.Hole;

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

    public final void addHole(Hole h) {
        holes.add(h);
    }

    public final Iterator<? extends Hole> holes() {
        return holes.iterator();
    }

    public final List<? extends Hole> holesOfDiameter(double diameter) {
        return holes.stream().filter(a -> (a.getDiameter().equals(diameter))).toList();
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
