package com.example.cluster.actors;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import com.example.cluster.nodelog.NodeInfoLogger;
import com.example.cluster.commands.Command;
import com.example.cluster.kafka.KafkaUtil;
import com.example.cluster.commands.DeploymentStatusRequestCommand;
import com.example.cluster.commands.DeploymentStatusKafkaResponseCommand;
import com.example.cluster.commands.WrappedDeploymentStatusRequestCommand;

import java.util.List;
import java.util.Arrays;

public class DeploymentMonitoringActor extends AbstractBehavior<Command> {
    public static final EntityTypeKey<Command> ENTITY_TYPE_KEY =
        EntityTypeKey.create(Command.class, "DeploymentMonitoring");

    private final String caseId;
    private final ActorContext<Command> context;

    public static Behavior<Command> create(String caseId) {
        return Behaviors.setup(ctx -> new DeploymentMonitoringActor(ctx, caseId));
    }

    private DeploymentMonitoringActor(ActorContext<Command> context, String caseId) {
        super(context);
        this.context = context;
        this.caseId = caseId;
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(WrappedDeploymentStatusRequestCommand.class, this::onDeploymentStatusRequest)
            .build();
    }

    private Behavior<Command> onDeploymentStatusRequest(WrappedDeploymentStatusRequestCommand msg) {
        DeploymentStatusRequestCommand request = msg.request;
        context.getLog().info("pocrun {} on Node {}  {} ", "\uD83D\uDC36", NodeInfoLogger.getNodeInfo(context), "got Kafka request to check deployment status");
        // Mocked operation
        List<String> healthy = Arrays.asList("service-a", "service-b", "service-c");
        List<String> failed = Arrays.asList("service-x");
        DeploymentStatusKafkaResponseCommand response = new DeploymentStatusKafkaResponseCommand(
            request.requestId, request.caseId, healthy, failed
        );
        KafkaUtil.produce(context.getSystem(),
            context.getSystem().settings().config().getConfig("kafka").getConfig("topics").getString("deployment-responses"),
            request.requestId,
            response
        );
        context.getLog().info("pocrun {} on Node {}  {} ", "\uD83D\uDC36", NodeInfoLogger.getNodeInfo(context), "sent response through Kafka with deployment status in it");
        return this;
    }

}