package ru.futurelink.gerber.panelizer.gbr.cmd.d;

import lombok.Getter;
import ru.futurelink.gerber.panelizer.gbr.cmd.FS;

import java.math.BigDecimal;

public class D01To03 extends DAperture {
    @Getter private final BigDecimal x;
    @Getter private final BigDecimal y;

    @Getter private BigDecimal i;
    @Getter private BigDecimal j;

    public D01To03(Integer code, BigDecimal x, BigDecimal y) {
        super(code);
        this.x = x;
        this.y = y;
        this.i = null;
        this.j = null;
    }

    public D01To03(Integer code, BigDecimal x, BigDecimal y, BigDecimal i, BigDecimal j) {
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
                parse(x, format.getXFractional()), parse(y, format.getYFractional()),
                parse(i, format.getXFractional()), parse(j, format.getYFractional())
        );
    }

    private static BigDecimal parse(String val, int fractionalLen) {
        if (val == null) return null;
        return BigDecimal.valueOf(Double.parseDouble(val)).movePointLeft(fractionalLen);
    }

    public D01To03 move(BigDecimal xOffset, BigDecimal yOffset) {
        return new D01To03(this.getCode(), this.x.add(xOffset), this.y.add(yOffset), i, j);
    }

    public String toString(FS format) {
        var xStr = x.movePointRight(format.getXFractional()).toBigInteger();
        var yStr = y.movePointRight(format.getYFractional()).toBigInteger();
        var iStr = ((i != null) ? "I" + i.movePointRight(format.getXFractional()).toBigInteger() : "");
        var jStr = ((j != null) ? "J" + j.movePointRight(format.getYFractional()).toBigInteger() : "");
        return String.format("X%sY%s%s%sD%02d", xStr, yStr, iStr, jStr, getCode()) + "*" ;
    }

    @Override
    public String toString() {
        var xStr = x.toPlainString();
        var yStr = y.toPlainString();
        var iStr = ((i != null) ? "I" + i : "");
        var jStr = ((j != null) ? "J" + j : "");
        return String.format("X%s Y%s %s %s D%02d", xStr, yStr, iStr, jStr, getCode());
    }
}
