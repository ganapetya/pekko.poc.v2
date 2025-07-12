package com.example.cluster.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Actual event that persisted in the event journal and restored as actor state
 * **/
public class CaseResolvedCountIncremented implements Event {
    public final String caseId;
    @JsonCreator
    public CaseResolvedCountIncremented(@JsonProperty("caseId")String caseId) {
        this.caseId = caseId;
    }
}
