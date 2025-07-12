package com.example.cluster.http;

import static org.apache.pekko.http.javadsl.server.Directives.*;

import com.example.cluster.commands.*;
import com.example.cluster.actors.pubsub.ResponseSubscriberActor;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.marshallers.jackson.Jackson;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.actor.typed.Props;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.AskPattern;

import java.time.Duration;

import org.apache.pekko.cluster.pubsub.DistributedPubSub;

import com.example.cluster.actors.CaseCompanionActor;
import com.example.cluster.commands.CaseResolvedMessage;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.typesafe.config.Config;

import java.util.concurrent.CompletionStage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


/**
 * HTTP server - http logic that endpoints
 */
public class HttpServer {
    
    private final ActorSystem<?> system;
    private final ClusterSharding sharding;
    private final Config config;
    private final org.apache.pekko.actor.ActorRef mediator;

    public HttpServer(ActorSystem<?> system, ClusterSharding sharding) {
        this.system = system;
        this.sharding = sharding;
        this.config = system.settings().config();
        // Initialize Distributed PubSub mediator
        this.mediator = DistributedPubSub.get(system).mediator();
    }

    public CompletionStage<ServerBinding> start(String host, int port) {
        system.log().info("Starting HTTP server on {}:{}", host, port);
        return Http.get(system).newServerAt(host, port).bind(createRoutes());
    }

    private Route createRoutes() {
        List<Route> routes = new ArrayList<>();
        
        system.log().info("Loading routes from configuration...");
        
        //Load routes from config file
        if (config.hasPath("http-routes.endpoints")) {
            Config endpointsConfig = config.getConfig("http-routes.endpoints");
            
            for (String routeName : endpointsConfig.root().keySet()) {
                try {
                    Config routeConfig = endpointsConfig.getConfig(routeName);
                    Route route = createRouteFromConfig(routeName, routeConfig);
                    if (route != null) {
                        routes.add(route);
                        system.log().info("Loaded route: {} -> {} {} (type: '{}')", 
                            routeName, 
                            routeConfig.getString("method"), 
                            routeConfig.getString("path"),
                            routeConfig.getString("type"));
                    }
                } catch (Exception e) {
                    system.log().error("Failed to load route {}: {}", routeName, e.getMessage());
                }
            }
        }
        
        // No fallback routes - everything must be configured
        if (routes.isEmpty()) {
            system.log().error("No routes configured! Check your http-routes.endpoints configuration");
            return complete(StatusCodes.INTERNAL_SERVER_ERROR, "No routes configured");
        }
        
        system.log().info("Total routes loaded: {}", routes.size());
        return buildRouteTree(routes);
    }
    
    private Route buildRouteTree(List<Route> routes) {
        Route result = routes.get(0);
        for (int i = 1; i < routes.size(); i++) {
            result = concat(result, routes.get(i));
        }
        return result;
    }
    
    private Route createRouteFromConfig(String routeName, Config routeConfig) {
        String path = routeConfig.getString("path");
        String method = routeConfig.getString("method");
        String type = routeConfig.getString("type");
        
        system.log().info("Creating route '{}': {} {} (type: '{}')", routeName, method, path, type);
        
        switch (type) {
            case "simple-response":
                return createSimpleResponseRoute(path, method, routeConfig);
            case "json-entity-handler":
                return createJsonEntityRoute(path, method, routeConfig);
            default:
                system.log().warn("Unknown route type: '{}' for route '{}'", type, routeName);
                return null;
        }
    }
    
    private Route createSimpleResponseRoute(String path, String method, Config routeConfig) {
        String response = routeConfig.getString("response");
        return createMethodRoute(method, path, () -> complete(response));
    }
    
    private Route createJsonEntityRoute(String path, String method, Config routeConfig) {
        if ("/api/v1/cases/resolve".equals(path) && "POST".equalsIgnoreCase(method)) {
            return post(() ->
                pathPrefix("api", () ->
                    pathPrefix("v1", () ->
                        pathPrefix("cases", () ->
                            path("resolve", () ->
                                entity(Jackson.unmarshaller(ResolveCaseRequest.class), request -> {
                                    system.log().info("Received resolve case request for caseId: {} using Distributed PubSub", request.caseId);
                                    
                                    // Create a unique response topic for this request
                                    String responseTopicId = "case-response-" + UUID.randomUUID();
                                    
                                    // Create a temporary subscriber actor for this request
                                    ActorRef<Command> subscriber = 
                                        system.systemActorOf(
                                            ResponseSubscriberActor.create(responseTopicId, mediator),
                                            "response-subscriber-" + UUID.randomUUID(),
                                            Props.empty()
                                        );
                                    
                                    // Send command to sharded entity with response topic
                                    EntityRef<Command> caseRef = sharding.entityRefFor(
                                        CaseCompanionActor.ENTITY_TYPE_KEY, request.caseId
                                    );
                                    caseRef.tell(new ResolveCaseCommand(request.caseId, responseTopicId));
                                    
                                    // Use Ask pattern with the subscriber
                                    CompletionStage<CaseResolvedMessage> future = AskPattern.ask(
                                        subscriber,
                                        WaitForResponseCommand::new,
                                        Duration.ofSeconds(35), // Generous timeout
                                        system.scheduler()
                                    );
                                    
                                    return completeOKWithFuture(future, Jackson.marshaller());
                                })
                            )
                        )
                    )
                )
            );
        }
        
        // Handle other JSON entity routes if needed
        system.log().warn("Unsupported JSON entity route: {} {}", method, path);
        return null;
    }
    
    
    // Helper method to create routes for different HTTP methods and paths
    private Route createMethodRoute(String method, String path, java.util.function.Supplier<Route> routeSupplier) {
        Route baseRoute = routeSupplier.get();
        
        // Handle path routing
        final Route pathRoute = buildPathRoute(path, baseRoute);
        
        // Handle HTTP method
        switch (method.toUpperCase()) {
            case "GET":
                return get(() -> pathRoute);
            case "POST":
                return post(() -> pathRoute);
            case "PUT":
                return put(() -> pathRoute);
            case "DELETE":
                return delete(() -> pathRoute);
            default:
                system.log().warn("Unsupported HTTP method: {}", method);
                return null;
        }
    }
    
    // Extract path building to separate method to avoid "effectively final" issues
    private Route buildPathRoute(String path, Route baseRoute) {
        if ("/".equals(path)) {
            return pathSingleSlash(() -> baseRoute);
        }
        
        // Parse path segments for nested paths like /api/v1/cases/resolve
        String[] segments = path.split("/");
        Route currentRoute = baseRoute;
        
        // Build nested path from right to left (skip empty first element from leading /)
        for (int i = segments.length - 1; i >= 1; i--) {
            if (!segments[i].isEmpty()) {
                final Route innerRoute = currentRoute;
                final String segment = segments[i];
                currentRoute = path(segment, () -> innerRoute);
            }
        }
        
        return currentRoute;
    }
    
    // Request DTO
    public static class ResolveCaseRequest {
        @JsonProperty("caseId")
        public String caseId;
        
        public ResolveCaseRequest() {}
        
        public ResolveCaseRequest(String caseId) {
            this.caseId = caseId;
        }
    }
}