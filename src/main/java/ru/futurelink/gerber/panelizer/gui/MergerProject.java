package ru.futurelink.gerber.panelizer.gui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.qt.core.*;
import lombok.Getter;
import ru.futurelink.gerber.panelizer.batch.Batch;
import ru.futurelink.gerber.panelizer.batch.BatchReader;
import ru.futurelink.gerber.panelizer.batch.BatchSettings;
import ru.futurelink.gerber.panelizer.batch.BatchWriter;
import ru.futurelink.gerber.panelizer.canvas.fetaures.Feature;
import ru.futurelink.gerber.panelizer.canvas.fetaures.MouseBites;
import ru.futurelink.gerber.panelizer.exceptions.GerberException;

import java.io.*;
import java.util.HashMap;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class MergerProject extends QObject {
    private final static double defaultMargin = 3;
    @Getter private double margin;
    @Getter private final HashMap<UUID, Batch> batches;
    @Getter private final HashMap<UUID, BatchPlacement> batchPlacements;
    @Getter private final HashMap<UUID, FeaturePlacement> featurePlacements;

    static class BatchPlacement {
        @Getter UUID batchUUID;
        @Getter double x;
        @Getter double y;

        public BatchPlacement(UUID batchUUID, double x, double y) {
            this.batchUUID = batchUUID;
            this.x = x;
            this.y = y;
        }

        void move(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    static class FeaturePlacement {
        @Getter Class<? extends Feature> featureClass;
        @Getter double x;
        @Getter double y;
        public FeaturePlacement(Class<? extends Feature> cls, double x, double y) {
            this.featureClass = cls;
            this.x = x;
            this.y = y;
        }

        void move(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    public final Batch getBatch(UUID batchUUID) {
        return batches.get(batchUUID);
    }

    public MergerProject(QObject parent) {
        super(parent);
        batchPlacements = new HashMap<>();
        featurePlacements = new HashMap<>();
        batches = new HashMap<>();
        margin = 3;
    }

    public static MergerProject load(QObject parent, String filename) throws IOException, GerberException {
        var mapper = new ObjectMapper();
        var project = new MergerProject(parent);
        try (var zip = new ZipFile(filename)) {
            var iter = zip.entries().asIterator();
            while (iter.hasNext()) {
                var element = iter.next();
                if (element.getName().equals("layout.json")) {
                    var stream = zip.getInputStream(element);
                    var node = mapper.readTree(stream.readAllBytes());
                    for (var bn : node.get("batches")) {
                        var name = bn.get("name").asText();
                        var id = bn.get("id").asText();
                        if ((id != null) && (name != null)) {
                            project.batches.get(UUID.fromString(id)).setName(name);
                        }
                    }

                    for (var fn : node.get("features")) {
                        var x = fn.get("x").asDouble();
                        var y = fn.get("y").asDouble();
                        project.addFeaturePlacement(UUID.randomUUID(), new FeaturePlacement(MouseBites.class, x, y));
                    }

                    for (var pn : node.get("instances")) {
                        var id = pn.get("batchUUID").asText();
                        var x = pn.get("x").asDouble();
                        var y = pn.get("y").asDouble();
                        project.addBatchPlacement(UUID.fromString(id), x, y);
                    }

                    project.margin = node.get("margin") == null ? defaultMargin : node.get("margin").asDouble();
                } else if (element.getName().endsWith(".zip")) {
                    var uuid = element.getName().substring(0, element.getName().length() - 4);
                    project.batches.put(UUID.fromString(uuid), new BatchReader(zip.getInputStream(element)).read(""));
                }
            }
        }
        return project;
    }

    public void save(String filename, BatchSettings settings) throws IOException, GerberException {
        try (var zipStream = new ZipOutputStream(new FileOutputStream(filename))) {
            // Save imported batches
            for (var b : batches.keySet()) {
                zipStream.putNextEntry(new ZipEntry(b.toString() + ".zip"));
                new BatchWriter(zipStream, batches.get(b)).write(settings);
                zipStream.closeEntry();
            }

            // Save layout to JSON
            var mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            var layoutNode = mapper.createObjectNode();
            var batchesNode = layoutNode.putArray("batches");
            var instancesNode = layoutNode.putArray("instances");
            var featuresNode = layoutNode.putArray("features");
            for (var i : batches.keySet()) {
                var o = batchesNode.addObject();
                o.put("id", i.toString());
                o.put("name", batches.get(i).getName());
            }
            for (var i : batchPlacements.keySet()) instancesNode.addPOJO(batchPlacements.get(i));
            for (var i : featurePlacements.keySet()) featuresNode.addPOJO(featurePlacements.get(i));
            layoutNode.put("margin", margin);

            zipStream.putNextEntry(new ZipEntry("layout.json"));
            mapper.writeValue(zipStream, layoutNode);
            try {
                zipStream.closeEntry();
                zipStream.finish();
            } catch(IOException ex) {
                // TODO Ignore stream closed exception for now
            }
        }
    }

    public final UUID addBatchZIP(String filename) throws IOException, GerberException {
        var name = new QFileInfo(filename).fileName();
        var uuid = UUID.randomUUID();
        batches.put(uuid, new BatchReader(filename).read(name));
        return uuid;
    }

    public UUID addBatchPlacement(UUID batchUUID, double x, double y) {
        var id = UUID.randomUUID();
        batchPlacements.put(id, new BatchPlacement(batchUUID, x, y));
        return id;
    }

    public void addFeaturePlacement(UUID featureUUID, FeaturePlacement placement) {
        featurePlacements.put(featureUUID, placement);
    }
}
