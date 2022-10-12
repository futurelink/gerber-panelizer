package ru.futurelink.gerber.panelizer.gui;

import io.qt.core.QPointF;
import io.qt.core.QRectF;
import io.qt.core.QSizeF;
import io.qt.gui.QColor;
import io.qt.gui.QPainter;
import io.qt.gui.QPen;
import io.qt.widgets.QWidget;
import lombok.Getter;
import ru.futurelink.gerber.panelizer.Layer;
import ru.futurelink.gerber.panelizer.batch.BatchMerger;
import ru.futurelink.gerber.panelizer.canvas.Geometry;
import ru.futurelink.gerber.panelizer.canvas.fetaures.Feature;
import ru.futurelink.gerber.panelizer.canvas.fetaures.RoundFeature;
import ru.futurelink.gerber.panelizer.drl.Excellon;
import ru.futurelink.gerber.panelizer.gbr.Gerber;
import ru.futurelink.gerber.panelizer.gbr.cmd.d.D01To03;
import ru.futurelink.gerber.panelizer.gbr.cmd.g.GCode;

public class GerberPainter extends QPainter {
    @Getter private final double scale;
    @Getter private final QPointF center;
    private final ColorSettings colorSettings = ColorSettings.getInstance();
    private final static double arcQ = 2880 / Math.PI;

    public GerberPainter(QWidget parent, double scale, QPointF center) {
        super(parent);
        this.scale = scale;
        this.center = center;
    }

    public void drawAxis(QPointF center, int width, int height) {
        setPen(colorSettings.getAxisPen());
        drawLine(
                (int) Math.round(center.x() / scale), 0,
                (int) Math.round(center.x() / scale), height);
        drawLine(
                0, (int) Math.round(center.y() / scale),
                width, (int) Math.round(center.y() / scale));
    }

    public void drawBoundingBoxMarks(QRectF box) {
        setPen(colorSettings.getMarksPen());

        // Bounding box
        var topLeft = translatedPoint(box.topLeft(), null);
        var bottomRight = translatedPoint(box.bottomRight(), null);
        var s = new QSizeF(box.width() / scale, -box.height() / scale);
        drawRect(new QRectF(topLeft, s));

        // Axis marks
        var cx = center.x() / scale;
        var cy = center.y() / scale;
        drawLine((int) topLeft.x(), (int) cy - 10, (int) topLeft.x(), (int) cy + 10);
        drawLine((int) cx - 10, (int) topLeft.y(), (int) cx + 10, (int) topLeft.y());
        drawLine((int) bottomRight.x(), (int) cy - 10, (int) bottomRight.x(), (int) cy + 10);
        drawLine((int) cx - 10, (int) bottomRight.y(), (int) cx + 10, (int) bottomRight.y());
    }

    public void drawHoles(QPainter painter, Layer layer, QPointF offset) {
        if (layer instanceof Excellon e) {
            var hi = e.holes();
            while (hi.hasNext()) {
                var h = hi.next();
                var c = translatedPoint(
                        h.getCenter().getX().doubleValue(),
                        h.getCenter().getY().doubleValue(),
                        offset
                );
                var dia = h.getDiameter() / scale / 2;

                painter.setPen(colorSettings.getDrillPen());
                painter.drawEllipse(c, dia, dia);
            }
        }
    }

    public void drawBatchOutline(QPainter painter, BatchMerger.BatchInstance b, boolean selected) {
        var rect = new QRectF(
                Math.round((b.getTopLeft().getX().doubleValue() + center.x()) / scale),
                -Math.round((b.getTopLeft().getY().doubleValue() - center.y()) / scale),
                Math.round(b.getBatch().width() / scale),
                Math.round(b.getBatch().height() / scale));
        var textRect = painter.boundingRect(rect, b.getBatch().getName());

        // Board batch title
        // -----------------
        painter.setPen(selected ? colorSettings.getSelectedPen() : colorSettings.getOutlinePen());
        painter.drawText(
                new QPointF(
                        rect.x() + rect.width() / 2 - (float) textRect.width() / 2,
                        rect.y() + rect.height() / 2
                ), b.getBatch().getName());
        var sizeString = String.format("%s", b.getSize());
        textRect = painter.boundingRect(rect, sizeString);
        painter.drawText(
                new QPointF(
                        rect.x() + rect.width() / 2 - (float) textRect.width() / 2,
                        rect.y() + rect.height() / 2 + 16
                ), sizeString);

        // Board outline
        // -------------
        var outlineLayer = b.getBatch().getLayer(Layer.Type.EdgeCuts);
        if (outlineLayer instanceof Gerber g) {
            painter.setPen(selected ? colorSettings.getSelectedPen() : new QPen(new QColor(0, 0, 0, 0), 1));
            drawGerber(g, new QPointF(b.getOffset().getX().doubleValue(), b.getOffset().getY().doubleValue()));
        }
    }

