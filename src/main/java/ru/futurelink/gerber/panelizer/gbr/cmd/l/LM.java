package ru.futurelink.gerber.panelizer.gbr.cmd.l;

import ru.futurelink.gerber.panelizer.gbr.cmd.Command;

public class LM extends Command {
    public enum Mirroring { N, X, Y, XY };
    final private Mirroring mirroring;

    public LM(Mirroring mirroring) {
        this.mirroring = mirroring;
    }

    static public LM fromString(String str) {
        var t = str.substring(2).replace("*","");
        return new LM(
                t.equalsIgnoreCase("X") ? Mirroring.X :
                t.equalsIgnoreCase("Y") ? Mirroring.Y :
                t.equalsIgnoreCase("XY") ? Mirroring.XY :
                Mirroring.N
        );
    }

    @Override
    public String toString() {
        return "%" + getCommand() + (
                (mirroring == Mirroring.X) ? "X" :
                (mirroring == Mirroring.Y) ? "Y" :
                (mirroring == Mirroring.XY) ? "XY" :
                "N"
        ) +  "*%";
    }
}
