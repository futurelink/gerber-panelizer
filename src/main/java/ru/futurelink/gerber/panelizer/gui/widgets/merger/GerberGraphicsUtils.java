package ru.futurelink.gerber.panelizer.gui.widgets.merger;

import io.qt.core.QPointF;
import io.qt.gui.QPolygonF;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class GerberGraphicsUtils {
    public static class Line {
        @Getter
        private final QPointF start;
        @Getter private final QPointF end;
        @Getter private final double width;

        public Line(QPointF p1, QPointF p2, double width) {
            this.start = p1;
            this.end = p2;
            this.width = width;
        }

        @Override
        public String toString() {
            return String.format("Line S=%s E=%s W=%f", start, end, width);
        }
    }

    // Polygon-ize all lines that form closed contours.
    // Start with one random line and try to find another line that starts or ends with
    // line strip start or end point. If line that we've found fits both start and end
    // then we create a polygon. This may be quite slow procedure and end up in O(n^2)
    // in worst case...
    // Takes: list of lines
    // Returns: list of polygons
    public static List<QPolygonF> closeContours(List<Line> lines) {
        QPolygonF polygon = null;
        var polygons = new ArrayList<QPolygonF>();
        var linesMark = new HashMap<Line, Boolean>();       // Exclusion list, true = processed and excluded
        for (var line : lines) linesMark.put(line, false);  // Fill up working hash with lines
        for (var line : linesMark.keySet()) {
            if (!linesMark.get(line)) {                     // If line was not processed
                if (polygon == null) {                      // If it's a new polygon then add line
                    if (!line.start.equals(line.end)) {     // Ignore zero length lines
                        polygon = new QPolygonF();
                        polygon.add(line.start); polygon.add(line.end);
                    }
                    linesMark.put(line, true); // Exclude the line
                } else {
                    boolean changed;
                    do {
                        changed = false;
                        for (var l : linesMark.keySet()) {
                            if (!linesMark.get(l)) {            // If line was not processed
                                if (l.start.equals(l.end)) {    // Ignore lines with zero length
                                    linesMark.put(l, true);
                                    continue;
                                }

                                boolean s = false, e = false;

                                // If line is attached to line strip start
                                if (polygon.startsWith(l.start)) { polygon.insert(0, l.end); s = true; changed = true; }
                                else if (polygon.startsWith(l.end)) { polygon.insert(0, l.start);  s = true; changed = true; }

                                // If line is attached to line strip end
                                if (polygon.endsWith(l.start)) { polygon.append(l.end); e = true; changed = true; }
                                else if (polygon.endsWith(l.end)) { polygon.append(l.start); e = true; changed = true; }

                                // If line is attached to both line strip start and end then we close polygon.
                                if (s || e) {
                                    linesMark.put(l, true);  // Exclude the line
                                    if (s && e) break;      // Contour closed, nothing to do
                                }
                            }
                        }
                    } while (changed);

                    polygons.add(polygon);
                    polygon = null;
                }
            }
        }
        return polygons;
    }
}
