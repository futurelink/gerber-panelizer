package ru.futurelink.gerber.panelizer.gbr.cmd.d;

import lombok.Getter;
import ru.futurelink.gerber.panelizer.gbr.cmd.Command;

public class DAperture extends Command {
    @Getter private final Integer Code;

    public DAperture(Integer code) {
        this.Code = code;
    }

    @Override
    public String toString() {
        return "D" + String.format("%d", Code) + "*" ;
    }
}
