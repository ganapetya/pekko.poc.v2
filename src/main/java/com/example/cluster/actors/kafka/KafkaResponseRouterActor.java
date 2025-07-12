package com.example.cluster.actors.kafka;

import com.example.cluster.kafka.KafkaUtil;
import com.example.cluster.kafka.ConsumingRoutingLogic;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.stream.javadsl.Sink;
import com.example.cluster.commands.Command;


/**
 *
 * This Actor is special
 * It is an example of actor that is Kafka consumer
 * It listens to Kafka topic
 * And redirects messages to other actors
 * According to injected logic
 *
 *
 * This is just an example of possible implementation
 * You can do 1 type of such actor per topic (Per Actor/Consumed Message Type is you want)
 *
 * In POC I use 1 type for 2 topics (topic for DeploymentStatus and topic for DeploymentStatusResponse)
 *
 * One topic relevant for 1-st cluster, other relevant for 2-d cluster
 *
 *
 */
public class KafkaResponseRouterActor<T> extends AbstractBehavior<Command> {


    public static <T> Behavior<Command> create(
            ActorSystem<?> system,
            String topic,
            String groupId,
            Class<T> messageClass,
            ConsumingRoutingLogic<T> routingLogic
    ) {
        return Behaviors.setup(ctx -> new KafkaResponseRouterActor<>(ctx, system, topic, groupId, messageClass, routingLogic));
    }

    private final ActorSystem<?> system;
    private final String topic;
    private final String groupId;
    private final Class<T> messageClass;
    private final ConsumingRoutingLogic<T> routingLogic;

    private KafkaResponseRouterActor(
            ActorContext<Command> ctx,
            ActorSystem<?> system,
            String topic,
            String groupId,
            Class<T> messageClass,
            ConsumingRoutingLogic<T> routingLogic
    ) {
        super(ctx);
        this.system = system;
        this.topic = topic;
        this.groupId = groupId;
        this.messageClass = messageClass;
        this.routingLogic = routingLogic;
        startKafkaConsumer(ctx);
    }

    private void startKafkaConsumer(ActorContext<Command> ctx) {
        ctx.getLog().info("[KafkaResponseRouterActor] Subscribing to topic: {} with group: {}", topic, groupId);
        KafkaUtil.consume(system, topic, groupId, messageClass)
            .to(Sink.foreach(message -> routingLogic.handle(message, ctx, system)))
            .run(system);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder().build();
    }
} 