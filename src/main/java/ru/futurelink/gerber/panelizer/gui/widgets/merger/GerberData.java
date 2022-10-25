package ru.futurelink.gerber.panelizer.gui.widgets.merger;

import lombok.Getter;
import ru.futurelink.gerber.panelizer.Layer;
import ru.futurelink.gerber.panelizer.batch.BatchMerger;
import ru.futurelink.gerber.panelizer.canvas.Aperture;
import ru.futurelink.gerber.panelizer.canvas.Macro;
import ru.futurelink.gerber.panelizer.gbr.Gerber;
import ru.futurelink.gerber.panelizer.gbr.cmd.a.AD;
import ru.futurelink.gerber.panelizer.gbr.cmd.a.AM;

import java.util.ArrayList;
import java.util.HashMap;

public class GerberData {
    @Getter private final BatchMerger merger;
    private final HashMap<Layer.Type, HashMap<Integer, Aperture>> apertures;
    private final HashMap<Layer.Type, HashMap<String, Macro>> macros;

    public GerberData(BatchMerger m) {
        merger = m;
        apertures = new HashMap<>();
        macros = new HashMap<>();
    }

    public HashMap<Integer, Aperture> getApertures(Layer.Type type) {
        return apertures.get(type);
    }

    public HashMap<String, Macro> getMacros(Layer.Type type) {
        return macros.get(type);
    }

    public void loadApertures(Layer.Type type) {
        var layer = merger.getMergedBatch().getLayer(type);
        if (layer instanceof Gerber g) {
            apertures.put(type, new HashMap<>());
            for (var cmd : g.getApertures()) {
                if (cmd instanceof AD a)
                    apertures.get(type).put(a.getCode(), new Aperture(a.getMacro(), a.getValue()));
            }
        }
    }

    public void loadMacros(Layer.Type type) {
        var layer = merger.getMergedBatch().getLayer(type);
        if (layer instanceof Gerber g) {
            macros.put(type, new HashMap<>());
            for (var cmd : g.getMacros()) {
                if (cmd instanceof AM a) {
                    var t = new ArrayList<String>();
                    a.blocks().forEachRemaining(t::add);
                    macros.get(type).put(a.getName(), new Macro(t));
                }
            }
        }
    }

    public void clear() {
        getMerger().clear();
        apertures.clear();
        macros.clear();
    }
}
