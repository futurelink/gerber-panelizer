package ru.futurelink.gerber.panelizer.batch;

import io.qt.core.QSettings;
import lombok.Getter;
import ru.futurelink.gerber.panelizer.Layer;
import java.util.HashMap;

public class BatchSettings {
    @Getter private final HashMap<Layer.Type, String> filePatterns;

    private static BatchSettings instance;

    public static BatchSettings getInstance() {
        if (instance == null) {
            instance = new BatchSettings();
        }
        return instance;
    }

    private BatchSettings() {
        filePatterns = new HashMap<>();
        loadSettings();
    }

    public final void saveSettings() {
        var s = new QSettings();
        s.beginGroup("BatchExport");
        for (var t : filePatterns.keySet()) {
            s.setValue(Layer.layerTypeName(t), filePatterns.get(t));
        }
        s.endGroup();
    }

    public final void loadSettings() {
        // Default settings
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

        // Load saved settings
        var s = new QSettings();
        s.beginGroup("BatchExport");
        for (var t : filePatterns.keySet()) {
            var val = s.value(Layer.layerTypeName(t));
            if (val != null) filePatterns.put(t, val.toString());
        }
        s.endGroup();
    }

    public final String getPattern(Layer.Type layer) {
        return filePatterns.get(layer);
    }

    public final String getFilename(Layer.Type layer, String batchName) {
        if ((layer == Layer.Type.TopDrill) || (layer == Layer.Type.BottomDrill)) {
            return getPattern(layer).replace("%project%", batchName) + ".drl";
        } else {
            return getPattern(layer).replace("%project%", batchName) + ".gbr";
        }
    }
}
