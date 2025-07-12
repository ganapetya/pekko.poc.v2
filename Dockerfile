FROM openjdk:11-jre-slim

RUN apt-get update && \
    apt-get install -y curl telnet && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the application jar
COPY target/pekko-cluster-poc-1.0-SNAPSHOT.jar app.jar

# Expose ports
EXPOSE 8080 8558 25520

# Run the application
ENTRYPOINT ["sh", "-c", "java -Dpekko.cluster.name=$PEKKO_CLUSTER_NAME -Dkafka.bootstrap-servers=$KAFKA_BOOTSTRAP_SERVERS -jar app.jar"] 