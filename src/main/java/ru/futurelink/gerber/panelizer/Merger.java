package ru.futurelink.gerber.panelizer;

import ru.futurelink.gerber.panelizer.exceptions.MergerException;

abstract public class Merger {
    public abstract Layer getLayer();
    public abstract void clean();
    public abstract void add(Layer layer, double xOffset, double yOffset) throws MergerException;
}
