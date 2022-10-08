package ru.futurelink.gerber.panelizer.gbr.cmd;

public class MO extends Command {
    public enum Mode { MM, INCH };

    private final Mode mode;

    public MO(Mode mode) {
        this.mode = mode;
    }

    static public MO fromString(String str) {
        var t = str.substring(2).replace("*","");
        return new MO(t.equalsIgnoreCase("MM") ? Mode.MM : Mode.INCH);
    }

    @Override
    public String toString() {
        return "%" + getCommand() + ((mode == Mode.MM) ? "MM" : "INCH") +  "*%";
    }
}
