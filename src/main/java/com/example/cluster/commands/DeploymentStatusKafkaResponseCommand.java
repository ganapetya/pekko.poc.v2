package com.example.cluster.commands;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class DeploymentStatusKafkaResponseCommand implements Command {
    public final String requestId;
    public final String caseId;
    public final List<String> healthyServices;
    public final List<String> failedServices;

    @JsonCreator
    public DeploymentStatusKafkaResponseCommand(
            @JsonProperty("requestId") String requestId,
            @JsonProperty("caseId") String caseId,
            @JsonProperty("healthyServices") List<String> healthyServices,
            @JsonProperty("failedServices") List<String> failedServices) {
        this.requestId = requestId;
        this.caseId = caseId;
        this.healthyServices = healthyServices;
        this.failedServices = failedServices;
    }
} 