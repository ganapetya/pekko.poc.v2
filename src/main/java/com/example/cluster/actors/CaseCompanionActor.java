package com.example.cluster.actors;

import com.example.cluster.events.CaseResolvedCountIncremented;
import com.example.cluster.commands.*;
import com.example.cluster.events.Event;
import com.example.cluster.commands.CaseResolvedMessage;
import com.example.cluster.nodelog.NodeInfoLogger;
import com.example.cluster.states.State;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.DispatcherSelector;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.apache.pekko.persistence.typed.PersistenceId;
import org.apache.pekko.persistence.typed.javadsl.CommandHandler;
import org.apache.pekko.persistence.typed.javadsl.EventHandler;
import org.apache.pekko.persistence.typed.javadsl.EventSourcedBehavior;
import org.apache.pekko.persistence.typed.javadsl.ReplyEffect;

import java.util.ArrayList;
import java.util.List;

import org.apache.pekko.cluster.pubsub.DistributedPubSub;
import org.apache.pekko.cluster.pubsub.DistributedPubSubMediator;
import com.example.cluster.kafka.KafkaUtil;
import com.example.cluster.commands.DeploymentStatusRequestCommand;

import java.util.UUID;


public class CaseCompanionActor extends EventSourcedBehavior<Command, Event, State> {

    public static final EntityTypeKey<Command> ENTITY_TYPE_KEY =
            EntityTypeKey.create(Command.class, "CaseCompanion");

    private final String caseId;
    private final ActorContext<Command> context;
    private final List<String> logAnalysisResponses = new ArrayList<>();
    private final org.apache.pekko.actor.ActorRef mediator;

    // Store current response topic for PubSub
    private String currentResponseTopicId = null;

    public static Behavior<Command> create(String caseId) {
        return Behaviors.setup(context -> new CaseCompanionActor(PersistenceId.of("CaseCompanion", caseId), caseId, context));
    }

    private CaseCompanionActor(PersistenceId persistenceId, String caseId, ActorContext<Command> context) {
        super(persistenceId);
        this.caseId = caseId;
        this.context = context;
        this.mediator = DistributedPubSub.get(context.getSystem()).mediator();
    }

    @Override
    public State emptyState() {
        return State.empty(caseId);
    }

    @Override
    public CommandHandler<Command, Event, State> commandHandler() {
        return newCommandHandlerBuilder()
                .forAnyState()
                .onCommand(ResolveCaseCommand.class, this::onResolveCase)
                .onCommand(AnalyzeLogResponseCommand.class, this::onWrappedLogAnalysisResponse)
                .onCommand(DeploymentStatusArrivedCommand.class, this::onDeploymentStatusArrived)
                .build();
    }

    // Handle ResolveCase command - works for both local and remote via PubSub
    private ReplyEffect<Event, State> onResolveCase(State state, ResolveCaseCommand command) {
        if (command.hasResponseTopic()) {
            // PubSub case: store the response topic for later use
            context.getLog().info("pocrun {} on Node {} {} (PubSub topic: {})",
                    "\uD83D\uDC31", NodeInfoLogger.getNodeInfo(context), "starts case resolution", command.getResponseTopicId());
            this.currentResponseTopicId = command.getResponseTopicId();
        } else {
            // Async case: no response expected
            this.currentResponseTopicId = null;
            throw new RuntimeException("I did not intend to support a case here, where client gets async response");
        }

        try {
            doResolveCase(command.getCaseId());
            return Effect().noReply();
        } catch (Exception ex) {
            context.getLog().error("[CaseCompanionActor] Error in onResolveCase: {}", ex.toString());
            throw ex;
        }
    }

    private void doResolveCase(String caseId) {
        //stage 1 - will create and contact local actor for log analysis
        context.getLog().info("[CaseCompanionActor] Starting case resolution for case: {}", caseId);
        String logAnalysisActorName = "log-analysis-" + caseId;
        var logAnalysisActor = context.spawn(LogAnalysisActor.create(), logAnalysisActorName, DispatcherSelector.fromConfig("pekko.actor.dispatchers.log-analysis-dispatcher"));
        AnalyzeLogRequestCommand logAnalysisRequest = new AnalyzeLogRequestCommand(caseId, context.getSelf());
        logAnalysisActor.tell(logAnalysisRequest);
    }

    private ReplyEffect<Event, State> onWrappedLogAnalysisResponse(State state, AnalyzeLogResponseCommand response) {
        //stage 2 - get reply from the local actor with result of the log analysis
        context.getLog().info("pocrun {} on Node {} {}", "\uD83D\uDC31", NodeInfoLogger.getNodeInfo(context), "gets data from LogAnalysisActor");
        logAnalysisResponses.add(response.toString());


        //stage 3 - send message to 2-d cluster using Kafka
        consultDeploymentActorInOtherCluster(response);
        return Effect().noReply();
    }

    private void consultDeploymentActorInOtherCluster(AnalyzeLogResponseCommand response) {
        try {
            context.getLog().info("pocrun {} on Node {} {}", "\uD83D\uDC31", NodeInfoLogger.getNodeInfo(context), "calls DeploymentActor in other cluster via Kafka");
            //I do not correlate future response till request ID - it is too fine a granularity for POC
            //I correlate future response till case ID only, making sure response comes to this stateful actor
            //but this shows that we also can correlate to requestId level and do some logic on that
            String requestId = UUID.randomUUID().toString();
            DeploymentStatusRequestCommand req = new DeploymentStatusRequestCommand(requestId, response.caseId);
            KafkaUtil.produce(context.getSystem(),
                context.getSystem().settings().config().getConfig("kafka").getConfig("topics").getString("deployment-requests"),
                requestId,
                req
            );
        } catch (Exception e) {
            context.getLog().error("[CaseCompanionActor] Error sending Kafka request: {}", e.toString());
        }
    }

    private ReplyEffect<Event, State> onDeploymentStatusArrived(State state, DeploymentStatusArrivedCommand msg) {
        context.getLog().info("pocrun {} on Node {} {}", "\uD83D\uDE80\uD83D\uDC31", NodeInfoLogger.getNodeInfo(context), "got response from actor in other cluster via Kafka");

        // Create the summary message
        String summary = String.format(
                "LogAnalysisResults: %s, DeploymentStatus: %s, TotalResolutions: %d",
                logAnalysisResponses,
                msg.deploymentStatusJson,
                state.getResolveCount() + 1 // +1 because we're about to increment
        );

        if (currentResponseTopicId != null) {
            // PubSub case: publish response to the distributed pub-sub topic
            String topicId = currentResponseTopicId;
            this.currentResponseTopicId = null; // Clear it

            //here we persist new state (which in our case is a CaseResolvedCountIncremented event)
            //and send response to the topic
            return Effect()
                    .persist(new CaseResolvedCountIncremented(msg.caseId))
                    .thenRun(newState -> {
                        CaseResolvedMessage response = new CaseResolvedMessage(caseId, summary);

                        context.getLog().info("Publishing response to topic: {}", topicId);
                        mediator.tell(new DistributedPubSubMediator.Publish(
                                topicId,
                                new ResponseReceivedCommand(response)
                        ), org.apache.pekko.actor.ActorRef.noSender());
                    })
                    .thenNoReply();
        } else {
            // No pending reply (async case)
            // we did not implement that
            throw new RuntimeException("We are not intended to support that");
        }
    }

    @Override
    public EventHandler<State, Event> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(CaseResolvedCountIncremented.class, (state, event) -> state.withIncrementedResolveCount())
                .build();
    }
}