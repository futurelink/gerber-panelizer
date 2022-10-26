package ru.futurelink.gerber.panelizer.gui.widgets.merger;

import io.qt.core.QPoint;
import io.qt.core.QPointF;
import io.qt.gui.*;
import io.qt.opengl.*;
import io.qt.opengl.widgets.QOpenGLWidget;
import io.qt.qt3d.render.QClearBuffers;
import io.qt.widgets.QWidget;
import lombok.Getter;
import ru.futurelink.gerber.panelizer.Layer;
import ru.futurelink.gerber.panelizer.canvas.Aperture;
import ru.futurelink.gerber.panelizer.canvas.Macro;
import ru.futurelink.gerber.panelizer.drl.Excellon;
import ru.futurelink.gerber.panelizer.exceptions.MergerException;
import ru.futurelink.gerber.panelizer.gbr.Gerber;
import ru.futurelink.gerber.panelizer.gui.widgets.intf.MergerRenderWidget;
import ru.futurelink.gerber.panelizer.gui.widgets.intf.OpenGLConst;

import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.util.*;

public class MergerOpenGLWidget extends QOpenGLWidget implements MergerRenderWidget, OpenGLConst {
    private final GerberData gerberData;
    private final QMatrix4x4 projection = new QMatrix4x4();
    private QVector2D viewOffset = new QVector2D();
    private QPointF centerOffset = new QPointF(0, 0);
    private float scale = 0.3f;
    private QOpenGLShaderProgram shaderProgram = null;
    private QOpenGLFunctions_3_3_Core GL = null;
    private final HashMap<Layer.Type, LayerBuffer> layerBuffers = new HashMap<>();
    @Getter private final List<Layer.Type> layerOrder = new ArrayList<>();
    private final HashMap<Layer.Type, QVector4D> layerColors = new HashMap<>();

    public final Signal1<String> onError = new Signal1<>();

    private class LayerBuffer {
        private static final int TRIANGLE_BUFFER_INDEX = 0;
        private static final int POLYGON_BUFFER_INDEX = 1;
        private static final int LINES_BUFFER_INDEX = 2;
        boolean initialized = false;
        @Getter private final QVector4D color;
        private final IntBuffer buffers;
        private final IntBuffer VAOs;
        @Getter private final ArrayList<PolygonMapItem> polygonMap;
        private final IntBuffer bufferDataLengths;

        private static class PolygonMapItem {
            int start;
            int length;

            private PolygonMapItem(int start, int length) {
                this.start = start;
                this.length = length;
            }
        }

        public LayerBuffer(QVector4D layerColor) {
            polygonMap = new ArrayList<>();
            buffers = IntBuffer.allocate(3);
            VAOs = IntBuffer.allocate(3);
            bufferDataLengths = IntBuffer.allocate(3);
            color = layerColor;
        }

        public void initialize() throws Exception {
            GL.glGenBuffers(buffers.capacity(), buffers);
            GL.glGenVertexArrays(VAOs.capacity(), VAOs);

            // Empty buffers
            updateLinesBuffer(null);
            updateTriangleBuffer(null);
            updatePolygonBuffer(null);

            // Initialize VAOs
            createVAO(buffers.get(TRIANGLE_BUFFER_INDEX), VAOs.get(TRIANGLE_BUFFER_INDEX));
            createVAO(buffers.get(POLYGON_BUFFER_INDEX), VAOs.get(POLYGON_BUFFER_INDEX));
            createVAO(buffers.get(LINES_BUFFER_INDEX), VAOs.get(LINES_BUFFER_INDEX));

            initialized = true;
        }

        public int getTriangleCount() { return bufferDataLengths.get(TRIANGLE_BUFFER_INDEX); }
        public int getLinesCount() { return bufferDataLengths.get(LINES_BUFFER_INDEX); }

        public int getTriangleVAO() { return VAOs.get(TRIANGLE_BUFFER_INDEX); }
        public int getPolygonVAO() { return VAOs.get(POLYGON_BUFFER_INDEX); }
        public int getLinesVAO() { return VAOs.get(LINES_BUFFER_INDEX); }

