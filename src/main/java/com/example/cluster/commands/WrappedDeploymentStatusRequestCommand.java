package com.example.cluster.commands;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class WrappedDeploymentStatusRequestCommand implements Command {
    @JsonProperty("request")
    public final DeploymentStatusRequestCommand request;

    @JsonCreator
    public WrappedDeploymentStatusRequestCommand(@JsonProperty("request") DeploymentStatusRequestCommand request) {
        this.request = request;
    }
}
