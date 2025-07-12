package com.example.cluster.commands;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.pekko.actor.typed.ActorRef;

public class WaitForResponseCommand implements Command {
    @JsonProperty("replyTo")
    public final ActorRef<CaseResolvedMessage> replyTo;

    @JsonCreator
    public WaitForResponseCommand(@JsonProperty("replyTo") ActorRef<CaseResolvedMessage> replyTo) {
        this.replyTo = replyTo;
    }
}
