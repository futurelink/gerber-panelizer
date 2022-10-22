package ru.futurelink.gerber.panelizer.gui;

import io.qt.core.*;
import io.qt.gui.*;
import io.qt.widgets.QWidget;
import lombok.Getter;
import ru.futurelink.gerber.panelizer.Layer;
import ru.futurelink.gerber.panelizer.batch.BatchMerger;
import ru.futurelink.gerber.panelizer.canvas.*;
import ru.futurelink.gerber.panelizer.drl.holes.HoleRound;
import ru.futurelink.gerber.panelizer.drl.holes.HoleRouted;
import ru.futurelink.gerber.panelizer.canvas.fetaures.Feature;
import ru.futurelink.gerber.panelizer.canvas.fetaures.RoundFeature;
import ru.futurelink.gerber.panelizer.drl.Excellon;
import ru.futurelink.gerber.panelizer.gbr.Gerber;
import ru.futurelink.gerber.panelizer.gbr.cmd.d.D01To03;
import ru.futurelink.gerber.panelizer.gbr.cmd.d.DAperture;
import ru.futurelink.gerber.panelizer.gbr.cmd.g.GCode;

import java.util.ArrayList;
import java.util.HashMap;

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

    public void drawHoles(Layer layer, QPointF offset) {
        if (layer instanceof Excellon e) {
            var hi = e.holes();
            while (hi.hasNext()) {
                var h = hi.next();
                var c = translatedPoint(h.getX(), h.getY(), offset);
                var radius = h.getDiameter() / scale / 2;
                if (h instanceof HoleRouted r) {
                    var pen = colorSettings.getDrillPen().clone();
                    pen.setWidth((int)(radius * 2));
                    setPen(pen);
                    var iter = r.points();
                    while (iter.hasNext()) {
                        var p = iter.next();
                        var end = translatedPoint(p.getX(), p.getY(), offset);
                        drawLine(c, end);
                        c = end;
                    }
                    setPen(Qt.PenStyle.NoPen);
                } else if (h instanceof HoleRound) {
                    setPen(Qt.PenStyle.NoPen);
                    setBrush(new QBrush(colorSettings.getDrillPen().color()));
                    drawEllipse(c, radius, radius);
                    setBrush(Qt.BrushStyle.NoBrush);
                }
            }
        }
    }

    public final void drawBatchOutline(QPainter painter, BatchMerger.BatchInstance b, boolean selected) {
        var rect = new QRectF(
                Math.round((b.getTopLeft().getX() + center.x()) / scale),
                -Math.round((b.getTopLeft().getY() - center.y()) / scale),
                Math.round(b.getBatch().width() / scale),
                Math.round(b.getBatch().height() / scale));
        var textRect = painter.boundingRect(rect, b.getBatch().getName());
        // Board batch title
        // -----------------
        painter.setPen(selected ? colorSettings.getSelectedPen() : colorSettings.getOutlinePen());
        drawText(
                new QPointF(
                        rect.x() + rect.width() / 2 - (float) textRect.width() / 2,
                        rect.y() + rect.height() / 2
                ), b.getBatch().getName());
        var sizeString = String.format("%s", b.getSize());
        textRect = painter.boundingRect(rect, sizeString);
        drawText(
                new QPointF(
                        rect.x() + rect.width() / 2 - (float) textRect.width() / 2,
                        rect.y() + rect.height() / 2 + 16
                ), sizeString);
        painter.setPen(Qt.PenStyle.NoPen);

        // Board outline
        // -------------
        var outlineLayer = b.getBatch().getLayer(Layer.Type.EdgeCuts);
        if (outlineLayer instanceof Gerber g) {
            drawGerber(g,
                    new QPointF(b.getOffset().getX(), b.getOffset().getY()),
                    null,
                    null,
                    selected ? colorSettings.getSelectedPen() : new QPen(Qt.PenStyle.NoPen));
        }
    }

    public final void drawGerber(Gerber g,
                                 final QPointF offset,
                                 final HashMap<Integer, Aperture> apertures,
                                 final HashMap<String, Macro> macros,
                                 QPen pen) {
        Aperture currentAperture = null;
        var currentInterpolation = Geometry.Interpolation.LINEAR;
        var currentPoint = new QPointF(0, 0);
        var polygonMode = false;
        var polygonPoints = new ArrayList<QPointF>();
        var brush = new QBrush(pen.color());

        setPen(Qt.PenStyle.NoPen);
        setBrush(Qt.BrushStyle.NoBrush);

        for (var cmd : g.getContents()) {
            if (cmd instanceof D01To03 d) {
                var p = new QPointF(d.getX(), d.getY());
                switch (d.getCode()) {
                    case 1:
                        if (currentInterpolation == Geometry.Interpolation.LINEAR) {
                            if (polygonMode) {
                                polygonPoints.add(translatedPoint(p, offset));
                            } else {
                                setPen(pen);
                                drawApertureLine(translatedPoint(currentPoint, offset), translatedPoint(p, offset), currentAperture);
                                setPen(Qt.PenStyle.NoPen);
                            }
                        } else {
                            setPen(pen);
                            drawApertureArc(currentPoint, p, currentInterpolation, offset, currentAperture, d);
                            setPen(Qt.PenStyle.NoPen);
                        }
                        break;
                    case 3:
                        if (apertures != null) {
                            setBrush(brush);
                            drawAperture(translatedPoint(p, offset), currentAperture, macros);
                            setBrush(Qt.BrushStyle.NoBrush);
                        }
                        break;
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
                } else if (gcode.getCode() == 36) {
                    polygonPoints.clear();
                    polygonMode = true;
                } else if (gcode.getCode() == 37) {
                    if (polygonMode) {
                        setBrush(brush);
                        drawPolygon(polygonPoints.toArray(QPointF[]::new));
                        setBrush(Qt.BrushStyle.NoBrush);
                        polygonMode = false;
                    }
                }
            } else if ((apertures != null) && (cmd instanceof DAperture a)) {
                currentAperture = apertures.get(a.getCode());
            }
        }
    }

    public void drawApertureArc(QPointF currentPoint, QPointF point, Geometry.Interpolation interpolation, QPointF offset, Aperture aperture, D01To03 d) {
        var arcC = new QPointF(currentPoint.x() + d.getI(), currentPoint.y() + d.getJ());
        var radius = Math.sqrt(Math.pow(currentPoint.x() - arcC.x(), 2) + Math.pow(currentPoint.y() - arcC.y(), 2));
        var ang1 = Math.atan2(currentPoint.y() - arcC.y(), currentPoint.x() - arcC.x());
        var ang2 = Math.atan2(point.y() - arcC.y(), point.x() - arcC.x());
        if ((interpolation == Geometry.Interpolation.CCW) && (ang2 < 0)) ang2 = ang2 + 2 * Math.PI;
        var arcRect = new QRectF(
                (arcC.x() - radius + center.x() + ((offset != null) ? offset.x() : 0)) / scale,
                -(arcC.y() - radius - center.y() + ((offset != null) ? offset.y() : 0)) / scale,
                radius * 2 / scale, -radius * 2 / scale);

        var pen = new QPen(pen());
        if (aperture != null) pen.setWidth((int) (aperture.getMeasures().get(0) / scale));
        setPen(pen);
        drawArc(arcRect, (int) (ang1 * arcQ), (int) ((ang2 - ang1) * arcQ));
        setPen(Qt.PenStyle.NoPen);
    }

    public void drawApertureLine(final QPointF start, final QPointF end, final Aperture aperture) {
        var pen = new QPen(pen());
        if (aperture != null) pen.setWidth((int) (aperture.getMeasures().get(0) / scale));
        setPen(pen);
        drawLine(start, end);
    }

    public void drawAperture(final QPointF p, final Aperture a, final HashMap<String, Macro> macros) {
        if (a == null) return;

        switch (a.getMacro()) {
            case "C" -> {   // Circle
                var radius = a.getMeasures().get(0) / scale / 2;
                drawEllipse(p, radius, radius);
            }
            case "O" ->     // Oval
                    drawEllipse(p, a.getMeasures().get(0) / scale / 2, a.getMeasures().get(1) / scale / 2);

            case "R" -> {   // Rectangle
                var rx = a.getMeasures().get(0) / scale / 2;
                var ry = a.getMeasures().get(1) / scale / 2;
                drawRect(new QRectF(p.x() - rx, p.y() - ry, rx * 2, ry * 2));
            }
            default ->      // Macro name
                    drawMacro(p, macros.get(a.getMacro()), a.getMeasures().toArray(Double[]::new), brush().color());
        }
    }

    public final void drawMacro(final QPointF p, Macro macro, Double[] measures, QColor color) {
        var result = macro.eval(measures);
        for (var r : result) {
            switch (r.getType()) {
                case Circle -> {
                    var dia = r.getValue(0) / scale / 2;
                    var center = new QPointF(r.getValue(1) / scale, -r.getValue(2) / scale).add(p);
                    drawEllipse(center, dia, dia);
                }
                case Outline -> {
                    var poly = new QPolygonF();
                    for (var i = 0; i < r.getValue(0) * 2; i+=2) {
                        poly.append(new QPointF(r.getValue(i+1) / scale, -r.getValue(i+2) / scale).add(p));
                    }
                    drawPolygon(poly);
                }
                case VectorLine -> {
                    setPen(new QPen(color, r.getValue(0) / scale));
                    drawLine(new QPointF(r.getValue(1) / scale, -r.getValue(2) / scale).add(p),
                            new QPointF(r.getValue(3) / scale, -r.getValue(4) / scale).add(p));
                    setPen(Qt.PenStyle.NoPen);
                }
            }
        }
    }

    public void drawFeature(Feature f, boolean selected) {
        if (f instanceof RoundFeature m) {
            var dia = (int) Math.round(m.getRadius() / scale);
            var c = translatedPoint(m.getCenter().getX(), m.getCenter().getY());

            setPen(selected ?
                    colorSettings.getSelectedPen() :
                    m.isValid() ? colorSettings.getValidFeaturePen() : colorSettings.getInvalidFeaturePen()
            );

            // Draw feature sign
            drawEllipse(c, dia, dia);
            if (!m.isValid()) {
                var r = m.getRadius() / scale / 2;
                drawLine((int) (c.x() - r), (int) (c.y() - r), (int) (c.x() + r), (int) (c.y() + r));
                drawLine((int) (c.x() + r), (int) (c.y() - r), (int) (c.x() - r), (int) (c.y() + r));
            }
            setPen(Qt.PenStyle.NoPen);
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
