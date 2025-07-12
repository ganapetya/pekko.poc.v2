package com.example.cluster.commands;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CaseResolvedMessage implements Command {
    @JsonProperty("caseId")
    public final String caseId;

    @JsonProperty("summary")
    public final String summary;

    @JsonCreator
    public CaseResolvedMessage(
            @JsonProperty("caseId") String caseId,
            @JsonProperty("summary") String summary
    ) {
        this.caseId = caseId;
        this.summary = summary;
    }

    @Override
    public String toString() {
        return String.format("CaseResolvedMessage{caseId='%s', summary='%s'}", caseId, summary);
    }

    public String getCaseId() {
        return caseId;
    }

    public String getSummary() {
        return summary;
    }
}