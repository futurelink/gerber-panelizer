package ru.futurelink.gerber.panelizer.gbr.cmd;

public abstract class Command {
    protected static Integer getDigits(String str, Integer index) {
        var endIndex = index;
        while ((str.charAt(endIndex) >= '0') && (str.charAt(endIndex) <= '9')) {
            endIndex++;
        }
        return Integer.parseInt(str.substring(index, endIndex));
    }

    public String getCommand() {
        return getClass().getSimpleName();
    }

    public String toString() {
        return "%" + getCommand() + "*%";
    }
}
