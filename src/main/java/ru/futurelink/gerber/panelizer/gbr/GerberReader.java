package ru.futurelink.gerber.panelizer.gbr;

import ru.futurelink.gerber.panelizer.exceptions.GerberException;
import ru.futurelink.gerber.panelizer.gbr.cmd.*;
import ru.futurelink.gerber.panelizer.gbr.cmd.a.*;
import ru.futurelink.gerber.panelizer.gbr.cmd.d.*;
import ru.futurelink.gerber.panelizer.gbr.cmd.g.*;
import ru.futurelink.gerber.panelizer.gbr.cmd.l.*;
import ru.futurelink.gerber.panelizer.gbr.cmd.t.*;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class GerberReader {
    private final InputStream stream;

    /**
     * Parser state variables
     */
    enum State { NONE, COMMAND, GCODE, DCODE }
    private State state;
    private StringBuilder buffer;
    private String prevBuffer;

    private final Pattern XYDRegex = Pattern.compile(
            "([XYIJ]-?\\d+)([XYIJ]-?\\d+)([XYIJ]-?\\d+)?([XYIJ]-?\\d+)?D(0?[1-3])\\*?"
    );

    private final static Logger log = Logger.getLogger("GerberReader");

    public GerberReader(File file) throws IOException {
        this(new FileInputStream(file));
        log.log(Level.INFO, "Reader created with file {0}", new Object[] { file.getName() });
    }

    public GerberReader(InputStream stream) {
        this.state = State.NONE;
        this.stream = stream;
    }

    /**
     * Reads gerber from a specific stream of file and give it a name.
     * @param name gerber instance name
     */
    public final Gerber read(String name) throws IOException, GerberException {
        buffer = new StringBuilder();
        var gerber = new Gerber(name);
        var cmdCount = 0;
        var reader = new BufferedReader(new InputStreamReader(new BufferedInputStream((stream))));
        while (reader.ready()) {
            var line = reader.readLine();

            // Parse line char-by-char
            int i = 0;
            int l = line.length();
            while (i < l) {
                var chr = line.charAt(i);
                if (chr == '%') {
                    if (this.state == State.NONE) {
                        this.state = State.COMMAND;        // Start command
                    } else {
                        this.state = State.NONE;           // Close command
                        processCmd(gerber);
                        cmdCount++;
                    }
                    this.prevBuffer = buffer.toString();
                    this.buffer = new StringBuilder();
                } else if (chr == 'G') {
                    if (this.state == State.NONE) {
                        this.state = State.GCODE;          // Start G-Code
                        this.prevBuffer = buffer.toString();
                        this.buffer = new StringBuilder();
                    } else {
                        buffer.append(chr);
                    }
                } else if (chr == 'D') {
                    if (this.state == State.NONE) {
                        this.state = State.DCODE;          // Start D-Code
                        this.prevBuffer = buffer.toString();
                        this.buffer = new StringBuilder();
                    } else {
                        buffer.append(chr);
                    }
                } else if (chr == '*') {                    // Symbol '*' closes D- and G-Codes
                    if (this.state == State.GCODE) {
                        processGCode(gerber);
                        this.state = State.NONE;
                        this.prevBuffer = buffer.toString();
                        this.buffer = new StringBuilder();
                        cmdCount++;
                    } else if (this.state == State.DCODE) {
                        // If there's something in buffer then we consider it a XYD command,
                        // otherwise it's separate D command (aperture selection).
                        if (prevBuffer.isEmpty()) {
                            processDCode(gerber);
                        } else {
                            processXYD(gerber);
                        }
                        this.state = State.NONE;
                        this.prevBuffer = buffer.toString();
                        this.buffer = new StringBuilder();
                        cmdCount++;
                    } else {
                        buffer.append(chr);
                    }
                } else {
                    buffer.append(chr);
                }
                i++;
            }
            if (!buffer.isEmpty()) buffer.append('\n');
        }
        log.log(Level.INFO, "File read, {0} commands", new Object[] { cmdCount });
        log.log(Level.INFO, "Gerber size is {0} x {1}", new Object[] { gerber.getWidth(), gerber.getHeight() });
        log.log(Level.INFO, "Min is {0} x {1} max is {2} x {3}",
                new Object[] { gerber.getMinX(), gerber.getMinY(), gerber.getMaxX(), gerber.getMaxY() });
        return gerber;
    }

    private boolean hasAperture(Integer number) {
        return true;
    }

    private void processDCode(Gerber gerber) throws GerberException {
        var dCmd = Integer.parseInt(buffer.toString());
        if (hasAperture(dCmd)) {
            gerber.add(new DAperture(dCmd));
        } else {
            throw new GerberException("Aperture does not exist");
        }
    }

    private void processXYD(Gerber gerber) throws GerberException {
        var dCmd = Integer.parseInt(buffer.toString());
        var cmd = this.prevBuffer;
        var matcher = XYDRegex.matcher(cmd + "D" + dCmd);
        if (matcher.matches()) {
            if ((dCmd >= 1) && (dCmd <= 3)) {
                String x = null, y = null, i = null, j = null;
                for (var n = 1; n < matcher.groupCount(); n++) {
                    var grp = matcher.group(n);
                    if (grp != null) {
                        switch (grp.charAt(0)) {
                            case 'X' -> x = grp.substring(1);
                            case 'Y' -> y = grp.substring(1);
                            case 'I' -> i = grp.substring(1);
                            case 'J' -> j = grp.substring(1);
                        }
                    }
                }
                if ((x != null) && (y != null)) {
                    gerber.add(new D01To03(dCmd, x, y, i, j, gerber.getFS()));
                } else {
                    throw new GerberException("Invalid value for D[1-3] - no X or Y coordinates that are mandatory");
                }
            } else {
                throw new GerberException("Invalid value for D[1-3]");
            }
        }
    }

    private void processCmd(Gerber gerber) {
        var cmd = this.buffer.toString();
        if (cmd.startsWith("TF")) {
            gerber.add(TF.fromString(cmd));
        } else if (cmd.startsWith("MO")) {
            gerber.add(MO.fromString(cmd));
        } else if (cmd.startsWith("FS")) {
            gerber.add(FS.fromString(cmd));
        } else if (cmd.startsWith("LP")) {
            gerber.add(LP.fromString(cmd));
        } else if (cmd.startsWith("AD")) {
            gerber.add(AD.fromString(cmd));
        } else if (cmd.startsWith("AM")) {
            gerber.add(AM.fromString(cmd));
        } else if (cmd.startsWith("TD")) {
            gerber.add(TD.fromString(cmd));
        } else if (cmd.startsWith("TA")) {
            gerber.add(TA.fromString(cmd));
        } else if (cmd.startsWith("TO")) {
            gerber.add(TO.fromString(cmd));
        }
    }

    private  void processGCode(Gerber gerber) {
        var cmd = this.buffer.toString();
        if (cmd.startsWith("04") || // This is a comment, so may be 'G04 Some text*'
                cmd.equals("01") || // Others are exact Gxx*
                cmd.equals("02") ||
                cmd.equals("03") ||
                cmd.equals("36") ||
                cmd.equals("37") ||
                cmd.equals("75") ||
                cmd.equals("76")
        ) {
            var code = cmd.substring(0, 2);
            var param = cmd.substring(2);
            gerber.add(new GCode(Integer.parseInt(code), param));
        }
    }
}
