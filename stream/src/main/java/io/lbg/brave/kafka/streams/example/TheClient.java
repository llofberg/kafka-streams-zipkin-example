package io.lbg.brave.kafka.streams.example;

import io.lbg.kafka.streams.example.TheMessage;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

import static io.lbg.brave.kafka.streams.example.TheStream.CONSUME_TOPIC;

public class TheClient {
  public static void main(String[] args) {
    new TheClient().run();
  }

  private void run() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("application.id", "example");
    properties.put("state.dir", "/tmp/kafka-streams");
    properties.put("auto.offset.reset", "earliest");
    properties.put("bootstrap.servers", "kafka:9092");
    properties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    properties.put("value.serializer", "io.confluent.kafka.serializers.KafkaAvroSerializer");
    properties.put("schema.registry.url", "http://schema-registry:8081");

    KafkaProducer<String, TheMessage> kafkaProducer = new KafkaProducer<>(properties);

    IntStream.range(0, 10000)
      .sequential()
      .peek(v -> {
        try {
          Thread.sleep(1000L);
        } catch (InterruptedException ignore) {

        }
      })
      .forEach(value -> kafkaProducer
        .send(new ProducerRecord<>(CONSUME_TOPIC, String.valueOf(value),
          TheMessage.newBuilder()
            .setId(value)
            .setName("Message: " + value)
            .setDate(new Date().getTime())
            .build())));
  }
}
