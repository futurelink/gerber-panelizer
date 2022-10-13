package ru.futurelink.gerber.panelizer.canvas;

import lombok.Getter;
import ru.futurelink.gerber.panelizer.gui.Utils;

import java.util.ArrayList;

public class Macro {

    public final static class OperationResult {
        @Getter private final Operation.Type type;
        @Getter private final Boolean exposure;
        private final ArrayList<Double> values;

        OperationResult(Operation.Type type, boolean exposure) {
            this.type = type;
            this.exposure = exposure;
            this.values = new ArrayList<>();
        }

        public double getValue(int index) {
            return values.get(index);
        }

        @Override
        public String toString() {
            return String.format("Result: %s, %s", type, values);
        }
    }

    public final static class Operation {
        public enum Type { Circle, VectorLine, CenterLine, Outline, Polygon }
        private final Type type;
        private final Boolean exposure;
        private final ArrayList<String> params;

        private Operation(Type type, boolean exposure) {
            this.type = type;
            this.exposure = exposure;
            this.params = new ArrayList<>();
        }

        private void addParameter(String p) {
            params.add(p);
        }

        public OperationResult eval(final Double[] operationParams) {
            var res = new OperationResult(type, exposure);
            for (var p : params) {
                res.values.add(Utils.eval(substVars(p, operationParams)));
            }
            return res;
        }

        private String substVars(final String p, Double[] params) {
            var r = p;
            for (var i = 0; i < params.length; i++) r = r.replaceAll("\\$" + (i+1), String.format("%f", params[i]));
            return r;
        }


        public static Operation fromString(String paramsString) {
            if (!paramsString.startsWith("0")) {
                var params = paramsString.split(",");
                var i = 0;
                var type = switch (params[i].trim()) {
                    case "1" -> Type.Circle;
                    case "20" -> Type.VectorLine;
                    case "21" -> Type.CenterLine;
                    case "4" -> Type.Outline;
                    case "5" -> Type.Polygon;
                    default -> null;
                };
                var o = new Operation(type, params[++i].trim().equals("1"));
                while (++i < params.length) o.addParameter(params[i].trim());
                return o;
            }
            return null;
        }

        @Override
        public String toString() {
            return String.format("Operation: %s, %s", type, params);
        }
    }

    @Getter ArrayList<Operation> operations;

    public Macro(ArrayList<String> params) {
        operations = new ArrayList<>();
        params.forEach(p -> {
            var o = Operation.fromString(p);
            if (o != null) operations.add(o);
        });
    }

    public ArrayList<OperationResult> eval(Double[] params) {
        var res = new ArrayList<OperationResult>();
        for (var o : operations) {
            res.add(o.eval(params));
        }
        return res;
    }

}
