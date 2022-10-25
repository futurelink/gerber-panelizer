package ru.futurelink.gerber.panelizer.gui.widgets.merger;

import io.qt.core.*;
import io.qt.gui.*;
import io.qt.widgets.QMenu;
import io.qt.widgets.QVBoxLayout;
import io.qt.widgets.QWidget;
import lombok.Getter;
import ru.futurelink.gerber.panelizer.Layer;
import ru.futurelink.gerber.panelizer.batch.Batch;
import ru.futurelink.gerber.panelizer.batch.BatchMerger;
import ru.futurelink.gerber.panelizer.canvas.fetaures.Feature;
import ru.futurelink.gerber.panelizer.canvas.fetaures.MouseBites;
import ru.futurelink.gerber.panelizer.exceptions.MergerException;
import ru.futurelink.gerber.panelizer.gui.Utils;
import ru.futurelink.gerber.panelizer.gui.widgets.intf.MergerRenderWidget;
import ru.futurelink.gerber.panelizer.gui.widgets.intf.MergerWidget;

import java.util.Arrays;
import java.util.UUID;

public class MergerPanelWidget extends QWidget implements MergerWidget {
    private final GerberData gerberData;
    @Getter private double margin;
    private QPointF mousePosition;
    private QPoint mousePressPoint;
    public final Signal1<QPointF> mouseMoved = new Signal1<>();
    public final Signal1<Object> deleteItem = new Signal1<>();
    public final Signal1<Object> moveItem = new Signal1<>();
    public final Signal3<Class <? extends Feature>, Double, Double> addFeatureItem = new Signal3<>();
    public final Signal3<UUID, Double, Double> addBatchItem = new Signal3<>();
    public final Signal1<QSizeF> batchChanged = new Signal1<>();

    private final QAction addFeatureAction;
    private final QAction deleteAction;
    private final MergerRenderWidget renderWidget;

    public MergerPanelWidget(QWidget parent, BatchMerger m) {
        super(parent);
        gerberData = new GerberData(m);
        renderWidget = new MergerOpenGLWidget(this, gerberData);
        renderWidget.setLayerOrder(Arrays.asList(
                Layer.Type.EdgeCuts,
                Layer.Type.FrontCopper,
                Layer.Type.FrontPaste,
                Layer.Type.FrontMask,
                Layer.Type.FrontSilk,
                Layer.Type.TopDrill
        ));

        var lay = new QVBoxLayout(this);
        lay.setContentsMargins(4, 4, 4, 4);
        setLayout(lay);
        layout().addWidget((QWidget) renderWidget);

        margin = 0;
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
        if (renderWidget.getHighlightedInstance() instanceof Feature f) {
            gerberData.getMerger().removeFeature(f);
        } else if (renderWidget.getHighlightedInstance() instanceof BatchMerger.BatchInstance b) {
            gerberData.getMerger().removeBatchInstance(b);
        }
        deleteItem.emit(renderWidget.getHighlightedInstance());
        try {
            mergeDisplayLayers();
        } catch (MergerException e) {
            e.printStackTrace();
        } finally {
            repaint();
        }
    }

    private void addMouseBites(boolean t) {
        addFeatureItem.emit(MouseBites.class, mousePosition.x(), mousePosition.y());
        try {
            mergeDisplayLayers();
        } catch (MergerException e) {
            e.printStackTrace();
        } finally {
            repaint();
        }
    }

    @Override
    protected void contextMenuEvent(QContextMenuEvent event) {
        var menu = new QMenu();

        deleteAction.setEnabled(renderWidget.getHighlightedInstance() != null);

        // Fill menu with available batches
        // --------------------------------
        var addBatchInstanceMenu = new QMenu("Add Gerber instance");
        addBatchInstanceMenu.setEnabled(renderWidget.getHighlightedInstance() == null);
        for (var batchId : gerberData.getMerger().getBatchUUIDs()) {
            addBatchInstanceMenu.addAction(gerberData.getMerger().getBatchName(batchId), b ->
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
            if (renderWidget.getHighlightedInstance() != null) {
                moveItem.emit(renderWidget.getHighlightedInstance());
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
        mousePosition = renderWidget.getScreenCoords(event.pos());
        var instance = renderWidget.getHighlightedInstance();
        if (mousePressPoint != null) {
            var offset = position.clone().subtract(mousePressPoint);
            if (instance == null) { // Moving coordinate system
                renderWidget.moveCenter(offset.x(), offset.y());
            } else { // Moving part that is under mouse
                var offsetMM = new QPointF(
                        Math.round(offset.x() * renderWidget.getScale() * 1000.0) / 1000.0,
                        Math.round(-offset.y() * renderWidget.getScale() * 1000.0) / 1000.0);
                if (instance instanceof MouseBites m) {
                    m.moveOffset(offsetMM.x(), offsetMM.y());
                } else if (instance instanceof BatchMerger.BatchInstance b) {
                    var constrainedOffset = getConstrainedOffset(b, offsetMM, margin);
                    b.moveOffset(constrainedOffset.x(), constrainedOffset.y());
                }
            }
            mousePressPoint = position;
            needRepaint = true;
        } else {
            var b = renderWidget.getInstanceAtPosition(mousePosition);
            if (b != renderWidget.getHighlightedInstance()) {  // Repaint only if something has changed
                renderWidget.setHighlightedInstance(b);
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
        var iter = gerberData.getMerger().batchInstances();
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
        iter = gerberData.getMerger().batchInstances();
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


    @Override
    protected void wheelEvent(QWheelEvent event) {
        var numDegrees = event.angleDelta();
        if (!numDegrees.isNull()) {
            // Change scale
            if (numDegrees.y() < 0) {
                renderWidget.scaleUp(1.1);
            } else {
                renderWidget.scaleDown(1.1);
            }
        }
        repaint();
        event.accept();
    }

    public void addBatch(UUID id, Batch b) {
        gerberData.getMerger().addBatch(id, b);
    }

    public void placeBatchInstance(UUID id, UUID batchUUID, double x, double y) throws MergerException {
        gerberData.getMerger().placeBatchInstance(id, batchUUID, x, y);
    }

    public void addFeature(Feature f) {
        gerberData.getMerger().addFeature(f);
    }

    @Override
    public void mergeDisplayLayers() throws MergerException {
        // Load and merge everything
        for (var l : renderWidget.getLayerOrder()) {
            gerberData.getMerger().mergeLayer(l);
            gerberData.loadMacros(l);
            gerberData.loadApertures(l);
        }

        renderWidget.postMergeDisplayLayers();

        batchChanged.emit(new QSizeF(
                gerberData.getMerger().getMergedBatch().width(),
                gerberData.getMerger().getMergedBatch().height()));
    }

    @Override
    public final void clear() {
        renderWidget.clear();
        margin = 0;
        repaint();
    }

    @Override
    public BatchMerger getMerger() {
        return gerberData.getMerger();
    }
}
