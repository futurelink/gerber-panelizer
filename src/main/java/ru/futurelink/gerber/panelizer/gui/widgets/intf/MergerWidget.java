package ru.futurelink.gerber.panelizer.gui.widgets.intf;

import ru.futurelink.gerber.panelizer.batch.Batch;
import ru.futurelink.gerber.panelizer.batch.BatchMerger;
import ru.futurelink.gerber.panelizer.canvas.fetaures.Feature;
import ru.futurelink.gerber.panelizer.exceptions.MergerException;

import java.util.UUID;

public interface MergerWidget {

    void addBatch(UUID id, Batch b);
    void addFeature(Feature f);
    void placeBatchInstance(UUID id, UUID batchUUID, double x, double y) throws MergerException;
    void mergeDisplayLayers() throws MergerException;
    void setMargin(double margin);
    void clear();
    BatchMerger getMerger();
 }
