package ru.futurelink.gerber.panelizer.batch;

import lombok.Getter;
import lombok.Setter;
import ru.futurelink.gerber.panelizer.Layer;
import ru.futurelink.gerber.panelizer.canvas.Point;
import ru.futurelink.gerber.panelizer.drl.ExcellonReader;
import ru.futurelink.gerber.panelizer.exceptions.GerberException;
import ru.futurelink.gerber.panelizer.exceptions.MergerException;
import ru.futurelink.gerber.panelizer.gbr.GerberReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;

public class Batch {
    @Getter @Setter private String name;
    private final HashMap<Layer.Type, Layer> layers;

    public Batch(String name) {
        this.name = name;
        this.layers = new HashMap<>();
    }

    public final void addGerber(String name, InputStream stream) throws GerberException, IOException {
        addLayer(new GerberReader(stream).read(name));
    }

    public final void addExcellon(String name, InputStream stream) throws IOException {
        addLayer(new ExcellonReader(stream).read(name));
    }

    public final void addLayer(Layer layer) {
        layers.put(layer.getLayerType(), layer);
    }

    public final Iterator<Layer> layers() {
        return layers.values().iterator();
    }

    public final Layer layer(Layer.Type layer) {
        return layers.get(layer);
    }

    public final Layer getOutlineLayer() {
        return getLayer(Layer.Type.EdgeCuts);
    }

    public final Layer getLayer(Layer.Type type) {
        return layers.get(type);
    }

    public final double width() {
        return Math.abs(right() - left());
    }

    public final double height() {
        return Math.abs(bottom() - top());
    }

    public final Point topLeft() {
        return new Point(left(), top());
    }

    public final Point bottomRight() {
        return new Point(right(), bottom());
    }

    public final double top() {
        return (getOutlineLayer() != null) ? getOutlineLayer().getMaxY() : 0;
    }

    public final double left() {
        return (getOutlineLayer() != null) ? getOutlineLayer().getMinX() : 0;
    }

    public final double right() {
        return (getOutlineLayer() != null) ? getOutlineLayer().getMaxX() : 0;
    }

    public final double bottom() {
        return (getOutlineLayer() != null) ? getOutlineLayer().getMinY() : 0;
    }
}
