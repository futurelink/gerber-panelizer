package ru.futurelink.gerber.panelizer.gbr.cmd.g;

import lombok.Getter;
import ru.futurelink.gerber.panelizer.gbr.cmd.Command;

public class GCode extends Command {
    @Getter private final int Code;
    @Getter private final String Param;

    public GCode(int code, String param) {
        this.Code = code;
        this.Param = param;
    }

    @Override
    public String toString() {
        return "G" + String.format("%02d%s", Code, (Param != null) ? Param : "") + "*";
    }
}
