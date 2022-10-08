package ru.futurelink.gerber.panelizer.drl;

import lombok.Getter;
import ru.futurelink.gerber.panelizer.Layer;
import ru.futurelink.gerber.panelizer.Merger;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ExcellonMerger extends Merger {
    @Getter private final Excellon layer;

    private final static Logger log = Logger.getLogger("ExcellonMerger");

    public ExcellonMerger(Layer.Type type, String name) {
        layer = new Excellon(name);
    }

    @Override
    public final void add(Layer source, double xOffset, double yOffset) {
        if (source instanceof Excellon e) {
            log.log(Level.INFO, "Adding Excellon file {0}", new Object[]{source.getName()});
            var holes = e.holes();
            while (holes.hasNext()) {
                var h = holes.next();
                layer.addHole(h.getCenter().offset(xOffset, yOffset), h.getDiameter());
            }
        }
    }

    @Override
    public void clean() {
        layer.clean();
    }
}