    public void drawGerber(Gerber g, QPointF offset) {
        var currentInterpolation = Geometry.Interpolation.LINEAR;
        var currentPoint = new QPointF(0, 0);
        for (var cmd : g.getContents()) {
            if (cmd instanceof D01To03 d) {
                var p = new QPointF(d.getX().doubleValue(), d.getY().doubleValue());
                switch (d.getCode()) {
                    case 1:
                        if (currentInterpolation == Geometry.Interpolation.LINEAR) {
                            drawLine(translatedPoint(currentPoint, offset), translatedPoint(p, offset));
                        } else {
                            var arcC = new QPointF(currentPoint.x() + d.getI().doubleValue(), currentPoint.y() + d.getJ().doubleValue());
                            var radius = Math.sqrt(Math.pow(currentPoint.x() - arcC.x(), 2) + Math.pow(currentPoint.y() - arcC.y(), 2));
                            var ang1 = Math.atan2(currentPoint.y() - arcC.y(), currentPoint.x() - arcC.x());
                            var ang2 = Math.atan2(p.y() - arcC.y(), p.x() - arcC.x());
                            if ((currentInterpolation == Geometry.Interpolation.CCW) && (ang2 < 0)) ang2 = ang2 + 2 * Math.PI;
                            var arcRect = new QRectF(
                                    (arcC.x() - radius + center.x() + ((offset != null) ? offset.x() : 0)) / scale,
                                    -(arcC.y() - radius - center.y() + ((offset != null) ? offset.y() : 0)) / scale,
                                    radius * 2 / scale, -radius * 2 / scale);
                            // drawRect(arcRect);
                            //drawEllipse(translatedPoint(currentPoint, offset), 2, 2);
                            // System.out.println("Ang1 = " + ang1 + "Ang2 = " + ang2 + ", Rot=" + aRot);
                            drawArc(arcRect, (int) (ang1 * arcQ), (int) ((ang2 - ang1) * arcQ));
                        }
                        break;
                    case 3: // Not supported yet
                    default: break;
                }
                currentPoint = p;
            } else if (cmd instanceof GCode gcode) {
                if (gcode.getCode() <= 3) {
                    currentInterpolation = switch (gcode.getCode()) {
                        case 1 -> Geometry.Interpolation.LINEAR;
                        case 2 -> Geometry.Interpolation.CW;
                        case 3 -> Geometry.Interpolation.CCW;
                        default -> null;
                    };
                }
            }
        }
    }

    public void drawFeature(QPainter painter, Feature f, boolean selected) {
        if (f instanceof RoundFeature m) {
            var dia = (int) Math.round(m.getRadius() / scale);
            var c = translatedPoint(m.getCenter().getX().doubleValue(), m.getCenter().getY().doubleValue());

            painter.setPen(selected ?
                    colorSettings.getSelectedPen() :
                    m.isValid() ? colorSettings.getValidFeaturePen() : colorSettings.getInvalidFeaturePen()
            );

            // Draw feature sign
            painter.drawEllipse(c, dia, dia);
            if (!m.isValid()) {
                var r = m.getRadius() / scale / 2;
                painter.drawLine((int) (c.x() - r), (int) (c.y() - r), (int) (c.x() + r), (int) (c.y() + r));
                painter.drawLine((int) (c.x() + r), (int) (c.y() - r), (int) (c.x() - r), (int) (c.y() + r));
            }
        }
    }

    private QPointF translatedPoint(double x, double y, QPointF offset) {
        if (offset == null) {
            return new QPointF((x + center.x()) / scale, -(y - center.y()) / scale);
        } else {
            return new QPointF((offset.x() + x + center.x()) / scale, -(offset.y() + y - center.y()) / scale);
        }
    }

    private QPointF translatedPoint(QPointF p, QPointF offset) {
        return translatedPoint(p.x(), p.y(), offset);
    }

    private QPointF translatedPoint(double x, double y) {
        return translatedPoint(x, y, null);
    }
}
