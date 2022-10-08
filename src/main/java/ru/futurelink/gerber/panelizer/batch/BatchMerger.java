package ru.futurelink.gerber.panelizer.batch;

import lombok.Getter;
import ru.futurelink.gerber.panelizer.Layer;
import ru.futurelink.gerber.panelizer.Merger;
import ru.futurelink.gerber.panelizer.canvas.Point;
import ru.futurelink.gerber.panelizer.canvas.fetaures.Feature;
import ru.futurelink.gerber.panelizer.drl.Excellon;
import ru.futurelink.gerber.panelizer.drl.ExcellonMerger;
import ru.futurelink.gerber.panelizer.exceptions.MergerException;
import ru.futurelink.gerber.panelizer.gbr.Gerber;
import ru.futurelink.gerber.panelizer.gbr.GerberCanvas;
import ru.futurelink.gerber.panelizer.gbr.GerberMerger;

import java.util.*;

public class BatchMerger {
    @Getter private final String name;
    private final HashMap<Layer.Type, Merger> layerMergers;
    private final ArrayList<Feature> features;
    private final HashMap<UUID, Batch> batches;
    private final ArrayList<BatchInstance> batchInstances;

    public static class BatchInstance {
        @Getter private final UUID id;
        @Getter private final Batch batch;
        @Getter private Point offset;

        public BatchInstance(UUID id, Batch b, Point offset) {
            this.id = id;
            this.batch = b;
            this.offset = offset;
        }

        public void moveOffset(double x, double y) {
            this.offset = offset.offset(x, y);
        }

        public final Point getTopLeft() {
            return new Point(
                    batch.left() + offset.getX().doubleValue(),
                    batch.top() + offset.getY().doubleValue()
            );
        }

        public final Point getBottomRight() {
            return new Point(
                    batch.right() + offset.getX().doubleValue(),
                    batch.bottom() + offset.getY().doubleValue()
            );
        }

        public final Point getSize() {
            return new Point(batch.width(), batch.height());
        }
    }

    public BatchMerger(String name) {
        this.name = name;
        this.layerMergers = new HashMap<>();
        this.batches = new HashMap<>();
        this.batchInstances = new ArrayList<>();
        this.features = new ArrayList<>();
    }

    public final String getBatchName(UUID id) {
        return batches.get(id).getName();
    }

    public final void addBatch(UUID id, Batch b) {
        batches.put(id, b);
    }

    public final void placeBatchInstance(UUID id, UUID batchUUID, double x, double y) throws MergerException {
        var batch = batches.get(batchUUID);
        var outline = batch.getOutlineLayer();
        if (outline == null) throw new MergerException("No outline layer detected, this Gerber can't be added!");
        addBatchInstance(id, batchUUID, x - batch.left(), y - batch.top());
    }

    public final void addBatchInstance(UUID id, UUID batchUUID, double xOffset, double yOffset) throws MergerException {
        var batch = batches.get(batchUUID);
        var outline = batch.getOutlineLayer();
        if (outline == null) throw new MergerException("No outline layer detected, this Gerber can't be added!");
        this.batchInstances.add(new BatchInstance(id, batch, new Point(xOffset, yOffset)));
    }

    private Merger getMerger(Layer.Type type, boolean clean) {
        if (!layerMergers.containsKey(type)) {
            if ((type == Layer.Type.TopDrill) || (type == Layer.Type.BottomDrill)) {
                layerMergers.put(type, new ExcellonMerger(type, name));
            } else {
                layerMergers.put(type, new GerberMerger(type, name));
            }
        } else {
            if (clean) layerMergers.get(type).clean();
        }
        return layerMergers.get(type);
    }

    public final void mergeLayer(Layer.Type type) throws MergerException {
        var merger = getMerger(type, true);
        for (var inst : batchInstances) {
            var sourcePosition = inst.getOffset();
            merger.add(
                    inst.batch.getLayer(type),
                    sourcePosition.getX().doubleValue(),
                    sourcePosition.getY().doubleValue()
            );
        }

        // Update feature data for specified layer
        updateFeatures(type);
    }

    // Merge all layers from all batches
    // ---------------------------------
    public final void merge() throws MergerException {
        for (var inst : batchInstances) {
            var iter = inst.getBatch().layers();
            while (iter.hasNext()) {
                var l = iter.next();
                mergeLayer(l.getLayerType());
            }
        }
    }

    public final Iterator<Feature> features() {
        return features.iterator();
    }

    public final void addFeature(Feature f) {
        features.add(f);
    }

    public final void removeFeature(Feature f) {
        features.remove(f);
    }

    public final void removeBatchInstance(BatchInstance b) {
        batchInstances.remove(b);
    }

    public final Set<UUID> getBatchUUIDs() {
        return batches.keySet();
    }

    public final Iterator<BatchInstance> batchInstances() {
        return batchInstances.iterator();
    }

    public final Batch getMergedBatch() {
        var batch = new Batch(name);
        for (var layer : layerMergers.keySet()) {
            batch.addLayer(layerMergers.get(layer).getLayer());
        }
        return batch;
    }

    public final void updateFeatures(Layer.Type type) throws MergerException {
        var layer = getMerger(type, false).getLayer();
        var canvas = new GerberCanvas();
        var needUpdate = false;
        for (var f : features) {
            if (f.affectedLayerTypes().contains(type)) needUpdate = true;
            canvas.addFeature(f);
        }
        if (!needUpdate) return;

        canvas.draw(layer);         // Get merged Gerber and add it to Canvas

        // Put modified layer back to merged layer
        if (layer instanceof Excellon e) {
            //getMerger(type, false).clean();
            canvas.writeToExcellon(e);
        } else if (layer instanceof Gerber g) {
            getMerger(type, false).clean();
            canvas.writeToGerber(g);
        }
    }

    public final void clear() {
        layerMergers.clear();
        features.clear();
        batches.clear();
        batchInstances.clear();
    }
}
