package ru.futurelink.gerber.panelizer.gui;

import io.qt.core.QFileInfo;
import io.qt.core.QStringList;
import io.qt.widgets.*;
import lombok.Getter;
import ru.futurelink.gerber.panelizer.batch.BatchMerger;
import ru.futurelink.gerber.panelizer.batch.BatchSettings;
import ru.futurelink.gerber.panelizer.batch.BatchWriter;
import ru.futurelink.gerber.panelizer.canvas.Point;
import ru.futurelink.gerber.panelizer.canvas.fetaures.Feature;
import ru.futurelink.gerber.panelizer.canvas.fetaures.RoundFeature;
import ru.futurelink.gerber.panelizer.exceptions.GerberException;
import ru.futurelink.gerber.panelizer.exceptions.MergerException;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

public class ProjectManagerWidget extends QDockWidget {
    private final QTreeWidget projectTree;
    @Getter private MergerProject project;
    @Getter MergerPanel workArea;
    @Getter private boolean modified;
    @Getter private String filename;

    public final Signal1<String> projectNameChanged = new Signal1<>();

    public ProjectManagerWidget(QWidget parent, MergerPanel workArea) {
        super(parent);
        setFeatures(DockWidgetFeature.NoDockWidgetFeatures);
        setWindowTitle("Project structure");
        setMinimumWidth(300);

        this.workArea = workArea;
        this.modified = false;

        projectTree = new QTreeWidget(this);
        projectTree.setColumnCount(2);
        setWidget(projectTree);
    }

    void refresh() {
        projectTree.clear();
        projectTree.setHeaderLabels(new QStringList("Part", "Details"));

        // Add top-level items
        var batchesItem = new QTreeWidgetItem(projectTree, new QStringList("Batches"));
        var featuresItem = new QTreeWidgetItem(projectTree, new QStringList("Features"));

        // Fill project contents
        var batches = project.getBatches();
        for (var b : batches.keySet()) {
            var item = new QTreeWidgetItem(batchesItem, new QStringList(
                    batches.get(b).getName(),
                    b.toString()
            ));
            for (var bk : project.getBatchPlacements().keySet()) {
                var bi = project.getBatchPlacements().get(bk);
                if (bi.getBatchUUID().equals(b)) {
                    item.addChild(new QTreeWidgetItem(item, new QStringList(
                            String.format("%s", bk),
                            String.format("%.4f", bi.getX()) + " x " + String.format("%.4f", bi.getY())
                    )));
                }
            }
            item.setExpanded(true);
            batchesItem.addChild(item);
        }

        for (var b : project.getFeaturePlacements().keySet()) {
            var f = project.getFeaturePlacements().get(b);
            batchesItem.addChild(
                    new QTreeWidgetItem(featuresItem, new QStringList(
                            f.getFeatureClass().getSimpleName(),
                            String.format("%.4f", f.getX()) + " x " + String.format("%.4f", f.getY())
                    ))
            );
        }

        featuresItem.setExpanded(true);
        batchesItem.setExpanded(true);
    }

    public String getProjectName() {
        if (filename == null) {
            return "Untitled";
        } else {
            return new QFileInfo(filename).fileName();
        }
    }

    public void setProject(MergerProject project) {
        this.project = project;
        setModified(false);
        refresh();
    }

    public void setModified(boolean modified) {
        this.modified = modified;
        if (modified) setWindowTitle("Project structure (modified)");
        else setWindowTitle("Project structure");
    }

    // Slot
    private void addFeatureItem(Class <? extends Feature> cls, double x, double y) {
        try {
            var fp = new MergerProject.FeaturePlacement(cls, x, y);
            var constr = fp.getFeatureClass().getConstructor(UUID.class, Point.class, double.class);
            var f = constr.newInstance(UUID.randomUUID(), new Point(fp.getX(), fp.getY()), 5);
            project.addFeaturePlacement(f.getId(), fp);
            workArea.addFeature(f);
            workArea.mergeDisplayLayers();
            refresh();
        } catch (NoSuchMethodException | InvocationTargetException |
                 InstantiationException | IllegalAccessException ex) {
            ex.printStackTrace();
        }
    }

    // Slot
    private void addBatchItem(UUID batchUUID, double x, double y) throws MergerException {
        var id = project.addBatchPlacement(batchUUID, x, y);
        workArea.placeBatchInstance(id, batchUUID, x, y);
        workArea.mergeDisplayLayers();
        refresh();
    }

    // Slot
    private void deleteItem(Object obj) {
        if (obj instanceof Feature f) {
            project.getFeaturePlacements().remove(f.getId());
        } else if (obj instanceof BatchMerger.BatchInstance b) {
            project.getBatchPlacements().remove(b.getId());
        }
        refresh();
    }