        public void updatePolygonBuffer(DoubleBuffer dataBuffer) throws Exception {
            updateBuffer(POLYGON_BUFFER_INDEX, dataBuffer);
        }

        public void updateLinesBuffer(DoubleBuffer dataBuffer) throws Exception {
            updateBuffer(LINES_BUFFER_INDEX, dataBuffer);
        }

        public void updateTriangleBuffer(DoubleBuffer dataBuffer) throws Exception {
            updateBuffer(TRIANGLE_BUFFER_INDEX, dataBuffer);
        }

        private void clean() throws Exception {
            if (initialized) {
                polygonMap.clear();
                updateLinesBuffer(null);
                updateTriangleBuffer(null);
                updatePolygonBuffer(null);
            }
        }

        private void updateBuffer(int bufferIndex, DoubleBuffer dataBuffer) throws Exception {
            GL.glBindBuffer(GL_ARRAY_BUFFER, buffers.get(bufferIndex));
            if (GL.glGetError() != 0) { throw new Exception("Error binding buffer on update!"); }
            bufferDataLengths.put(bufferIndex, (dataBuffer != null) ? dataBuffer.capacity() : 0);
            var length = (long) bufferDataLengths.get(bufferIndex) * Double.BYTES;
            GL.glBufferData(GL_ARRAY_BUFFER, length, null, GL_DYNAMIC_DRAW);
            GL.glBufferSubData(GL_ARRAY_BUFFER, 0, length, dataBuffer);
            if (GL.glGetError() != 0) { throw new Exception("Error setting buffer data: " + GL.glGetError()); }
            GL.glBindBuffer(GL_ARRAY_BUFFER, 0);
        }

        private void createVAO(int buffer, int vao) throws Exception {
            GL.glBindBuffer(GL_ARRAY_BUFFER, buffer);
            if (GL.glGetError() != 0) { throw new Exception("Error binding buffer: " + GL.glGetError()); }
            GL.glBindVertexArray(vao);
            if (GL.glGetError() != 0) { throw new Exception("Error binding VAO on update + " + GL.glGetError()); }

            GL.glEnableVertexAttribArray(0);
            GL.glVertexAttribPointer(0, 2, GL_DOUBLE, false, 0, null);  // Vertex

            GL.glBindVertexArray(0);
            GL.glDisableVertexAttribArray(0);
        }
    }

    public MergerOpenGLWidget(QWidget parent, GerberData data) {
        super(parent);
        gerberData = data;

        // Set up layer colors
        layerColors.put(Layer.Type.EdgeCuts, new QVector4D(0.7f, 0.7f, 0.0f, 0.8f));
        layerColors.put(Layer.Type.FrontMask, new QVector4D(0.0f, 0.7f, 0.0f, 0.5f));
        layerColors.put(Layer.Type.FrontCopper, new QVector4D(1.0f, 1.0f, 0.0f, 1.0f));
        layerColors.put(Layer.Type.FrontSilk, new QVector4D(1.0f, 0.5f, 0.0f, 1.0f));
        layerColors.put(Layer.Type.TopDrill, new QVector4D(0.0f, 0.0f, 0.0f, 1.0f));

        setMouseTracking(true);
    }

