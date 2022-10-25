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
    private float scale = 3.0f;
    private final QMatrix4x4 projection = new QMatrix4x4();
    private QVector2D offset = new QVector2D();
    private QOpenGLShaderProgram shaderProgram = null;
    private QOpenGLFunctions_3_3_Core GL = null;
    private final HashMap<Layer.Type, LayerBuffer> layerBuffers = new HashMap<>();
    @Getter private final List<Layer.Type> layerOrder = new ArrayList<>();
    private final HashMap<Layer.Type, QVector4D> layerColors = new HashMap<>();

    private static class PolygonMapItem {
        int start;
        int length;

        private PolygonMapItem(int start, int length) {
            this.start = start;
            this.length = length;
        }
    }

    private class LayerBuffer {
        @Getter private final QVector4D color;
        private final IntBuffer buffers;
        private final IntBuffer VAOs;
        @Getter private final ArrayList<PolygonMapItem> polygonMap;

        public LayerBuffer(QVector4D layerColor) {
            polygonMap = new ArrayList<>();
            buffers = IntBuffer.allocate(3);
            VAOs = IntBuffer.allocate(3);
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
            updateVAO(buffers.get(0), VAOs.get(0));
            updateVAO(buffers.get(1), VAOs.get(1));
            updateLinesVAO(buffers.get(2));
        }

        public int getTriangleVAO() { return VAOs.get(0); }
        public int getPolygonVAO() { return VAOs.get(1); }
        public int getLinesVAO() { return VAOs.get(2); }

        public int getTriangleBuffer() { return buffers.get(0); }
        public int getPolygonBuffer() { return buffers.get(1); }
        public int getLinesBuffer() { return buffers.get(2); }

        public void updatePolygonBuffer(DoubleBuffer dataBuffer) throws Exception {
            updateBuffer(getPolygonBuffer(), dataBuffer);
        }

        public void updateLinesBuffer(DoubleBuffer dataBuffer) throws Exception {
            updateBuffer(getLinesBuffer(), dataBuffer);
        }

        public void updateTriangleBuffer(DoubleBuffer dataBuffer) throws Exception {
            updateBuffer(getTriangleBuffer(), dataBuffer);
        }

        private void updateBuffer(int buffer, double[] data) throws Exception {
            updateBuffer(buffer, DoubleBuffer.wrap(data));
        }

        private void updateBuffer(int buffer, DoubleBuffer dataBuffer) throws Exception {
            GL.glBindBuffer(GL_ARRAY_BUFFER, buffer);
            if (GL.glGetError() != 0) { throw new Exception("Error binding buffer on update!"); }
            if (dataBuffer != null) {
                var dataSize = dataBuffer.capacity() * Double.BYTES;
                GL.glBufferData(GL_ARRAY_BUFFER, dataSize, dataBuffer, GL_DYNAMIC_DRAW);
            } else {
                GL.glBufferData(GL_ARRAY_BUFFER, 0, null, GL_DYNAMIC_DRAW);
            }
            if (GL.glGetError() != 0) { throw new Exception("Error setting buffer data!"); }
            GL.glBindBuffer(GL_ARRAY_BUFFER, 0);
        }

        private void updateVAO(int buffer, int vao) throws Exception {
            GL.glBindBuffer(GL_ARRAY_BUFFER, buffer);
            if (GL.glGetError() != 0) { throw new Exception("Error binding buffer!"); }
            GL.glBindVertexArray(vao);
            if (GL.glGetError() != 0) { throw new Exception("Error binding VAO on update!"); }

            GL.glEnableVertexAttribArray(0);
            GL.glVertexAttribPointer(0, 2, GL_DOUBLE, false, 0, null);  // Vertex

            GL.glBindVertexArray(0);
            GL.glDisableVertexAttribArray(0);
        }

        private void updateLinesVAO(int buffer) throws Exception {
            GL.glBindBuffer(GL_ARRAY_BUFFER, buffer);
            if (GL.glGetError() != 0) { throw new Exception("Error binding buffer!"); }
            GL.glBindVertexArray(getLinesVAO());
            if (GL.glGetError() != 0) { throw new Exception("Error binding VAO on update!"); }

            var stride = 5 * Double.BYTES;
            GL.glEnableVertexAttribArray(0);
            GL.glVertexAttribPointer(0, 2, GL_DOUBLE, false, stride, null);  // Vertex
            GL.glVertexAttribPointer(1, 2, GL_DOUBLE, false, stride, null);  // Normal
            GL.glVertexAttribPointer(2, 1, GL_DOUBLE, false, stride, null);  // Width

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
        //GL.glHint(GL_POLYGON_SMOOTH, GL_NICEST);

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

        offset.setX(-100);
        offset.setY(100);
    }

    @Override
    protected void wheelEvent(QWheelEvent event) {
        var numDegrees = event.angleDelta();
        if (!numDegrees.isNull()) {
            if (numDegrees.y() < 0) {
                scale = scale * 1.1f;
            } else {
                scale = scale / 1.1f;
            }
        }
        event.accept();

        // Update scene
        resizeGL(width(), height());
        repaint();
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
            System.out.println("Could not compile vertex shader:");
            System.out.println(vertexShader.log());
        }

        var fragmentShader = new QOpenGLShader(new QOpenGLShader.ShaderType(QOpenGLShader.ShaderTypeBit.Fragment));
        if (!fragmentShader.compileSourceCode("""
                        #version 330
                        uniform vec4 color;
                        void main() {
                            gl_FragColor = color;
                        }
                    """)) {
            System.out.println("Could not compile fragment shader:");
            System.out.println(fragmentShader.log());
        }

        shaderProgram = new QOpenGLShaderProgram(context());
        shaderProgram.addShader(vertexShader);
        shaderProgram.addShader(fragmentShader);
        shaderProgram.link();
    }

    public void updateBuffers(Layer.Type type, Layer layer) throws MergerException {
        var layerBuffer = layerBuffers.get(type);
        if (layerBuffer == null) return;

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
        System.out.println("---- Layer type: " + type + " ----");
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
            throw new MergerException(e.getMessage());
        }

        // Copy data fo OpenGL buffer
        System.out.println("Polygons points count: " + gr.getPolygonsPointsCount());
        try {
            var b = DoubleBuffer.allocate(gr.getPolygonsPointsCount() * 2);
            var s = 0;
            for (var poly : gr.getPolygons()) {
                for (var point : poly) { b.put(point.x()); b.put(point.y()); }
                layerBuffers.get(type).getPolygonMap().add(new PolygonMapItem(s, poly.length()));
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

    void cleanBuffers() {

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
            shaderProgram.setUniformValue("offset", offset);
        } else {
            System.out.println("Could not bind shader program");
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
            GL.glDrawArrays(GL_TRIANGLES, 0, 1000000);
            GL.glBindVertexArray(0);

            // Lines buffer
            GL.glBindVertexArray(layerBuffers.get(layer).getLinesVAO());
            GL.glDrawArrays(GL_LINES, 0, 1000000);
            GL.glBindVertexArray(0);

            height += 0.01; // Place next layer on the top
        }

        // Release
        shaderProgram.release();

        GL.glFlush();
    }

    @Override
    protected void resizeGL(int w, int h) {
        projection.setToIdentity();
        projection.ortho( -w / scale, w / scale, -h / scale, h / scale, -1.0f, 1.0f );
        GL.glViewport(0, 0, w, h);
    }

    @Override
    public void scaleUp(double step) {

    }

    @Override
    public void scaleDown(double step) {

    }

    @Override
    public void moveCenter(double xStepPx, double yStepPx) {
        offset = new QVector2D(
                (float)(offset.x() + xStepPx / scale * 2),
                (float)(offset.y() - yStepPx / scale * 2)
        );
        repaint();
    }

    @Override
    public double getScale() {
        return scale;
    }

    @Override
    public QPointF getCenter() {
        return offset.toPointF();
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
            try { updateBuffers(l, gerberData.getMerger().getMergedBatch().getLayer(l)); }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void clear() {
        cleanBuffers();
        repaint();
    }

    @Override
    public QPointF getScreenCoords(QPoint screenPos) {
        return new QPointF(getCenter().x() + screenPos.x() / getScale() * 2, getCenter().y() - screenPos.y() / getScale() * 2);
    }

}
