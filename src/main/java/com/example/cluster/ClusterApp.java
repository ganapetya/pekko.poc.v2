package com.example.cluster;

import com.example.cluster.actors.kafka.KafkaResponseRouterActor;
import com.example.cluster.commands.*;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.Props;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;
import org.apache.pekko.management.javadsl.PekkoManagement;
import com.example.cluster.actors.CaseCompanionActor;
import com.example.cluster.http.HttpServer;
import com.typesafe.config.Config;
import kamon.Kamon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main application class that starts the Pekko cluster
 */
public class ClusterApp extends AbstractBehavior<Command> {

    private static final int LB_INTERNAL_PORT = 80;
    private static final Logger log = LoggerFactory.getLogger(ClusterApp.class);

    public static class StartComplete implements Command {
        public final String message;
        
        public StartComplete(String message) {
            this.message = message;
        }
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(context -> {
            ActorSystem<?> system = context.getSystem();
            Config config = system.settings().config();
            Logger log = LoggerFactory.getLogger(ClusterApp.class);
            
            // 1. Extract configuration first
            String configuredClusterName = config.hasPath("pekko.cluster.name") 
                ? config.getString("pekko.cluster.name") : "";
            log.info("[ClusterApp] Resolved CLUSTER_NAME = '{}'", configuredClusterName);
            
            int httpPort = config.hasPath("http.port") ? config.getInt("http.port") : 8080;
            
            // 2. Start Pekko Management early
            PekkoManagement.get(system).start();
            log.info("Pekko Management started");
            
            // 3. Branch based on cluster type
            if ("ClusterA".equalsIgnoreCase(configuredClusterName)) {
                initializeClusterA(context, system, httpPort);
            } else if ("ClusterB".equalsIgnoreCase(configuredClusterName)) {
                initializeClusterB(context, system, httpPort);
            } else {
                log.error("Unknown cluster name: '{}'", configuredClusterName);
            }
            
            context.getLog().info("Cluster node startup completed");
            return new ClusterApp(context);
        });
    }

    private static void initializeClusterA(ActorContext<Command> context, ActorSystem<?> system, int httpPort) {
        Logger log = LoggerFactory.getLogger(ClusterApp.class);
        log.info("[ClusterApp] Starting as ClusterA (CaseResolverService)");
        context.getLog().info("[ClusterA] Starting CaseCompanionActor");
        // Initialize sharding for ClusterA
        ClusterSharding sharding = ClusterSharding.get(system);
        sharding.init(
            Entity.of(
                CaseCompanionActor.ENTITY_TYPE_KEY,
                entityContext -> CaseCompanionActor.create(entityContext.getEntityId())
            ).withEntityProps(Props.empty().withDispatcherFromConfig("pekko.actor.dispatchers.case-companion-dispatcher"))
        );
        system.log().info("✅ Initialized CaseCompanion sharding with dispatcher: case-companion-dispatcher");
        
        //in this router actor we inject logic, which will allow to find sharded CaseCompanionActor by caseId
        var kafkaConfig = system.settings().config().getConfig("kafka");
        String responsesTopic = kafkaConfig.getConfig("topics").getString("deployment-responses");
        String groupId = kafkaConfig.getString("consumer-group");
        
        context.spawn(
            KafkaResponseRouterActor.create(
                system,
                responsesTopic,
                groupId,
                DeploymentStatusKafkaResponseCommand.class,
                (response, ctx, sys) -> {
                    ClusterSharding shardingCaseCompanon = ClusterSharding.get(sys);
                    var entityRef = shardingCaseCompanon.entityRefFor(
                        com.example.cluster.actors.CaseCompanionActor.ENTITY_TYPE_KEY,
                        response.caseId
                    );
                    entityRef.tell(new DeploymentStatusArrivedCommand(response.caseId, response.toString()));
                }
            ),
            "kafka-router-actor-for-case-companion"
        );
        // Start HTTP server
        startHttpServer(context, system, sharding, httpPort);
    }

    private static void initializeClusterB(ActorContext<Command> context, ActorSystem<?> system, int httpPort) {
        Logger log = LoggerFactory.getLogger(ClusterApp.class);
        log.info("[ClusterApp] Starting as ClusterB (OpsService)");
        context.getLog().info("[ClusterB] Starting DeploymentMonitoringActor");
        // Initialize sharding for DeploymentMonitoringActor
        ClusterSharding sharding = ClusterSharding.get(system);
        sharding.init(
            Entity.of(
                com.example.cluster.actors.DeploymentMonitoringActor.ENTITY_TYPE_KEY,
                entityContext -> com.example.cluster.actors.DeploymentMonitoringActor.create(entityContext.getEntityId())
            )
        );
        // Start HTTP server (no sharding needed for ClusterB)
        HttpServer httpServer = new HttpServer(system, null);
        httpServer.start("0.0.0.0", httpPort)
            .thenAccept(binding -> {
                context.getLog().info("HTTP server started on port: {}", httpPort);
                context.getSelf().tell(new StartComplete("HTTP server started on port " + httpPort));
            })
            .exceptionally(ex -> {
                context.getLog().error("HTTP server startup failed", ex);
                return null;
            });

        //in this router actor we inject logic, which will allow to find sharded DeploymentMonitoringActor by caseId
        var kafkaConfig = system.settings().config().getConfig("kafka");
        String topic = kafkaConfig.getConfig("topics").getString("deployment-requests");
        String groupId = kafkaConfig.getString("consumer-group");


        context.spawn(
            KafkaResponseRouterActor.create(
                system,
                topic,
                groupId,
                DeploymentStatusRequestCommand.class,
                (request, ctx, sys) -> {
                    ClusterSharding deploymentActorSharding = ClusterSharding.get(sys);
                    var entityRef = deploymentActorSharding.entityRefFor(
                        com.example.cluster.actors.DeploymentMonitoringActor.ENTITY_TYPE_KEY,
                        request.caseId
                    );
                    entityRef.tell(new WrappedDeploymentStatusRequestCommand(request));
                }
            ),
            "kafka-router-actor-for-deployment-requests"
        );
    }

    private static void startHttpServer(ActorContext<Command> context, ActorSystem<?> system, 
                                      ClusterSharding sharding, int httpPort) {
        try {
            HttpServer httpServer = new HttpServer(system, sharding);
            httpServer.start("0.0.0.0", httpPort)
                .thenAccept(binding -> {
                    context.getLog().info("HTTP server started on port: {}", httpPort);
                    context.getSelf().tell(new StartComplete("HTTP server started on port " + httpPort));
                })
                .exceptionally(ex -> {
                    context.getLog().error("HTTP server startup failed", ex);
                    return null;
                });
        } catch (Exception e) {
            context.getLog().error("HTTP server initialization failed", e);
        }
    }

    // Constructor now only handles actor behavior setup
    private ClusterApp(ActorContext<Command> context) {
        super(context);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(StartComplete.class, this::onStartComplete)
            .build();
    }

    private Behavior<Command> onStartComplete(StartComplete message) {
        getContext().getLog().info("Startup complete: {}", message.message);
        return this;
    }

    public static void main(String[] args) {
        Kamon.init();
        Logger logger = LoggerFactory.getLogger(ClusterApp.class);
        String envClusterName = System.getenv("PEKKO_CLUSTER_NAME");
        logger.info("Starting Pekko cluster application...with env cluster name: {}", envClusterName);
        
        ActorSystem<Command> system = ActorSystem.create(ClusterApp.create(), envClusterName);
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            system.log().info("Shutting down cluster node...");
            system.terminate();
        }));
        
        system.log().info("Pekko cluster node started with system: {}", system.name());
    }
}