package com.example.cluster.commands;

import com.fasterxml.jackson.annotation.JsonCreator;

public class TimeoutCommand implements Command {
    @JsonCreator
    public TimeoutCommand() {
    }
}
