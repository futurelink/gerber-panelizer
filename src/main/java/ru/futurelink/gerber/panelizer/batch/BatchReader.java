package ru.futurelink.gerber.panelizer.batch;

import ru.futurelink.gerber.panelizer.exceptions.GerberException;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class BatchReader {
    private final ZipInputStream stream;

    private final static Logger log = Logger.getLogger("BatchReader");

    public BatchReader(String filename) throws FileNotFoundException {
        this(new FileInputStream(filename));
    }

    public BatchReader(InputStream stream) {
        this.stream = new ZipInputStream(stream);
    }
    public final Batch read(String name) throws IOException, GerberException {
        var batch = new Batch(name);
        var element = stream.getNextEntry();
        while (element != null) {
            if (element.getName().endsWith(".gbr")) {
                log.log(Level.INFO, "Loading Gerber from {0}", new Object[]{element.getName()});
                batch.addGerber(element.getName(), stream);
            } else if (element.getName().endsWith(".drl")) {
                log.log(Level.INFO, "Loading Excellon from {0}", new Object[]{element.getName()});
                batch.addExcellon(element.getName(), stream);
            }
            element = stream.getNextEntry();
        }
        return batch;
    }
}
