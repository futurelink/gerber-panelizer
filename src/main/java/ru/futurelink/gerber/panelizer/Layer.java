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

    public static Layer.Type layerNameType(String name) {
        return switch(name) {
            case "Top Drill" -> Layer.Type.TopDrill;
            case "Bottom Drill" -> Layer.Type.BottomDrill;
            case "Front Copper" -> Layer.Type.FrontCopper;
            case "Front Mask" -> Layer.Type.FrontMask;
            case "Front Solder Paste" -> Layer.Type.FrontPaste;
            case "Front Silkscreen" -> Layer.Type.FrontSilk;
            case "Back Copper" -> Layer.Type.BackCopper;
            case "Back Mask" -> Layer.Type.BackMask;
            case "Back Solder Paste" -> Layer.Type.BackPaste;
            case "Back Silkscreen" -> Layer.Type.BackSilk;
            case "Outline" -> Layer.Type.EdgeCuts;
            default -> null;
        };
    }

    public final String layerTypeFileFunction(Type type) {
        return switch (type) {
            case FrontCopper -> "Copper,L1,Top";
            case FrontMask -> "Soldermask,Top";
            case FrontPaste -> "Paste,Top";
            case FrontSilk -> "Legend,Top";
            case BackCopper -> "Copper,L2,Bot";
            case BackMask -> "Soldermask,Bot";
            case BackPaste -> "Paste,Bot";
            case BackSilk -> "Legend,Bot";
            case EdgeCuts -> "Profile,NP";
            case TopDrill -> "Drill,Top";
            case BottomDrill -> "Drill,Bot";
        };
    }
}
