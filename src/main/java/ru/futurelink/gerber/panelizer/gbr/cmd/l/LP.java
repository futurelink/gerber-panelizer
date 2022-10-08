package ru.futurelink.gerber.panelizer.gbr.cmd.l;

import ru.futurelink.gerber.panelizer.gbr.cmd.Command;

public class LP extends Command {
    public enum Polarity { DARK, LIGHT };
    final private Polarity polarity;

    public LP(Polarity pol) {
        this.polarity = pol;
    }

    static public LP fromString(String str) {
        var t = str.substring(2).replace("*","");
        return new LP(t.equalsIgnoreCase("D") ? Polarity.DARK : Polarity.LIGHT);
    }

    @Override
    public String toString() {
        return "%" + getCommand() + ((polarity == Polarity.DARK) ? "D" : "L") +  "*%";
    }
}
