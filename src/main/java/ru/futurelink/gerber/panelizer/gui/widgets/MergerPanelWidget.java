package ru.futurelink.gerber.panelizer.gui.widgets;

import io.qt.core.*;
import io.qt.gui.*;
import io.qt.widgets.QMenu;
import io.qt.widgets.QWidget;
import lombok.Getter;
import ru.futurelink.gerber.panelizer.Layer;
import ru.futurelink.gerber.panelizer.batch.Batch;
import ru.futurelink.gerber.panelizer.batch.BatchMerger;
import ru.futurelink.gerber.panelizer.canvas.Point;
import ru.futurelink.gerber.panelizer.canvas.fetaures.Feature;
import ru.futurelink.gerber.panelizer.canvas.fetaures.MouseBites;
import ru.futurelink.gerber.panelizer.exceptions.MergerException;
import ru.futurelink.gerber.panelizer.gbr.Gerber;
import ru.futurelink.gerber.panelizer.gui.ColorSettings;
import ru.futurelink.gerber.panelizer.gui.GerberPainter;
import ru.futurelink.gerber.panelizer.gui.Utils;

import java.util.UUID;

public class MergerPanelWidget extends QWidget {

    @Getter private final BatchMerger merger;
    private double scale;
    @Getter private double margin;
    private QPointF center;
    private QPointF mousePosition;
    private QPoint mousePressPoint;
    private Object instanceUnderMouse;
    private Object instanceSelected;
    public final Signal1<QPointF> mouseMoved = new Signal1<>();
    public final Signal1<Object> deleteItem = new Signal1<>();
    public final Signal1<Object> moveItem = new Signal1<>();
    public final Signal3<Class <? extends Feature>, Double, Double> addFeatureItem = new Signal3<>();
    public final Signal3<UUID, Double, Double> addBatchItem = new Signal3<>();
    public final Signal1<QSizeF> batchChanged = new Signal1<>();

    private final QAction addFeatureAction;
    private final QAction deleteAction;

    public MergerPanelWidget(QWidget parent, BatchMerger m) {
        super(parent);
        merger = m;
        scale = 0.25;
        margin = 0;
        center = new QPointF(10, 10);
        mousePressPoint = null;

        addFeatureAction = new QAction("Add MouseBites feature");
        addFeatureAction.triggered.connect(this, "addMouseBites(boolean)");

        deleteAction = new QAction("Delete item");
        deleteAction.triggered.connect(this, "deleteItem(boolean)");

        setMouseTracking(true);
        repaint();
    }

    public void setMargin(double margin) {
        this.margin = margin;
    }

    private void deleteItem(boolean t) {
        if (instanceUnderMouse instanceof Feature f) {
            getMerger().removeFeature(f);
        } else if (instanceUnderMouse instanceof BatchMerger.BatchInstance b) {
            getMerger().removeBatchInstance(b);
        }
        deleteItem.emit(instanceUnderMouse);
        mergeDisplayLayers();
        repaint();
    }

    private void addMouseBites(boolean t) {
        addFeatureItem.emit(MouseBites.class, mousePosition.x(), mousePosition.y());
        mergeDisplayLayers();
        repaint();
    }

    @Override
    protected void contextMenuEvent(QContextMenuEvent event) {
        var menu = new QMenu();

        deleteAction.setEnabled(instanceUnderMouse != null);

        // Fill menu with available batches
        // --------------------------------
        var addBatchInstanceMenu = new QMenu("Add Gerber instance");
        addBatchInstanceMenu.setEnabled(instanceUnderMouse == null);
        for (var batchId : merger.getBatchUUIDs()) {
            addBatchInstanceMenu.addAction(merger.getBatchName(batchId), b ->
                addBatchItem.emit(batchId, mousePosition.x(), mousePosition.y())
            );
        }

        menu.addAction(addFeatureAction);
        menu.addMenu(addBatchInstanceMenu);
        menu.addSeparator();
        menu.addAction(deleteAction);
        menu.exec(event.globalPos());
    }

    @Override
    protected void mousePressEvent(QMouseEvent event) {
        if (event.button() == Qt.MouseButton.LeftButton) {
            mousePressPoint = event.pos();
        }
    }

