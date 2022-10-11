package ru.futurelink.gerber.panelizer.gui;

import io.qt.core.QPointF;
import io.qt.core.QRectF;

import java.util.List;

public class Utils {

    public static class QRange {
        private final double x1;
        private final double x2;
        public QRange(double x1, double x2) {
            this.x1 = x1;
            this.x2 = x2;
        }

        public double x1() { return x1; }

        public double x2() { return x2; }

        public QRange intersection(QRange r) {
            Double ix1 = null, ix2 = null;
            if (this.contains(r.x1)) ix1 = r.x1; else if (r.contains(this.x1)) ix1 = this.x1;
            if (this.contains(r.x2)) ix2 = r.x2; else if (r.contains(this.x2)) ix2 = this.x2;
            if ((ix1 != null) && (ix2 != null)) return new QRange(ix1, ix2);
            else return null;
        }

        public boolean contains(double x) {
            return ((x >= x1) && (x <= x2)) || ((x >= x2) && (x <= x1));
        }

        public boolean contains(QRange r) {
            return (contains(r.x1) && contains(r.x2));
        }
    }

    public static List<QPointF> contourPolyline(List<QRectF> rect) {
        return null;
    }

}