    // Slot
    private void moveItem(Object obj) {
        if (obj instanceof RoundFeature f) {
            project.getFeaturePlacements().get(f.getId()).move(
                    f.getCenter().getX().doubleValue(),
                    f.getCenter().getY().doubleValue()
            );
        } else if (obj instanceof Feature f) {
            project.getFeaturePlacements().get(f.getId()).move(
                    f.getTopLeft().getX().doubleValue(),
                    f.getTopLeft().getY().doubleValue()
            );
        } else if (obj instanceof BatchMerger.BatchInstance b) {
            project.getBatchPlacements().get(b.getId()).move(
                    b.getTopLeft().getX().doubleValue(),
                    b.getTopLeft().getY().doubleValue()
            );
        }
        refresh();
    }

    final void newProject() {
        filename = null;
        project = new MergerProject(this);
        setProject(project);
        workArea.clear();
        projectNameChanged.emit(getProjectName());
    }

    final void openProject() {
        try {
            var dlg = new QFileDialog(this);
            dlg.setWindowTitle("Open project");
            dlg.setNameFilter("Panel project (*.mrg)");
            if (dlg.exec() != 0) {
                workArea.clear();
                filename = dlg.selectedFiles().get(0);
                project = MergerProject.load(this, filename);
                setProject(project);
                refresh();
                projectNameChanged.emit(getProjectName());

                // Synchronize workarea with project... should be somewhere else!!
                for (var id : project.getBatches().keySet()) {
                    workArea.addBatch(id, project.getBatches().get(id));
                }

                for (var p : project.getBatchPlacements().keySet()) {
                    var b = project.getBatchPlacements().get(p);
                    workArea.placeBatchInstance(p, b.getBatchUUID(), b.getX(), b.getY());
                }

                for (var p : project.getFeaturePlacements().keySet()) {
                    var f = project.getFeaturePlacements().get(p);
                    try {
                        var constr = f.getFeatureClass().getConstructor(UUID.class, Point.class, double.class);
                        workArea.addFeature(constr.newInstance(p, new Point(f.getX(), f.getY()), 5));
                    } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                             InvocationTargetException ex) {
                        throw new MergerException(ex.getMessage());
                    }
                }

                workArea.mergeDisplayLayers();
                setModified(false);
            }
        } catch (IOException | GerberException | MergerException e) {
            QMessageBox.critical(this, "Error...", e.getMessage());
            newProject();   // Fall back to new project
        }
    }

    final void saveProject() {
        var settings = BatchSettings.getInstance();
        try {
            if (filename != null) {
                project.save(filename, settings);
            } else {
                var dlg = new QFileDialog(this);
                dlg.setWindowTitle("Save project");
                dlg.setNameFilter("Panel project (*.mrg)");
                dlg.setDefaultSuffix(".mrg");
                if (dlg.exec() != 0) {
                    project.save(dlg.selectedFiles().get(0), settings);
                    filename = dlg.selectedFiles().get(0);
                    projectNameChanged.emit(getProjectName());
                }
            }
            setModified(false);
        } catch (GerberException | IOException e) {
            e.printStackTrace();
            QMessageBox.critical(this, "Error...", e.getMessage());
        }
    }

    final void saveProjectAs() {
        var settings = BatchSettings.getInstance();
        var dlg = new QFileDialog(this);
        dlg.setWindowTitle("Save project as...");
        dlg.setNameFilter("Panel project (*.mrg)");
        dlg.setDefaultSuffix(".mrg");
        if (dlg.exec() != 0) {
            try {
                project.save(dlg.selectedFiles().get(0), settings);
                filename = dlg.selectedFiles().get(0);
                setModified(false);
                projectNameChanged.emit(getProjectName());
            } catch (GerberException | IOException e) {
                e.printStackTrace();
                QMessageBox.critical(this, "Error...", e.getMessage());
            }
        }
    }

    final void addBatch() {
        try {
            var dlg = new QFileDialog(this);
            dlg.setWindowTitle("Add batch ZIP...");
            dlg.setNameFilter("Gerber ZIP archive (*.zip)");
            dlg.setDefaultSuffix(".zip");
            if (dlg.exec() != 0) {
                var id = project.addBatchZIP(dlg.selectedFiles().get(0));
                workArea.addBatch(id, project.getBatches().get(id));
                refresh();
            }
        } catch (IOException | GerberException e) {
            QMessageBox.critical(this, "Error...", e.getMessage());
        }
    }

    void export() {
        var settings = BatchSettings.getInstance();
        var dlg = new QFileDialog(this);
        dlg.setWindowTitle("Export merged batch ZIP...");
        dlg.setNameFilter("Gerber ZIP archive (*.zip)");
        dlg.setDefaultSuffix(".zip");
        if (dlg.exec() != 0) {
            try {
                workArea.getMerger().merge();
                new BatchWriter(
                        new File(dlg.selectedFiles().get(0)),
                        workArea.getMerger().getMergedBatch()
                ).write(settings);
                QMessageBox.information(this, "Export", "Gerber panel successfully exported");
            } catch (IOException | MergerException | GerberException e) {
                QMessageBox.critical(this, "Error...", e.getMessage());
            }
        }
    }

}
