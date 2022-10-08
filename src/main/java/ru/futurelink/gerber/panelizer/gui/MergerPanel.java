package ru.futurelink.gerber.panelizer.gui;

import io.qt.core.QMetaObject;
import io.qt.core.QPoint;
import io.qt.core.QPointF;
import io.qt.core.Qt;
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

import java.util.UUID;

public class MergerPanel extends QWidget {

    @Getter private final BatchMerger merger;
    private double scale;
    private double minDistance;
    private QPointF center;
    private QPointF mousePosition;
    private QPoint mousePressPoint;
    private Object instanceUnderMouse;
    private Object instanceSelected;
    private final GerberPainter.Settings painterSettings;
    public final Signal1<QPointF> mouseMoved = new Signal1<>();
    public final Signal1<Object> deleteItem = new Signal1<>();
    public final Signal1<Object> moveItem = new Signal1<>();
    public final Signal3<Class <? extends Feature>, Double, Double> addFeatureItem = new Signal3<>();
    public final Signal3<UUID, Double, Double> addBatchItem = new Signal3<>();

    private final QAction addFeatureAction;
    private final QAction deleteAction;

    public MergerPanel(QWidget parent, BatchMerger m) {
        super(parent);
        merger = m;
        scale = 0.25;
        minDistance = 3; // 3mm minimal distance between batches
        center = new QPointF(10, 10);
        mousePressPoint = null;
        painterSettings = new GerberPainter.Settings();

        addFeatureAction = new QAction("Add MouseBites feature");
        addFeatureAction.triggered.connect(this, "addMouseBites(boolean)");

        deleteAction = new QAction("Delete item");
        deleteAction.triggered.connect(this, "deleteItem(boolean)");

        setMouseTracking(true);
        repaint();
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
            addBatchInstanceMenu.addAction(merger.getBatchName(batchId), b -> {
                addBatchItem.emit(batchId, mousePosition.x(), mousePosition.y());
            });
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
                if (instanceUnderMouse instanceof MouseBites m) {
                    m.moveOffset(Math.round(offset.x() * scale * 1000.0) / 1000.0,
                            Math.round(-offset.y() * scale * 1000.0) / 1000.0);
                } else if (instanceUnderMouse instanceof BatchMerger.BatchInstance b) {
                    b.moveOffset(Math.round(offset.x() * scale * 1000.0) / 1000.0,
                            Math.round(-offset.y() * scale * 1000.0) / 1000.0);
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
        var painter = new GerberPainter(this, painterSettings, scale, center);
        painter.setBackground(new QBrush(new QColor(40, 40, 40)));
        painter.setRenderHint(QPainter.RenderHint.Antialiasing);

        painter.drawAxis(center, width(), height());

        // Draw merged layers
        // ------------------
        painter.drawHoles(painter, merger.getMergedBatch().getLayer(Layer.Type.TopDrill), null);
        if (merger.getMergedBatch().getLayer(Layer.Type.EdgeCuts) instanceof Gerber fullOutline) {
            painter.setPen(new QPen(new QColor(0, 0, 0), 1));
            painter.drawGerber(fullOutline, null);
        }

        /*if (merger.getBatch().getLayer(Layer.Type.FrontSilk) instanceof Gerber g) {
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

    void mergeDisplayLayers() {
        try {
            merger.mergeLayer(Layer.Type.EdgeCuts);
            merger.mergeLayer(Layer.Type.FrontSilk);
            merger.mergeLayer(Layer.Type.TopDrill);
        } catch (MergerException ex) {
            ex.printStackTrace();
        }
    }

    public final void clear() {
        merger.clear();
        repaint();
    }
}
