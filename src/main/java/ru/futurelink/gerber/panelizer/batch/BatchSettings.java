package ru.futurelink.gerber.panelizer.batch;

import ru.futurelink.gerber.panelizer.Layer;
import java.util.HashMap;

public class BatchSettings {
    private final HashMap<Layer.Type, String> filePatterns;

    public BatchSettings() {
        filePatterns = new HashMap<>();
        filePatterns.put(Layer.Type.FrontCopper,    "%project%-F_Cu");
        filePatterns.put(Layer.Type.FrontMask,      "%project%-F_Mask");
        filePatterns.put(Layer.Type.FrontPaste,     "%project%-F_Paste");
        filePatterns.put(Layer.Type.FrontSilk,      "%project%-F_Silkscreen");
        filePatterns.put(Layer.Type.BackCopper,     "%project%-B_Cu");
        filePatterns.put(Layer.Type.BackMask,       "%project%-B_Mask");
        filePatterns.put(Layer.Type.BackPaste,      "%project%-B_Paste");
        filePatterns.put(Layer.Type.BackSilk,       "%project%-B_Silkscreen");
        filePatterns.put(Layer.Type.EdgeCuts,       "%project%-B_Edge_Cuts");
        filePatterns.put(Layer.Type.TopDrill,       "%project%-PTH");
        filePatterns.put(Layer.Type.BottomDrill,    "%project%-NPTH");
    }

    public String getPattern(Layer.Type layer) {
        return filePatterns.get(layer);
    }

    public String getFilename(Layer.Type layer, String batchName) {
        if ((layer == Layer.Type.TopDrill) || (layer == Layer.Type.BottomDrill)) {
            return getPattern(layer).replace("%project%", batchName) + ".drl";
        } else {
            return getPattern(layer).replace("%project%", batchName) + ".gbr";
        }
    }
}
