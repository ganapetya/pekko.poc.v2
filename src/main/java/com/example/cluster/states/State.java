package com.example.cluster.states;

import com.example.cluster.serialize.CborSerializable;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

// State
public class State implements CborSerializable {
    public final String caseId;
    public final boolean isResolved;
    private final int resolveCount;

    @JsonCreator
    public State(@JsonProperty("caseId") String caseId,
                 @JsonProperty("isResolved") boolean isResolved,
                 @JsonProperty("resolveCount") int resolveCount) {
        this.caseId = caseId;
        this.isResolved = isResolved;
        this.resolveCount = resolveCount;
    }

    public static State empty(String caseId) {
        return new State(caseId, false,  0);
    }

    public State withIncrementedResolveCount() {
        return new State(caseId, true, resolveCount + 1);
    }

    public int getResolveCount() {
        return resolveCount;
    }
}
