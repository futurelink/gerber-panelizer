package ru.futurelink.gerber.panelizer.gbr;

import lombok.Getter;
import ru.futurelink.gerber.panelizer.Layer;
import ru.futurelink.gerber.panelizer.Merger;
import ru.futurelink.gerber.panelizer.gbr.cmd.FS;
import ru.futurelink.gerber.panelizer.gbr.cmd.MO;
import ru.futurelink.gerber.panelizer.gbr.cmd.a.AD;
import ru.futurelink.gerber.panelizer.gbr.cmd.a.AM;
import ru.futurelink.gerber.panelizer.gbr.cmd.d.D01To03;
import ru.futurelink.gerber.panelizer.gbr.cmd.d.DAperture;
import ru.futurelink.gerber.panelizer.gbr.cmd.t.TF;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GerberMerger extends Merger {
    @Getter private final Gerber layer;

    private final static Logger log = Logger.getLogger("GerberMerger");

    public GerberMerger(Layer.Type type, String name) {
        this.layer = new Gerber(name);
        fillHeader(type, name);
    }

    @Override
    public void clean() {
        // Persist layer type and name of Gerber
        var name = layer.getName();
        var type = layer.getLayerType();
        layer.clean();
        fillHeader(type, name);
    }

    @Override
    public void add(Layer source, double xOffset, double yOffset) {
        if (source instanceof Gerber g) {
            var destinationFunction = layer.getTF(TF.FileFunction);
            var sourceFunction = g.getTF(TF.FileFunction);
            if ((destinationFunction == null) || destinationFunction.equals(g.getTF(TF.FileFunction))) {
                log.log(Level.INFO, "Adding Gerber file {0} function {1}",
                        new Object[]{source.getName(), sourceFunction});
                if (destinationFunction == null) layer.add(new TF(TF.FileFunction, sourceFunction));

                var macroSubstitutes = mergeMacros(g);
                var apertureSubstitutes = mergeApertures(g, macroSubstitutes);
                mergeCommands(g, apertureSubstitutes, BigDecimal.valueOf(xOffset), BigDecimal.valueOf(yOffset));
            } else {
                log.log(Level.WARNING, "Source and destination .FileFunction attrs differ, so they can't be merged");
            }
        }
    }

    private HashMap <String, String> mergeMacros(Gerber source) {
        log.log(Level.INFO, "Merging macros...");
        var substTable = new HashMap<String, String>();
        for (var cmd : source.getMacros()) {
            var m = (AM)cmd;
            if (!layer.hasMacro(m.getName())) {
                log.log(Level.INFO, "Added macro {0}", new Object[] { m.getName() });
                layer.add(cmd);
            } else {
                log.log(Level.INFO, "Skipped macro {0}", new Object[] { m.getName() });
            }
        }
        return substTable;
    }

    private void mergeCommands(Gerber source, HashMap<Integer, Integer> apertureSubst, BigDecimal xOffset, BigDecimal yOffset) {
        for (var cmd : source.getContents()) {
            if (cmd instanceof D01To03 d) {
                layer.add(d.move(xOffset, yOffset));
            } else if (cmd instanceof DAperture a) {
                if (apertureSubst.containsKey(a.getCode())) {
                    layer.add(new DAperture(apertureSubst.get(a.getCode())));
                } else {
                    layer.add(cmd);
                }
            } else {
                layer.add(cmd);
            }
        }
    }

    private HashMap <Integer, Integer> mergeApertures(Gerber source, HashMap<String, String> macroSubst) {
        log.log(Level.INFO, "Merging apertures...");
        var apertureMapping = new HashMap <Integer, Integer>();
        var sourceApertures = source.getApertures();

        // If value and macro of an aperture is equal but code is different - then add it
        // to a substitute table if it's completely equal then remove from apertures
        // that are to be added into destination.
        var apertureIndex = layer.getApertureLastIndex();
        for (var s : sourceApertures) {
            var sourceAperture = (AD)s;
            var destinationAperture = findAperture((AD)s);
            if (destinationAperture != null) {
                log.log(Level.FINE, "Aperture substitution {0} -> {1}",
                        new Object[] { sourceAperture.getCode(), destinationAperture.getCode() });
                apertureMapping.put(sourceAperture.getCode(), destinationAperture.getCode());
            } else {
                log.log(Level.FINE, "Aperture adding {0} -> {1}", new Object[] { sourceAperture.getCode(), apertureIndex });
                layer.add(new AD(apertureIndex, sourceAperture.getMacro(), sourceAperture.getValue()));
                apertureMapping.put(sourceAperture.getCode(), apertureIndex);
                apertureIndex++;
            }
        }

        log.log(Level.INFO, "Processed {0} apertures", new Object[] { sourceApertures.size() });

        return apertureMapping;
    }

    private AD findAperture(AD aperture) {
        var destinationApertures = layer.getApertures();
        for (var d : destinationApertures) {
            var destinationAperture = (AD)d;
            if (aperture.getValue().equals(destinationAperture.getValue()) &&
                aperture.getMacro().equals(destinationAperture.getMacro())) {
                return destinationAperture;
            }
        }
        return null;
    }

    private void fillHeader(Layer.Type type, String name) {
        layer.add(new TF(TF.GenerationSoftware, "GerberMerger", "(1.0)"));
        layer.add(new TF(TF.CreationDate, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
        layer.add(new TF(TF.ProjectId, name, UUID.randomUUID().toString()));
        layer.add(new TF(TF.SameCoordinates, "Original"));
        layer.add(new TF(TF.FilePolarity, "Positive"));
        layer.add(new TF(TF.FileFunction, layer.fileFunctionFromLayerType(type)));

        layer.add(new FS(true, true));
        layer.add(new MO(MO.Mode.MM));
    }
}
