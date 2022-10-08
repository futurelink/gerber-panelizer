package ru.futurelink.gerber.panelizer;

import lombok.Getter;

abstract public class Layer {
    @Getter protected String name;
    @Getter protected double maxX = -99999;
    @Getter protected double minX = 99999;
    @Getter protected double maxY = -99999;
    @Getter protected double minY = 99999;

    public enum Type {
        FrontCopper, FrontMask, FrontPaste, FrontSilk,
        BackCopper, BackMask, BackPaste, BackSilk,
        EdgeCuts, TopDrill, BottomDrill
    }

    public Layer(String name) {
        this.name = name;
    }

    public void clean() {
        this.maxX = -99999;
        this.minX = 99999;
        this.maxY = -99999;
        this.minY = 99999;
    }

    abstract public Type getLayerType();

    public static String layerTypeName(Layer.Type type) {
        return switch(type) {
            case TopDrill -> "Top Drill";
            case BottomDrill -> "Bottom Drill";
            case FrontCopper -> "Front Copper";
            case FrontMask -> "Front Mask";
            case FrontPaste -> "Front Solder Paste";
            case FrontSilk -> "Front Silkscreen";
            case BackCopper -> "Back Copper";
            case BackMask -> "Back Mask";
            case BackPaste -> "Back Solder Paste";
            case BackSilk -> "Back Silkscreen";
            case EdgeCuts -> "Outline";
        };
    }
}
