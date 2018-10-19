package io.lbg.brave.kafka.streams.example;

import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import io.confluent.shaded.com.google.common.collect.ImmutableMap;
import io.lbg.kafka.streams.example.TheMessage;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;

import java.util.Properties;

@Slf4j
@Data
public class TheStream {

  final static String CONSUME_TOPIC = "itop";
  private final static String PRODUCE_TOPIC = "otop";

  private final Properties properties = new Properties();

  public static void main(String[] args) {
    new TheStream().run();
  }

  private void run() {

    SpecificAvroSerde<TheMessage> theSerde = new SpecificAvroSerde<>();
    theSerde.configure(ImmutableMap.of("schema.registry.url", "http://schema-registry:8081"), false);

    properties.put("application.id", "example");
    properties.put("state.dir", "/tmp/kafka-streams");
    properties.put("auto.offset.reset", "earliest");
    properties.put("bootstrap.servers", "kafka:9092");
    properties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    properties.put("value.serializer", "io.confluent.kafka.serializers.KafkaAvroSerializer");
    properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
    properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, SpecificAvroSerde.class);
    properties.put("schema.registry.url", "http://schema-registry:8081");

    StreamsBuilder streamsBuilder = new StreamsBuilder();


    KStream<String, TheMessage> consumedStream =
      streamsBuilder.stream(CONSUME_TOPIC, Consumed.with(Serdes.String(), theSerde));

    KTable<String, TheMessage> theTable =
      streamsBuilder.table(PRODUCE_TOPIC, Consumed.with(Serdes.String(), theSerde));

    KStream<String, TheMessage> joinedStream =
      consumedStream.leftJoin(theTable, (valueFromStream, valueFromTable) -> {
        if (valueFromTable == null) {
          return valueFromStream;
        }
        if (valueFromStream == null) { // Never true
          return valueFromTable;
        }
        return
          TheMessage.newBuilder()
            .setDate(valueFromStream.getDate() > valueFromTable.getDate() ? valueFromStream.getDate() : valueFromTable.getDate())
            .setId(valueFromStream.getId())
            .setName(valueFromTable.getName())
            .build();
      });

    KStream<String, TheMessage> mappedStream = joinedStream
      .map((key, value) -> {
        Long date = value.getDate();
        value.setDate(date == null ? 0 : date + 1);
        return new KeyValue<>(key, value);
      });

    mappedStream
      .peek((key, value) -> System.out.println(value.toString()))
      .to(PRODUCE_TOPIC);

    Topology topology = streamsBuilder.build();
    log.info("{}", topology.describe());

    KafkaStreams streams = new KafkaStreams(topology, properties);
    streams.start();
  }
}
