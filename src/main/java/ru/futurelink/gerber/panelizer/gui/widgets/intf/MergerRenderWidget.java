package ru.futurelink.gerber.panelizer.gui.widgets.intf;

import io.qt.core.QPoint;
import io.qt.core.QPointF;
import ru.futurelink.gerber.panelizer.Layer;

import java.util.List;

public interface MergerRenderWidget {
    void scaleUp(double step);
    void scaleDown(double step);
    double getScale();
    QPointF getCenter();
    void moveCenter(double xStep, double yStep);
    Object getHighlightedInstance();
    void setHighlightedInstance(Object instance);
    Object getInstanceAtPosition(QPointF position);
    void setLayerOrder(List<Layer.Type> order);
    List<Layer.Type> getLayerOrder();
    void postMergeDisplayLayers();
    void clear();
    QPointF getScreenCoords(QPoint screenPos);
}
