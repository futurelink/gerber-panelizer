package ru.futurelink.gerber.panelizer;

import io.qt.widgets.QApplication;
import ru.futurelink.gerber.panelizer.batch.*;
import ru.futurelink.gerber.panelizer.canvas.fetaures.MouseBites;
import ru.futurelink.gerber.panelizer.canvas.Point;
import ru.futurelink.gerber.panelizer.drl.ExcellonMerger;
import ru.futurelink.gerber.panelizer.drl.ExcellonWriter;
import ru.futurelink.gerber.panelizer.gbr.*;
import ru.futurelink.gerber.panelizer.gui.MainWindow;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.LogManager;
import java.util.zip.ZipFile;

public class Panelizer {
    public static void main(String[] args) {
        try {
            LogManager.getLogManager().readConfiguration(
                    Panelizer.class.getClassLoader().getResourceAsStream("logger.properties")
            );
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        QApplication.initialize(args);
        var win = new MainWindow();
        win.show();

        QApplication.exec();
    }
}
