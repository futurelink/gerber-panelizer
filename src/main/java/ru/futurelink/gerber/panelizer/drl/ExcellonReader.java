package ru.futurelink.gerber.panelizer.drl;

import ru.futurelink.gerber.panelizer.drl.holes.HoleRound;
import ru.futurelink.gerber.panelizer.drl.holes.HoleRouted;
import ru.futurelink.gerber.panelizer.canvas.Point;

import java.io.*;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class ExcellonReader {
    private final InputStream stream;

    enum State { HEADER, BODY }

    private State state;

    private final static Logger log = Logger.getLogger("ExcellonReader");

    private final Pattern toolTableRegex = Pattern.compile("T(\\d+)C(\\d+\\.\\d*).*");
    private final Pattern toolRegex = Pattern.compile("T(\\d+).*");
    private final Pattern holeRegex = Pattern.compile("^(G\\d+)? *([XY]-?\\d+\\.?\\d*) *([XY]-?\\d+\\.?\\d*).*$");

    public ExcellonReader(File file) throws IOException {
        this(new FileInputStream(file));
        log.log(Level.INFO, "Reader created with file {0}", new Object[] { file.getName() });
    }

    public ExcellonReader(InputStream stream) {
        this.state = State.HEADER;
        this.stream = stream;
    }

    public Excellon read(String name) throws IOException {
        var excellon = new Excellon(name);
        var reader = new BufferedReader(new InputStreamReader(new BufferedInputStream((stream))));
        var currentTool = 0;
        var currentPoint = new Point(0, 0);
        HoleRouted currentRoutedHole = null;
        var toolTable = new HashMap<Integer, Double>();
        while (reader.ready()) {
            var line = reader.readLine();
            if (state == State.HEADER) {
                if (line.equals("%")) {
                    state = State.BODY;
                } else {
                    var matcher = toolTableRegex.matcher(line);
                    if (matcher.matches()) {
                        var t = Integer.parseInt(matcher.group(1));
                        var c = Double.parseDouble(matcher.group(2));
                        toolTable.put(t, c);
                        log.log(Level.FINE, "Found tool {0} diameter {1}", new Object[] { t, c });
                    }
                }
            } else if (state == State.BODY) {
                switch (line) {
                    case "G05" -> // Turn off routing mode
                            currentRoutedHole = null;
                    case "M15" -> { // Drill down: creates a routed hole
                        currentRoutedHole = new HoleRouted(currentPoint, toolTable.get(currentTool));
                        excellon.addHole(currentRoutedHole);
                        log.log(Level.FINE, "Found {0}", new Object[]{currentRoutedHole});
                    }
                    case "M16" -> // Drill up: ends up a routed hole
                        currentRoutedHole = null;
                    default -> {
                        var holeMatcher = holeRegex.matcher(line);
                        if (holeMatcher.matches()) {
                            Integer code = null;
                            Double x = null, y = null;
                            for (var n = 1; n <= holeMatcher.groupCount(); n++) {
                                var grp = holeMatcher.group(n);
                                if (grp != null) {
                                    switch (grp.charAt(0)) {
                                        case 'G' -> code = Integer.parseInt(grp.substring(1));
                                        case 'X' -> x = Double.parseDouble(grp.substring(1));
                                        case 'Y' -> y = Double.parseDouble(grp.substring(1));
                                    }
                                }
                            }

                            // Should have both X and Y coordinates
                            if ((x != null) && (y != null)) {
                                currentPoint = new Point(x, y);
                                if (code == null) {
                                    if (currentRoutedHole == null) {
                                        excellon.addHole(new HoleRound(currentPoint, toolTable.get(currentTool)));
                                        log.log(Level.FINE, "Found hole at {0} diameter {1}",
                                                new Object[]{currentPoint, toolTable.get(currentTool)});
                                    } else {
                                        log.log(Level.WARNING, "Drill cannot be used in routing mode");
                                    }
                                } else {
                                    if (currentRoutedHole != null) { // Add point to current routed hole
                                        currentRoutedHole.addPoint(currentPoint.getX(), currentPoint.getY());
                                        log.log(Level.FINE, "Add point to {0}", new Object[]{currentRoutedHole});
                                    }
                                }
                            } else {
                                log.log(Level.WARNING, "Command is incomplete: " + line);
                            }
                            continue;
                        }
                        var matcher = toolRegex.matcher(line);
                        if (matcher.matches()) {
                            currentTool = Integer.parseInt(matcher.group(1));
                            log.log(Level.FINE, "Switch to tool {0}", new Object[]{currentTool});
                            continue;
                        }
                    }
                }
            }
        }
        return excellon;
    }
}
