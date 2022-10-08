package ru.futurelink.gerber.panelizer.drl;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ExcellonWriter {
    private final OutputStream stream;

    private final static Logger log = Logger.getLogger("ExcellonWriter");

    public ExcellonWriter(OutputStream stream) {
        this.stream = stream;
    }

    public void write(Excellon drl) throws IOException {
        log.log(Level.INFO, "Writing Excellon file...");
        var writer = new BufferedWriter(new OutputStreamWriter(stream));
        var df = new Formatter(Locale.US);

        // Analyze Excellon and build tools table
        var toolTable = new ArrayList<Double>();
        var iter = drl.holes();
        while(iter.hasNext()) {
            var h = iter.next();
            if (!toolTable.contains(h.getDiameter())) {
                toolTable.add(h.getDiameter());
            }
        }

        // Write header
        writer.write("M48\n");
        writer.write("FMAT,2\n");
        writer.write("METRIC\n");

        // Write tool table
        for (var i = 0; i < toolTable.size(); i++) {
            writer.write(new Formatter(Locale.US).format("T%dC%.3f\n", (i + 1), toolTable.get(i)).toString());
            df.flush();
        }
        writer.write("%\n");
        writer.write("G90\nG05\n");

        // Write holes grouped by tools
        for (var i = 0; i < toolTable.size(); i++) {
            writer.write("T" + (i + 1) + "\n");
            var holes = drl.holesOfDiameter(toolTable.get(i));
            for (var h : holes) {
                writer.write(new Formatter(Locale.US).format("X%.3fY%.3f\n", h.getCenter().getX(), h.getCenter().getY()).toString());
            }
        }

        // Wrote footer
        writer.write("T0\nM30\n");

        writer.flush();
    }


    public void writeTools(Set<Double> tools) {
        for (var t : tools) {
            log.log(Level.INFO, "Drill size {0}", new Object[] { t });
        }
    }
}
