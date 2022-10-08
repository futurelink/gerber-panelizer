package ru.futurelink.gerber.panelizer.gbr.cmd;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class CommandNamed extends Command {
    @Getter
    private final String Name;

    @Getter
    private final ArrayList<String> Params;

    public CommandNamed(String name) {
        this.Name = name;
        this.Params = new ArrayList<>();
    }

    public CommandNamed(String name, String ... params) {
        this(name);
        Params.addAll(Arrays.asList(params));
    }

    @Override
    public String toString() {
        return "%" +
                getCommand() +
                getName() +
                (Params.isEmpty() ? "" : "," + String.join(",", getParams())) +
                "*%";
    }

}
