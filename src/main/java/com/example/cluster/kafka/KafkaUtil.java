package com.example.cluster.kafka;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.kafka.ConsumerSettings;
import org.apache.pekko.kafka.ProducerSettings;
import org.apache.pekko.kafka.Subscriptions;
import org.apache.pekko.kafka.javadsl.Consumer;
import org.apache.pekko.kafka.javadsl.Producer;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import com.typesafe.config.Config;
import java.util.concurrent.CompletionStage;

public class KafkaUtil {
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static ProducerSettings<String, byte[]> producerSettings(ActorSystem<?> system) {
        Config config = system.settings().config().getConfig("kafka");
        return ProducerSettings.create(system, new StringSerializer(), new ByteArraySerializer())
                .withBootstrapServers(config.getString("bootstrap-servers"));
    }

    public static ConsumerSettings<String, byte[]> consumerSettings(ActorSystem<?> system, String groupId) {
        Config config = system.settings().config().getConfig("kafka");
        return ConsumerSettings.create(system, new StringDeserializer(), new ByteArrayDeserializer())
                .withBootstrapServers(config.getString("bootstrap-servers"))
                .withGroupId(groupId)
                .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    }

    public static <T> ProducerRecord<String, byte[]> toProducerRecord(String topic, String key, T message) throws Exception {
        byte[] value = objectMapper.writeValueAsBytes(message);
        return new ProducerRecord<>(topic, key, value);
    }

    public static <T> T fromConsumerRecord(ConsumerRecord<String, byte[]> record, Class<T> clazz) throws Exception {
        return objectMapper.readValue(record.value(), clazz);
    }

    // producing a message
    public static CompletionStage<org.apache.pekko.Done> produce(
            ActorSystem<?> system, String topic, String key, Object message) {
        ProducerRecord<String, byte[]> record;
        try {
            record = toProducerRecord(topic, key, message);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return Source.single(record)
                .runWith(Producer.plainSink(producerSettings(system)), system);
    }

    // consuming messages
    public static <T> Source<T, Consumer.Control> consume(
            ActorSystem<?> system, String topic, String groupId, Class<T> clazz) {
        return Consumer.plainSource(
                consumerSettings(system, groupId),
                Subscriptions.topics(topic))
                .map(record -> {
                    try {
                        return fromConsumerRecord(record, clazz);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }
} 