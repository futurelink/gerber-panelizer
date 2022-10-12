package ru.futurelink.gerber.panelizer.gui;

import io.qt.gui.QColor;
import io.qt.gui.QPen;
import lombok.Getter;

public class ColorSettings {
    @Getter
    private final QPen axisPen;
    @Getter private final QPen drillPen;
    @Getter private final QPen outlinePen;
    @Getter private final QPen selectedPen;
    @Getter private final QPen validFeaturePen;
    @Getter private final QPen invalidFeaturePen;
    @Getter private final QPen marksPen;

    private static ColorSettings instance = null;

    public static ColorSettings getInstance() {
        if (instance == null) {
            instance = new ColorSettings();
        }
        return instance;
    }

    private ColorSettings() {
        axisPen = new QPen(new QColor(200, 200, 200), 1);
        drillPen = new QPen(new QColor(180, 180, 180), 1);
        outlinePen = new QPen(new QColor(0, 0, 0, 255), 1);
        selectedPen = new QPen(new QColor(0, 0, 200), 2);
        validFeaturePen = new QPen(new QColor(0, 200, 0), 1);
        invalidFeaturePen = new QPen(new QColor(200, 0, 0), 1);
        marksPen = new QPen(new QColor(200, 0, 200), 1);
    }
}