    @Override
    protected void initializeGL() {
        GL = QOpenGLVersionFunctionsFactory.get(QOpenGLFunctions_3_3_Core.class, context());

        GL.glEnable(GL_DEPTH_TEST);
        GL.glEnable(GL_CULL_FACE);

        GL.glEnable(GL_BLEND);
        GL.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        GL.glEnable(GL_LINE_SMOOTH);
        GL.glEnable(GL_POLYGON_SMOOTH);
        GL.glHint(GL_LINE_SMOOTH_HINT, GL_NICEST);

        GL.glEnable(GL_MULTISAMPLE);

        GL.glClearColor(0.7f, 0.7f, 0.7f, 1.0f);

        initializeShaders();

        // Initialize buffers and VAOs
        try {
            for (var b : layerBuffers.keySet()) {
                layerBuffers.get(b).initialize();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
     * Create simple shaders
     */
    private void initializeShaders() {
        var vertexShader = new QOpenGLShader(new QOpenGLShader.ShaderType(QOpenGLShader.ShaderTypeBit.Vertex));
        if (!vertexShader.compileSourceCode("""
                        #version 330 core
                        layout (location = 0) in vec2 vertex;
                        uniform float height;
                        uniform vec2 offset;
                        uniform mat4 projection;
                        void main() {
                            gl_Position = (vec4(vertex, 0.0, 1.0) + vec4(offset, height, 0.0)) * projection;
                        }
                    """)) {
            onError.emit("Could not compile vertex shader:" + vertexShader.log());
            return;
        }

        var fragmentShader = new QOpenGLShader(new QOpenGLShader.ShaderType(QOpenGLShader.ShaderTypeBit.Fragment));
        if (!fragmentShader.compileSourceCode("""
                        #version 330
                        uniform vec4 color;
                        void main() {
                            gl_FragColor = color;
                        }
                    """)) {
            onError.emit("Could not compile fragment shader:" + fragmentShader.log());
            return;
        }

        shaderProgram = new QOpenGLShaderProgram(context());
        shaderProgram.addShader(vertexShader);
        shaderProgram.addShader(fragmentShader);
        shaderProgram.link();
    }

    public void updateBuffers(Layer.Type type, Layer layer) throws MergerException {
        var layerBuffer = layerBuffers.get(type);
        if (layerBuffer == null) return;

        System.out.println("---- Layer type: " + type + " ----");

        var gr = new GerberGraphics();
        if (layer instanceof Gerber gerber) {
            if (type == Layer.Type.EdgeCuts) {
                gr.processEdgeCutsGerber(gerber, 20);
            } else {
                gr.processGerber(gerber, getApertures(type), getMacros(type), 20);
            }
        } else if (layer instanceof Excellon excellon) {
            gr.processExcellon(excellon, 20);
        }

        // Copy data fo OpenGL buffer
        System.out.println("Triangles count: " + gr.getTrianglesCount());
        try {
            var b = DoubleBuffer.allocate(gr.getTrianglesCount() * 6);
            for (var tri : gr.getTriangles()) {
                b.put(tri[0].x()); b.put(tri[0].y());
                b.put(tri[1].x()); b.put(tri[1].y());
                b.put(tri[2].x()); b.put(tri[2].y());
            }

            layerBuffer.updateTriangleBuffer(b);
        } catch (Exception e) {
            e.printStackTrace();
            throw new MergerException(e.getMessage());
        }

        // Copy data fo OpenGL buffer
        System.out.println("Polygons points count: " + gr.getPolygonsPointsCount());
        try {
            var b = DoubleBuffer.allocate(gr.getPolygonsPointsCount() * 2);
            var s = 0;
            for (var poly : gr.getPolygons()) {
                for (var point : poly) { b.put(point.x()); b.put(point.y()); }
                layerBuffers.get(type).getPolygonMap().add(new LayerBuffer.PolygonMapItem(s, poly.length()));
                s += poly.length();
            }

            layerBuffer.updatePolygonBuffer(b);
        } catch (Exception e) {
            throw new MergerException(e.getMessage());
        }

        // Convert lines to buffer
        /*System.out.println("Lines points count: " + gr.getLinesPointsCount());
        try {
            var b = DoubleBuffer.allocate(gr.getLinesPointsCount() * 5);
            layerBuffer.updateLinesBuffer(b);
        } catch (Exception e) {
            throw new MergerException(e.getMessage());
        }*/
    }

    private HashMap<Integer, Aperture> getApertures(Layer.Type type) {
        return gerberData.getApertures(type);
    }

    private HashMap<String, Macro> getMacros(Layer.Type type) {
        return gerberData.getMacros(type);
    }

    @Override
    protected void paintGL() {
        GL.glClear(QClearBuffers.BufferType.ColorBuffer.value());

        // Configure shader
        if (shaderProgram.bind()) {
            shaderProgram.setUniformValue("projection", projection);
            shaderProgram.setUniformValue("offset", viewOffset);
        } else {
            onError.emit("Could not bind shader program: " + GL.glGetError());
            return;
        }

        var height = 0.0f;
        for (var layer : layerOrder) {
            if (!layerBuffers.containsKey(layer)) continue;

            shaderProgram.setUniformValue("color", layerBuffers.get(layer).getColor());
            shaderProgram.setUniformValue("height", height);

            // Polygon buffer
            GL.glBindVertexArray(layerBuffers.get(layer).getPolygonVAO());
            for (var poly : layerBuffers.get(layer).getPolygonMap()) {
                GL.glDrawArrays(GL_POLYGON, poly.start, poly.length);
            }
            GL.glBindVertexArray(0);

            // Triangles buffer
            GL.glBindVertexArray(layerBuffers.get(layer).getTriangleVAO());
            GL.glDrawArrays(GL_TRIANGLES, 0, layerBuffers.get(layer).getTriangleCount());
            GL.glBindVertexArray(0);

            // Lines buffer
            GL.glBindVertexArray(layerBuffers.get(layer).getLinesVAO());
            GL.glDrawArrays(GL_LINES, 0, layerBuffers.get(layer).getLinesCount());
            GL.glBindVertexArray(0);

            height += 0.01; // Place next layer on the top
        }

        // Release
        shaderProgram.release();

        GL.glFlush();
    }

    @Override
    protected void resizeGL(int w, int h) {
        centerOffset = new QPointF(w * scale / 2, h * scale / 2);
        projection.setToIdentity();
        projection.ortho(
                -(float) centerOffset.x(), (float) centerOffset.x(),
                -(float) centerOffset.y(), (float) centerOffset.y(),
                -1.0f, 1.0f);
        GL.glViewport(0, 0, w, h);
    }

    @Override
    public void scaleUp(double step) {
        scale = scale * (float) step;
        moveCenter(0, 0);   // Recalculate center offset
        resizeGL(width(), height());
        repaint();
    }

    @Override
    public void scaleDown(double step) {
        scale = scale / (float) step;
        moveCenter(0, 0);   // Recalculate center offset
        resizeGL(width(), height());
        repaint();
    }

    @Override
    public void moveCenter(double xStepPx, double yStepPx) {
        viewOffset = new QVector2D(
                (float)(viewOffset.x() + xStepPx * scale),
                (float)(viewOffset.y() - yStepPx * scale)
        );
        repaint();
    }

    @Override
    public double getScale() {
        return scale;
    }

    @Override
    public QPointF getCenter() {
        return viewOffset.toPointF();
    }

    @Override
    public Object getHighlightedInstance() {
        return null;
    }

    @Override
    public void setHighlightedInstance(Object instance) {

    }

    @Override
    public Object getInstanceAtPosition(QPointF position) {
        return null;
    }

    @Override
    public void setLayerOrder(List<Layer.Type> order) {
        layerOrder.clear();
        layerOrder.addAll(order);

        // Create buffers, if they do not exist
        for (var type : layerOrder) {
            if (!layerBuffers.containsKey(type)) {
                layerBuffers.put(type, new LayerBuffer(layerColors.get(type)));
            }
        }

        repaint();
    }

    @Override
    public void postMergeDisplayLayers() {
        // Update graphics buffers
        for (var l : getLayerOrder()) {
            try {
                updateBuffers(l, gerberData.getMerger().getMergedBatch().getLayer(l));
            } catch (Exception e) {
                onError.emit(e.getMessage());
            }
        }
    }

    @Override
    public void clear() {
        try {
            for (var b : layerBuffers.keySet()) {
                layerBuffers.get(b).clean();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        repaint();
    }

    @Override
    public QPointF getScreenCoords(QPoint screenPos) {
        return new QPointF(-getCenter().x() - centerOffset.x() + screenPos.x() * scale,
                -(getCenter().y() - centerOffset.y() + screenPos.y() * scale));
    }

    @Override
    public Signal1<String> onErrorSignal() {
        return onError;
    }

}
