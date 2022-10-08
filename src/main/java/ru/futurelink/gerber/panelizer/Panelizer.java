package ru.futurelink.gerber.panelizer;


import io.qt.gui.QIcon;
import io.qt.widgets.QApplication;

import ru.futurelink.gerber.panelizer.gui.MainWindow;

import java.io.IOException;
import java.util.logging.LogManager;

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
        QApplication.setWindowIcon(new QIcon(":/app-icon.png"));
        var win = new MainWindow();
        win.show();

        QApplication.exec();
    }
}
