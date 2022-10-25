package ru.futurelink.gerber.panelizer.gui.widgets.merger;

import io.qt.core.QPoint;
import io.qt.core.QPointF;
import io.qt.core.QRectF;
import io.qt.core.Qt;
import io.qt.gui.*;
import io.qt.widgets.QWidget;
import lombok.Getter;
import ru.futurelink.gerber.panelizer.Layer;
import ru.futurelink.gerber.panelizer.canvas.Point;
import ru.futurelink.gerber.panelizer.canvas.fetaures.MouseBites;
import ru.futurelink.gerber.panelizer.gbr.Gerber;
import ru.futurelink.gerber.panelizer.gui.ColorSettings;
import ru.futurelink.gerber.panelizer.gui.GerberPainter;
import ru.futurelink.gerber.panelizer.gui.widgets.intf.MergerRenderWidget;

import java.util.ArrayList;
import java.util.List;

public class MergerPaintedWidget extends QWidget implements MergerRenderWidget {
    private final GerberData gerberData;
    private double scale;
    private QPointF center;
    private final ColorSettings colorSettings = ColorSettings.getInstance();
    private Object instanceUnderMouse = null;

    @Getter private final List<Layer.Type> layerOrder = new ArrayList<>();

    public MergerPaintedWidget(QWidget parent, GerberData gerberData) {
        super(parent);
        this.gerberData = gerberData;
        this.scale = 0.3;
        this.center = new QPointF(0, 0);
        setMouseTracking(true);
    }

    @Override
    protected void paintEvent(QPaintEvent event) {
        paintInternal();
    }

    private void paintInternal() {
        var painter = new GerberPainter(this, scale, center);
        painter.setBackground(new QBrush(new QColor(40, 40, 40)));
        painter.setRenderHint(QPainter.RenderHint.Antialiasing);

        painter.drawAxis(center, width(), height());

        // Draw merged layers
        // ------------------
        if (gerberData.getMerger().getMergedBatch().getLayer(Layer.Type.EdgeCuts) instanceof Gerber fullOutline) {
            painter.setPen(new QPen(new QColor(0, 0, 0), 1));
            painter.drawGerber(
                    fullOutline, null,
                    gerberData.getApertures(Layer.Type.EdgeCuts),
                    gerberData.getMacros(Layer.Type.EdgeCuts),
                    colorSettings.getOutlinePen()
            );

            var w = fullOutline.getMaxX() - fullOutline.getMinX();
            var h = fullOutline.getMaxY() - fullOutline.getMinY();
            painter.drawBoundingBoxMarks(new QRectF(fullOutline.getMinX(), fullOutline.getMinY(), w, h));
        }

        // Paint merger boards
        // -------------------
        var i = gerberData.getMerger().batchInstances();
        while (i.hasNext()) {
            var b = i.next();
            painter.drawBatchOutline(painter, b, b.equals(instanceUnderMouse));
        }

        // Paint feature points
        // --------------------
        var fi = gerberData.getMerger().features();
        while (fi.hasNext()) {
            var f = fi.next();
            painter.drawFeature(f, f.equals(instanceUnderMouse));
        }

        painter.drawHoles(gerberData.getMerger().getMergedBatch().getLayer(Layer.Type.TopDrill), null);

        // Draw border
        painter.setPen(colorSettings.getAxisPen());
        painter.setBrush(Qt.BrushStyle.NoBrush);
        painter.drawRect(0, 0, width()-1, height()-1);
    }

    public void scaleUp(double step) {
        scale = scale / step;
    }

    public void scaleDown(double step) {
        scale = scale * step;
    }

    @Override
    public void moveCenter(double xStepPx, double yStepPx) {
        center = new QPointF(center.x() + xStepPx * scale, center.y() + yStepPx * scale);
    }

    @Override
    public double getScale() {
        return scale;
    }

    @Override
    public QPointF getCenter() {
        return center;
    }

    @Override
    public Object getHighlightedInstance() {
        return instanceUnderMouse;
    }

    @Override
    public void setHighlightedInstance(Object instance) {
        instanceUnderMouse = instance;
        repaint();
    }

    // Returns instance (Feature or Batch) under mouse cursor.
    // Feature has priority, so if both feature and batch are under
    // mouse, then feature is returned.
    @Override
    public final Object getInstanceAtPosition(QPointF position) {
        Object i = null;

        // Check if mouse is over feature
        var fi = gerberData.getMerger().features();
        while (fi.hasNext()) {
            var instance = fi.next();
            if (instance instanceof MouseBites m) {
                if (isInCircle(position, m.getCenter(), m.getRadius())) {
                    i = instance;
                    break;
                }
            }
        }

        // If moues is not over feature, then check if mouse is over
        // the batch projection.
        if (i == null) {
            var iter = gerberData.getMerger().batchInstances();
            while (iter.hasNext()) {
                var instance = iter.next();
                var tl = instance.getTopLeft();
                var br = instance.getBottomRight();
                if (isInBetween(position.x(), tl.getX(), br.getX()) &&
                        isInBetween(position.y(), tl.getY(), br.getY())
                ) {
                    i = instance;
                    break;
                }
            }
        }

        return i;
    }

    @Override
    public void setLayerOrder(List<Layer.Type> order) {
        layerOrder.clear();
        layerOrder.addAll(order);
        repaint();
    }

    @Override
    public void postMergeDisplayLayers() {
        // Nothing to do for now
    }

    @Override
    public void clear() {
        gerberData.clear();
    }

    @Override
    public QPointF getScreenCoords(QPoint screenPos) {
        return new QPointF(-(getCenter().x() - screenPos.x() * getScale()), getCenter().y() - screenPos.y() * getScale());
    }

    private boolean isInBetween(double value, double a, double b) {
        return (a < b) ? ((value > a) && (value < b)) : ((value < a) && (value > b));
    }

    private boolean isInCircle(QPointF p, Point center, double radius) {
        return Math.pow(p.x() - center.getX(), 2) + Math.pow(p.y() - center.getY(), 2) < radius * radius;
    }
}
