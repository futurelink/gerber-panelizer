package ru.futurelink.gerber.panelizer.gbr;

import ru.futurelink.gerber.panelizer.exceptions.GerberException;
import ru.futurelink.gerber.panelizer.gbr.cmd.d.D01To03;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GerberWriter {
    private final OutputStream stream;
    private final static Logger log = Logger.getLogger("GerberWriter");

    public GerberWriter(OutputStream stream) {
        this.stream = stream;
    }

    public void write(Gerber gerber) throws IOException, GerberException {
        if (gerber.getFS() == null) throw new GerberException("Undefined format (FS command is not given)");

        var writer = new BufferedWriter(new OutputStreamWriter(stream));
        // write header
        for (var cmd : gerber.getHeader()) {
            writer.write(cmd.toString());
            writer.write("\n");
        }

        // write macro definitions
        for (var cmd : gerber.getMacros()) {
            writer.write(cmd.toString());
            writer.write("\n");
        }

        // write apertures dictionary
        for (var cmd : gerber.getApertures()) {
            writer.write(cmd.toString());
            writer.write("\n");
        }

        // then other commands
        for (var cmd : gerber.getContents()) {
            if (cmd instanceof D01To03) {
                writer.write(((D01To03)cmd).toString(gerber.getFS()));
            } else {
                writer.write(cmd.toString());
            }
            writer.write("\n");
        }
        writer.flush();
        log.log(Level.INFO, "Written Gerber to stream");
    }
}
