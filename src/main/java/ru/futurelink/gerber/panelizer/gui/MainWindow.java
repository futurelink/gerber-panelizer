package ru.futurelink.gerber.panelizer.gui;

import io.qt.core.QPointF;
import io.qt.core.QSizeF;
import io.qt.core.Qt;
import io.qt.gui.QCloseEvent;
import io.qt.gui.QKeySequence;
import io.qt.widgets.*;
import lombok.Getter;
import ru.futurelink.gerber.panelizer.batch.BatchMerger;
import ru.futurelink.gerber.panelizer.batch.BatchSettings;
import ru.futurelink.gerber.panelizer.gui.widgets.ExportSettingsDialog;
import ru.futurelink.gerber.panelizer.gui.widgets.ProjectManagerWidget;
import ru.futurelink.gerber.panelizer.gui.widgets.merger.MergerPanelWidget;

public class MainWindow extends QMainWindow {
    private final static String appName = "Gerber Panelizer";
    @Getter private final QMenuBar menuBar;
    @Getter private final MergerPanelWidget workArea;

    private final QLabel statusCoords;
    private final QLabel statusSize;
    private final QStatusBar statusBar;
    private final ProjectManagerWidget projectManager;

    public MainWindow() {
        setWindowTitle("Gerber panelizer");
        resize(1000, 700);

        workArea = new MergerPanelWidget(this, new BatchMerger("merged"));
        setCentralWidget(workArea);

        statusBar = new QStatusBar(this);
        statusCoords = new QLabel();
        statusCoords.setMinimumWidth(200);
        statusBar.addWidget(statusCoords);
        statusSize = new QLabel();
        statusSize.setMinimumWidth(200);
        statusBar.addWidget(statusSize);
        setStatusBar(statusBar);

        projectManager = new ProjectManagerWidget(this, workArea);
        projectManager.projectNameChanged.connect(name -> setWindowTitle(appName + " - " + name));
        addDockWidget(Qt.DockWidgetArea.LeftDockWidgetArea, projectManager);

        workArea.batchChanged.connect(this, "batchChanged(QSizeF)");
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
        projectManager.saveProject();
    }

    private void export() {
        projectManager.export();
    }

    private void about() {
        QMessageBox.information(this, "About",
                "Gerber panelizer by Denis Pavlov (futurelink.vl@gmail.com)\n" +
                "This software is provided as-is, successful result is not guaranteed.");
    }

    private void documentation() {

    }

    private void quit() { close(); }

    @Override
    protected void closeEvent(QCloseEvent event) {
        if (projectManager.isModified()) {
            var res = QMessageBox.question(this, "Save changes?",
                    "Project changes were not saved. Do you want to save changes and quit?",
                    new QMessageBox.StandardButtons(
                            QMessageBox.StandardButton.Yes,
                            QMessageBox.StandardButton.No,
                            QMessageBox.StandardButton.Cancel));
            if (res == QMessageBox.StandardButton.Cancel) { event.ignore(); }
            if (res == QMessageBox.StandardButton.Yes) projectManager.saveProject();
        }
    }

    // Slot
    private void showCoordinates(QPointF point) {
        statusCoords.setText(String.format("Coords: %.4f, %.4f", point.x(), point.y()));
    }

    // Slot
    private void batchChanged(QSizeF size) {
        statusSize.setText(String.format("Size: %.4f x %.4f", size.width(), size.height()));
        projectManager.setModified(true);
    }
}
