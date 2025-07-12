package com.example.cluster.commands;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ResponseReceivedCommand implements Command {
    @JsonProperty("response")
    public final CaseResolvedMessage response;

    @JsonCreator
    public ResponseReceivedCommand(@JsonProperty("response") CaseResolvedMessage response) {
        this.response = response;
    }
}
