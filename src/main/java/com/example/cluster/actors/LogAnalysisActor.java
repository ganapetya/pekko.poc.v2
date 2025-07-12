package com.example.cluster.actors;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import com.example.cluster.commands.AnalyzeLogRequestCommand;
import com.example.cluster.commands.AnalyzeLogResponseCommand;
import com.example.cluster.nodelog.NodeInfoLogger;

/**
 * LogAnalysisActor that processes AnalyzeLogRequest messages
 */
public class LogAnalysisActor extends AbstractBehavior<AnalyzeLogRequestCommand> {

    public static Behavior<AnalyzeLogRequestCommand> create() {
        return Behaviors.setup(LogAnalysisActor::new);
    }

    private LogAnalysisActor(ActorContext<AnalyzeLogRequestCommand> context) {
        super(context);
    }

    @Override
    public Receive<AnalyzeLogRequestCommand> createReceive() {
        return newReceiveBuilder()
            .onMessage(AnalyzeLogRequestCommand.class, this::onAnalyzeLog)
            .build();
    }

    private Behavior<AnalyzeLogRequestCommand> onAnalyzeLog(AnalyzeLogRequestCommand msg) {
        getContext().getLog().info("\uD83D\uDD0D [LogAnalysisActor] Processing log analysis request for case: {}", msg.getCaseId());
        
        processAnalyzeLogRequest(msg);
        
        return Behaviors.stopped(); // stateless, stop after handling
    }

    private void processAnalyzeLogRequest(AnalyzeLogRequestCommand msg) {
        getContext().getLog().info("pocrun {} on Node {}  {} ", "\uD83D\uDC2D", NodeInfoLogger.getNodeInfo(getContext()), "got request to perform log analysis");
        try {
            doProcessAnalyzeLogRequest(msg);
        } catch (Exception ex) {
            getContext().getLog().error("[LogAnalysisActor] Error processing log analysis request: {}", ex.toString());
            throw ex;
        }
    }

    private void doProcessAnalyzeLogRequest(AnalyzeLogRequestCommand msg) {
        String data = "";
        String replyFrom = getContext().getSelf().path().toString();
        try {
            long sleepMillis = java.util.concurrent.ThreadLocalRandom.current().nextLong(300, 2001);
            getContext().getLog().debug("\uD83D\uDD0D [LogAnalysisActor] Starting log analysis for case: {}", msg.getCaseId());
            //simulate some processing time
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Processing interrupted", e);
            }
            data = "Mock log analysis for case " + msg.getCaseId();
            getContext().getLog().info("pocrun {} on Node {}  {} ", "\uD83D\uDC2D", NodeInfoLogger.getNodeInfo(getContext()), "will reply with log analysis data");
            msg.getReplyTo().tell(new AnalyzeLogResponseCommand( msg.getCaseId(), data, replyFrom));
        } catch (Exception ex) {
            data = "Error processing log analysis: " + ex.getMessage();
            getContext().getLog().error("[LogAnalysisActor] Failed in log analysis: caseId={}, reason={}",
                msg.getCaseId(), ex.toString());
            try {
                msg.getReplyTo().tell(new AnalyzeLogResponseCommand(msg.getCaseId(), data, replyFrom));
            } catch (Exception sendEx) {
                getContext().getLog().error("[LogAnalysisActor] Could not deliver error response: caseId={}, reason={}"
                   , msg.getCaseId(), sendEx.toString());
            }
            throw ex;
        }
    }
} 