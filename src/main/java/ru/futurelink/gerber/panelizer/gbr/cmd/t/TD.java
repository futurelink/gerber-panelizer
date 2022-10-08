package ru.futurelink.gerber.panelizer.gbr.cmd.t;

import ru.futurelink.gerber.panelizer.gbr.cmd.CommandNamed;

import java.util.Arrays;

public class TD extends CommandNamed {
    public TD(String name, String ... params) {
        super(name, params);
    }

    static public TD fromString(String str) {
        var t = str.substring(2).replace("*","").split(",");
        var name = t[0];
        var params = Arrays.copyOfRange(t, 1, t.length);
        return new TD(name, params);
    }
}
