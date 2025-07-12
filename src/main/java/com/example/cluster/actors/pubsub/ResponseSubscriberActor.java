package com.example.cluster.actors.pubsub;

import com.example.cluster.commands.*;
import com.example.cluster.commands.TimeoutCommand;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;
import org.apache.pekko.cluster.pubsub.DistributedPubSubMediator;

import java.time.Duration;

// Response Subscriber Actor for Distributed PubSub
public class ResponseSubscriberActor extends AbstractBehavior<Command> {

    private final String topicId;
    private ActorRef<CaseResolvedMessage> pendingReplyTo;
    private final org.apache.pekko.actor.ActorRef mediator;

    public static Behavior<Command> create(String topicId, org.apache.pekko.actor.ActorRef mediator) {
        return Behaviors.setup(context -> new ResponseSubscriberActor(context, topicId, mediator));
    }

    private ResponseSubscriberActor(ActorContext<Command> context, String topicId, org.apache.pekko.actor.ActorRef mediator) {
        super(context);
        this.topicId = topicId;
        this.mediator = mediator;
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(WaitForResponseCommand.class, this::onWaitForResponse)
                .onMessage(ResponseReceivedCommand.class, this::onResponseReceived)
                .onMessage(TimeoutCommand.class, this::onTimeout)
                .build();
    }

    private Behavior<Command> onWaitForResponse(WaitForResponseCommand cmd) {
        this.pendingReplyTo = cmd.replyTo;

        // Subscribe to the response topic
        mediator.tell(new DistributedPubSubMediator.Subscribe(topicId, Adapter.toClassic(getContext().getSelf())),
                org.apache.pekko.actor.ActorRef.noSender());

        // Set a timeout to clean up if no response comes
        getContext().scheduleOnce(
                Duration.ofSeconds(30),
                getContext().getSelf(),
                new TimeoutCommand()
        );

        return this;
    }

    private Behavior<Command> onResponseReceived(ResponseReceivedCommand cmd) {
        getContext().getLog().info("Received response for topic {}: {}", topicId, cmd.response);

        if (pendingReplyTo != null) {
            pendingReplyTo.tell(cmd.response);
        }

        // Unsubscribe and stop
        mediator.tell(new DistributedPubSubMediator.Unsubscribe(topicId, Adapter.toClassic(getContext().getSelf())),
                org.apache.pekko.actor.ActorRef.noSender());
        return Behaviors.stopped();
    }

    private Behavior<Command> onTimeout(TimeoutCommand cmd) {
        getContext().getLog().warn("Timeout waiting for response on topic: {}", topicId);

        if (pendingReplyTo != null) {
            pendingReplyTo.tell(new CaseResolvedMessage("timeout", "Request timed out"));
        }

        // Unsubscribe and stop
        mediator.tell(new DistributedPubSubMediator.Unsubscribe(topicId, Adapter.toClassic(getContext().getSelf())),
                org.apache.pekko.actor.ActorRef.noSender());
        return Behaviors.stopped();
    }
}
