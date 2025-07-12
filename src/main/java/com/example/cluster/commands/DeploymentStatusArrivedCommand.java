package com.example.cluster.commands;

// Command for deployment status arrival (you might already have this)
public final class DeploymentStatusArrivedCommand implements Command {
    public final String caseId;
    public final String deploymentStatusJson;

    public DeploymentStatusArrivedCommand(String caseId, String deploymentStatusJson) {
        this.caseId = caseId;
        this.deploymentStatusJson = deploymentStatusJson;
    }
}
