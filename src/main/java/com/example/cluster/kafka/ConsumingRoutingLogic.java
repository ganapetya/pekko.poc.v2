package com.example.cluster.kafka;

import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.ActorSystem;

@FunctionalInterface
public interface ConsumingRoutingLogic<T> {
    void handle(T message, ActorContext<?> ctx, ActorSystem<?> system);
} 