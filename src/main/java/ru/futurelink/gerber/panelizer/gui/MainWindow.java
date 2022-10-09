package ru.futurelink.gerber.panelizer.gui;

import io.qt.core.QPointF;
import io.qt.core.Qt;
import io.qt.gui.QKeySequence;
import io.qt.widgets.*;
import lombok.Getter;
import ru.futurelink.gerber.panelizer.batch.BatchMerger;
import ru.futurelink.gerber.panelizer.batch.BatchSettings;

public class MainWindow extends QMainWindow {
    private final static String appName = "Gerber Panelizer";
    @Getter private final QMenuBar menuBar;
    @Getter private final MergerPanel workArea;
    private final QStatusBar statusBar;
    private final ProjectManagerWidget projectManager;
    private final BatchSettings batchSettings;

    //private MergerProject project;

    public MainWindow() {
        setWindowTitle("Gerber panelizer");
        resize(1000, 700);

        batchSettings = BatchSettings.getInstance();

        workArea = new MergerPanel(this, new BatchMerger("merged"));
        setCentralWidget(workArea);

        statusBar = new QStatusBar(this);
        setStatusBar(statusBar);

        projectManager = new ProjectManagerWidget(this, workArea);
        addDockWidget(Qt.DockWidgetArea.LeftDockWidgetArea, projectManager);

        workArea.mouseMoved.connect(this, "showCoordinates(QPointF)");
        workArea.deleteItem.connect(projectManager, "deleteItem(Object)");
        workArea.moveItem.connect(projectManager, "moveItem(Object)");
        workArea.addFeatureItem.connect(projectManager, "addFeatureItem(Class, double, double)");
        workArea.addBatchItem.connect(projectManager, "addBatchItem(UUID, double, double)");

        var fileMenu = new QMenu("File");
        fileMenu.addAction("New project", QKeySequence.fromString("Ctrl+N"), projectManager::newProject);
        fileMenu.addAction("Open project", QKeySequence.fromString("Ctrl+O"), projectManager::openProject);
        fileMenu.addAction("Save project", QKeySequence.fromString("Ctrl+S"), this::saveProject);
        fileMenu.addAction("Save project as...", projectManager::saveProjectAs);
        fileMenu.addSeparator();
        fileMenu.addAction("Add Gerber ZIP", QKeySequence.fromString("Ctrl+A"), projectManager::addBatch);
        fileMenu.addAction("Export merged panel ZIP", QKeySequence.fromString("Ctrl+E"), this::export);
        fileMenu.addSeparator();
        fileMenu.addAction("Quit", QKeySequence.fromString("Ctrl+Q"), this::quit);

        var settingsMenu = new QMenu("Settings");
        settingsMenu.addAction("Export settings", this::exportSettings);

        var helpMenu = new QMenu("Help");
        helpMenu.addAction("Online documentation", this::documentation);
        helpMenu.addSeparator();
        helpMenu.addAction("About", this::about);

        menuBar = new QMenuBar(this);
        menuBar.addMenu(fileMenu);
        menuBar.addMenu(settingsMenu);
        menuBar.addMenu(helpMenu);
        setMenuBar(menuBar);

        try {
            projectManager.newProject();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void exportSettings() {
        new ExportSettingsDialog(this).exec();
    }

    private void saveProject() {
        projectManager.saveProject(batchSettings);
    }

    private void export() {
        projectManager.export(batchSettings);
    }

    private void about() {
        QMessageBox.information(this, "About",
                "Gerber panelizer by Denis Pavlov (futurelink.vl@gmail.com)\n" +
                "This software is provided as-is, successful result is not guaranteed.");
    }

    private void documentation() {

    }

    private void quit() {
        close();
    }

    // Slot
    public void showCoordinates(QPointF point) {
        statusBar.showMessage(String.format("Coords: %.4f, %.4f", point.x(), point.y()));
    }
}
