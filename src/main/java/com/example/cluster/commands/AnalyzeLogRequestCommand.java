package com.example.cluster.commands;

import org.apache.pekko.actor.typed.ActorRef;

public class AnalyzeLogRequestCommand implements Command {
    private final String caseId;
    public final ActorRef<Command> replyTo;

    public AnalyzeLogRequestCommand(String caseId, ActorRef<Command> replyTo) {
        this.replyTo = replyTo;
        this.caseId = caseId;
    }

    public String getCaseId() {
        return caseId;
    }

    public ActorRef<Command> getReplyTo() {
        return replyTo;
    }
}
