package ru.futurelink.gerber.panelizer.canvas;

import lombok.Getter;

import java.util.ArrayList;

public class Aperture {
    @Getter private final String macro;
    @Getter private final ArrayList<Double> measures;

    public Aperture(String macro, String measures) {
        this.macro = macro;
        this.measures = new ArrayList<>();
        for (var m : measures.split("X")) {
            this.measures.add(Double.parseDouble(m.trim()));
        }
    }
}
