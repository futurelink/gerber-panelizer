package ru.futurelink.gerber.panelizer.batch;

import ru.futurelink.gerber.panelizer.Layer;
import ru.futurelink.gerber.panelizer.drl.Excellon;
import ru.futurelink.gerber.panelizer.drl.ExcellonWriter;
import ru.futurelink.gerber.panelizer.exceptions.GerberException;
import ru.futurelink.gerber.panelizer.gbr.Gerber;
import ru.futurelink.gerber.panelizer.gbr.GerberWriter;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class BatchWriter {
    private final ZipOutputStream zipStream;
    private final Batch batch;

    private final static Logger log = Logger.getLogger("BatchReader");

    public BatchWriter(File file, Batch batch) throws FileNotFoundException {
        this(new FileOutputStream(file), batch);
    }

    public BatchWriter(OutputStream zipStream, Batch batch) throws FileNotFoundException {
        this.zipStream = new ZipOutputStream(zipStream);
        this.batch = batch;
    }

    public void write(BatchSettings settings) throws IOException, GerberException {
        log.log(Level.INFO, "Writing batch...");
        var iter = batch.layers();
        while (iter.hasNext()) {
            var layer = iter.next();

            var filename = settings.getFilename(layer.getLayerType(), batch.getName());
            log.log(Level.INFO, "Writing batch file {0}", new Object[] { filename });

            zipStream.putNextEntry(new ZipEntry(filename));
            if (layer instanceof Gerber g) {
                new GerberWriter(zipStream).write(g);
            } else if (layer instanceof Excellon e) {
                new ExcellonWriter(zipStream).write(e);
            }
            zipStream.closeEntry();
        }

        zipStream.finish();
    }

}
