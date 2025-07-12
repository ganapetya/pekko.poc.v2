package com.example.cluster.commands;

public class AnalyzeLogResponseCommand implements Command {

    public final String caseId;
    public final String data;
    public final String replyFrom;

    public AnalyzeLogResponseCommand(String caseId, String data, String replyFrom) {
        this.caseId = caseId;
        this.data = data;
        this.replyFrom = replyFrom;
    }

    @Override
    public String toString() {
        return "AnalyzeLogResponse[uuid=" + ", caseId=" + caseId + ", data=" + data + ", replyFrom=" + replyFrom + "]";
    }
}
