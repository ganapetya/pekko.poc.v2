package com.example.cluster.commands;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DeploymentStatusRequestCommand implements Command {
    public final String requestId;
    public final String caseId;
    @JsonCreator
    public DeploymentStatusRequestCommand(
            @JsonProperty("requestId") String requestId,
            @JsonProperty("caseId") String caseId) {
        this.requestId = requestId;
        this.caseId = caseId;
    }
} 