    @Override
    protected void mouseReleaseEvent(QMouseEvent event) {
        try {
            // Object moved
            if (instanceUnderMouse != null) {
                moveItem.emit(instanceUnderMouse);
                mergeDisplayLayers();
                repaint();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        mousePressPoint = null;
    }

    @Override
    protected void mouseMoveEvent(QMouseEvent event) {
        boolean needRepaint = false;
        var position = event.pos();
        mousePosition = new QPointF(-(center.x() - position.x() * scale), center.y() - position.y() * scale);
        if (mousePressPoint != null) {
            var offset = position.clone().subtract(mousePressPoint);
            if (instanceUnderMouse == null) { // Moving coordinate system
                center = new QPointF(center.x() + offset.x() * scale, center.y() + offset.y() * scale);
            } else { // Moving part that is under mouse
                var offsetMM = new QPointF(
                        Math.round(offset.x() * scale * 1000.0) / 1000.0,
                        Math.round(-offset.y() * scale * 1000.0) / 1000.0);
                if (instanceUnderMouse instanceof MouseBites m) {
                    m.moveOffset(offsetMM.x(), offsetMM.y());
                } else if (instanceUnderMouse instanceof BatchMerger.BatchInstance b) {
                    var constrainedOffset = getConstrainedOffset(b, offsetMM, margin);
                    b.moveOffset(constrainedOffset.x(), constrainedOffset.y());
                }
            }
            mousePressPoint = position;
            needRepaint = true;
        } else {
            var b = getInstanceUnderMouse(mousePosition);
            if (b != instanceUnderMouse) {  // Repaint only if something has changed
                instanceUnderMouse = b;
                needRepaint = true;
            }
        }

        // Send millimeter position to main window
        mouseMoved.emit(mousePosition);

        if (needRepaint) repaint();

        event.accept();
    }

    // Checks for collisions with other placements and returns offset
    // that is possible.
    private QPointF getConstrainedOffset(BatchMerger.BatchInstance instance, QPointF offset, double distance) {
        // Check left-right border intersection
        var instRect = new QRectF(instance.left() - distance + offset.x(), instance.top(),
                instance.width() + distance * 2, instance.height());
        var iter = merger.batchInstances();
        while (iter.hasNext()) {
            var b = iter.next();
            if (b != instance) {
                var i = rectIntersection(instRect, new QRectF(b.left(), b.top(), b.width(), b.height()));
                if (i != null) {
                    offset.setX(offset.x() - ((offset.x() > 0) ? Math.abs(i.width()) : -Math.abs(i.width())));
                    break;
                }
            }
        }

        // Check top-bottom border
        instRect = new QRectF(instance.left(), instance.top() + distance + offset.y(),
                instance.width(), instance.height() - distance * 2);
        iter = merger.batchInstances();
        while (iter.hasNext()) {
            var b = iter.next();
            if (b != instance) {
                var i = rectIntersection(instRect, new QRectF(b.left(), b.top(), b.width(), b.height()));
                if (i != null) {
                    offset.setY(offset.y() - ((offset.y() > 0) ? Math.abs(i.height()) : -Math.abs(i.height())));
                    break;
                }
            }
        }

        return offset;
    }

    private QRectF rectIntersection(QRectF r1, QRectF r2) {
        var r1XRange = new Utils.QRange(r1.left(), r1.right());
        var r2XRange = new Utils.QRange(r2.left(), r2.right());
        var r1YRange = new Utils.QRange(r1.top(), r1.bottom());
        var r2YRange = new Utils.QRange(r2.top(), r2.bottom());
        var xIntersection = r1XRange.intersection(r2XRange);
        var yIntersection  = r1YRange.intersection(r2YRange);
        if ((xIntersection != null) && (yIntersection != null)) {
            return new QRectF(
                    xIntersection.x1(),
                    yIntersection.x1(),
                    xIntersection.x2() - xIntersection.x1(),
                    yIntersection.x2() - yIntersection.x1());
        }
        return null;
    }

    // Returns instance (Feature or Batch) under mouse cursor.
    // Feature has priority, so if both feature and batch are under
    // mouse, then feature is returned.
    private Object getInstanceUnderMouse(QPointF mousePosition) {
        Object i = null;

        // Check if mouse is over feature
        var fi = merger.features();
        while (fi.hasNext()) {
            var instance = fi.next();
            if (instance instanceof MouseBites m) {
                if (isInCircle(mousePosition, m.getCenter(), m.getRadius())) {
                    i = instance;
                    break;
                }
            }
        }

        // If moues is not over feature, then check if mouse is over
        // the batch projection.
        if (i == null) {
            var iter = merger.batchInstances();
            while (iter.hasNext()) {
                var instance = iter.next();
                var tl = instance.getTopLeft();
                var br = instance.getBottomRight();
                if (isInBetween(mousePosition.x(), tl.getX().doubleValue(), br.getX().doubleValue()) &&
                        isInBetween(mousePosition.y(), tl.getY().doubleValue(), br.getY().doubleValue())
                ) {
                    i = instance;
                    break;
                }
            }
        }

        return i;
    }

    @Override
    protected void wheelEvent(QWheelEvent event) {
        var numDegrees = event.angleDelta();
        if (!numDegrees.isNull()) {
            // Change scale
            if (numDegrees.y() < 0) {
                scale = scale * 1.1;
            } else {
                scale = scale / 1.1;
            }

            // Move center relative to current mouse position
            //var centerOffsetX = (center.x() - mousePosition.x());
            //var centerOffsetY = (center.y() - mousePosition.y());
            //center = new QPointF(center.x() - centerOffsetX, center.y() - centerOffsetY);
        }
        repaint();
        event.accept();
    }

    @Override
    protected void paintEvent(QPaintEvent event) {
        var painter = new GerberPainter(this, scale, center);
        painter.setBackground(new QBrush(new QColor(40, 40, 40)));
        painter.setRenderHint(QPainter.RenderHint.Antialiasing);

        painter.drawAxis(center, width(), height());

        // Draw merged layers
        // ------------------
        painter.drawHoles(painter, merger.getMergedBatch().getLayer(Layer.Type.TopDrill), null);
        if (merger.getMergedBatch().getLayer(Layer.Type.EdgeCuts) instanceof Gerber fullOutline) {
            painter.setPen(new QPen(new QColor(0, 0, 0), 1));
            painter.drawGerber(fullOutline, null);

            var w = fullOutline.getMaxX() - fullOutline.getMinX();
            var h = fullOutline.getMaxY() - fullOutline.getMinY();
            painter.drawBoundingBoxMarks(new QRectF(fullOutline.getMinX(), fullOutline.getMinY(), w, h));
        }

        /*if (merger.getMergedBatch().getLayer(Layer.Type.FrontMask) instanceof Gerber g) {
            painter.setPen(new QPen(new QColor(160, 160, 160), 1));
            painter.drawGerber(g, null);
        }*/

        // Paint merger boards
        // -------------------
        var i = merger.batchInstances();
        while (i.hasNext()) {
            var b = i.next();
            painter.drawBatchOutline(painter, b, b.equals(instanceUnderMouse));
        }

        // Paint feature points
        // --------------------
        var fi = merger.features();
        while (fi.hasNext()) {
            var f = fi.next();
            painter.drawFeature(painter, f, f.equals(instanceUnderMouse));
        }

        // Draw border
        painter.setPen(new QPen(new QColor(100, 100, 100)));
        painter.drawRect(0, 0, width()-1, height()-1);
    }

    public void addBatch(UUID id, Batch b) {
        merger.addBatch(id, b);
    }

    public void placeBatchInstance(UUID id, UUID batchUUID, double x, double y) throws MergerException {
        merger.placeBatchInstance(id, batchUUID, x, y);
    }

    public void addFeature(Feature f) {
        merger.addFeature(f);
    }

    private boolean isInBetween(double value, double a, double b) {
        return (a < b) ? ((value > a) && (value < b)) : ((value < a) && (value > b));
    }

    private boolean isInCircle(QPointF p, Point center, double radius) {
        return (Math.pow(p.x() - center.getX().doubleValue(), 2) +
                Math.pow(p.y() - center.getY().doubleValue(), 2) < radius * radius);
    }

    public void mergeDisplayLayers() {
        try {
            merger.mergeLayer(Layer.Type.EdgeCuts);
            merger.mergeLayer(Layer.Type.FrontMask);
            merger.mergeLayer(Layer.Type.TopDrill);
        } catch (MergerException ex) {
            ex.printStackTrace();
        }
        batchChanged.emit(new QSizeF(merger.getMergedBatch().width(), merger.getMergedBatch().height()));
    }

    public final void clear() {
        merger.clear();
        repaint();
    }
}
