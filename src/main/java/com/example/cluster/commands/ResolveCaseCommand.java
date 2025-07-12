package com.example.cluster.commands;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;


public final class ResolveCaseCommand implements Command {
    private final String caseId;
    private final String responseTopicId;

    // Constructor for JSON entity routes (with response topic for PubSub)
    @JsonCreator
    public ResolveCaseCommand(
            @JsonProperty("caseId") String caseId,
            @JsonProperty("responseTopicId") String responseTopicId) {
        this.caseId = caseId;
        this.responseTopicId = responseTopicId;
    }

    // Constructor for simple cases (backward compatibility)
    public ResolveCaseCommand(String caseId) {
        this.caseId = caseId;
        this.responseTopicId = null;
    }

    public String getCaseId() {
        return caseId;
    }

    public String getResponseTopicId() {
        return responseTopicId;
    }

    public boolean hasResponseTopic() {
        return responseTopicId != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResolveCaseCommand that = (ResolveCaseCommand) o;
        return Objects.equals(caseId, that.caseId) &&
               Objects.equals(responseTopicId, that.responseTopicId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(caseId, responseTopicId);
    }

    @Override
    public String toString() {
        return "ResolveCase{" +
                "caseId='" + caseId + '\'' +
                ", responseTopicId='" + responseTopicId + '\'' +
                '}';
    }
}