package ru.futurelink.gerber.panelizer.gui;

import io.qt.core.Qt;
import io.qt.gui.QColor;
import io.qt.gui.QPen;
import lombok.Getter;

public class ColorSettings {
    @Getter private final QPen axisPen;
    @Getter private final QPen drillPen;
    @Getter private final QPen outlinePen;
    @Getter private final QPen selectedPen;
    @Getter private final QPen validFeaturePen;
    @Getter private final QPen invalidFeaturePen;
    @Getter private final QPen marksPen;
    @Getter private final QPen tracksPen;

    private static ColorSettings instance = null;

    public static ColorSettings getInstance() {
        if (instance == null) {
            instance = new ColorSettings();
        }
        return instance;
    }

    private ColorSettings() {
        axisPen = new QPen(new QColor(200, 200, 200), 1);
        drillPen = new QPen(new QColor(50, 50, 50), 1);
        drillPen.setCapStyle(Qt.PenCapStyle.RoundCap);
        tracksPen = new QPen(new QColor(180, 180, 180), 1);
        tracksPen.setCapStyle(Qt.PenCapStyle.RoundCap);
        outlinePen = new QPen(new QColor(0, 0, 0, 255), 1);
        outlinePen.setCapStyle(Qt.PenCapStyle.RoundCap);
        selectedPen = new QPen(new QColor(0, 0, 200), 2);
        validFeaturePen = new QPen(new QColor(0, 200, 0), 1);
        invalidFeaturePen = new QPen(new QColor(200, 0, 0), 1);
        marksPen = new QPen(new QColor(200, 0, 200), 1);
        marksPen.setCapStyle(Qt.PenCapStyle.RoundCap);
    }
}

