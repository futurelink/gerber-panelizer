package ru.futurelink.gerber.panelizer.gbr.cmd.d;

import io.qt.core.QPointF;
import io.qt.core.QRectF;
import lombok.Getter;
import ru.futurelink.gerber.panelizer.canvas.Geometry;
import ru.futurelink.gerber.panelizer.gbr.cmd.FS;

import java.math.BigDecimal;

public class D01To03 extends DAperture {
    @Getter private final double x;
    @Getter private final double y;

    @Getter private final Double i;
    @Getter private final Double j;

    public D01To03(Integer code, double x, double y) {
        this(code, x, y, null, null);
    }

    public D01To03(Integer code, double x, double y, Double i, Double j) {
        super(code);
        this.x = x;
        this.y = y;
        this.i = i;
        this.j = j;
    }

    public D01To03(Integer code, String x, String y, FS format) {
        this(code, parse(x, format.getXFractional()), parse(y, format.getYFractional()));
    }

    public D01To03(Integer code, String x, String y, String i, String j, FS format) {
        this(code,
                parse(x, format.getXFractional()),
                parse(y, format.getYFractional()),
                parse(i, format.getXFractional()),
                parse(j, format.getYFractional())
        );
    }

    private static Double parse(String val, int fractionalLen) {
        if (val == null) return null;
        return BigDecimal.valueOf(Double.parseDouble(val)).movePointLeft(fractionalLen).doubleValue();
    }

    public D01To03 move(double xOffset, double yOffset) {
        return new D01To03(this.getCode(), this.x + xOffset, this.y + yOffset, i, j);
    }

    public String toString(FS format) {
        var xStr = BigDecimal.valueOf(x).movePointRight(format.getXFractional()).toBigInteger();
        var yStr = BigDecimal.valueOf(y).movePointRight(format.getYFractional()).toBigInteger();
        var iStr = ((i != null) ? "I" + BigDecimal.valueOf(i).movePointRight(format.getXFractional()).toBigInteger() : "");
        var jStr = ((j != null) ? "J" + BigDecimal.valueOf(j).movePointRight(format.getYFractional()).toBigInteger() : "");
        return String.format("X%sY%s%s%sD%02d", xStr, yStr, iStr, jStr, getCode()) + "*" ;
    }

    @Override
    public String toString() {
        var xStr = BigDecimal.valueOf(x).toPlainString();
        var yStr = BigDecimal.valueOf(y).toPlainString();
        var iStr = ((i != null) ? "I" + i : "");
        var jStr = ((j != null) ? "J" + j : "");
        return String.format("X%s Y%s %s %s D%02d", xStr, yStr, iStr, jStr, getCode());
    }
}
