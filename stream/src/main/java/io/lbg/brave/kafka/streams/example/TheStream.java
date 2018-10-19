package io.lbg.brave.kafka.streams.example;

import brave.Tracing;
import brave.kafka.streams.KafkaStreamsTracing;
import brave.okhttp3.TracingCallFactory;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import io.confluent.shaded.com.google.common.collect.ImmutableMap;
import io.lbg.kafka.streams.example.TheMessage;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.processor.ProcessorContext;
import zipkin2.Span;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.urlconnection.URLConnectionSender;

import java.io.IOException;
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

    URLConnectionSender urlConnectionSender = URLConnectionSender
      .newBuilder()
      .endpoint("http://zipkin:9411/api/v2/spans")
      .build();

    AsyncReporter<Span> asyncReporter = AsyncReporter
      .builder(urlConnectionSender)
      .build();

    Tracing tracing = Tracing
      .newBuilder()
      .localServiceName("TheStream")
      .spanReporter(asyncReporter)
      .build();

    KafkaStreamsTracing kafkaStreamsTracing = KafkaStreamsTracing.create(tracing);

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

    OkHttpClient okHttpClient = new OkHttpClient.Builder().build();
    Call.Factory factory = TracingCallFactory.create(tracing, okHttpClient);

    StreamsBuilder streamsBuilder = new StreamsBuilder();

    TransformerSupplier<String, TheMessage, KeyValue<String, TheMessage>> transformerSupplier =
      kafkaStreamsTracing.transformer("T", new Transformer<String, TheMessage, KeyValue<String, TheMessage>>() {
        @Override
        public void init(ProcessorContext context) {
        }

        @Override
        public KeyValue<String, TheMessage> transform(String key, TheMessage value) {
          Request request = new Request.Builder()
            .url("http://web:5050/aa/bb/" + key)
            .build();
          try {
            factory.newCall(request).execute();
          } catch (IOException e) {
            e.printStackTrace();
          }
          return new KeyValue<>(key, value);
        }

        @Override
        public void close() {

        }
      });

    KStream<String, TheMessage> consumedStream =
      streamsBuilder.stream(CONSUME_TOPIC, Consumed.with(Serdes.String(), theSerde));

    KStream<String, TheMessage> transformedStream = consumedStream.transform(transformerSupplier);

    KTable<String, TheMessage> theTable =
      streamsBuilder.table(PRODUCE_TOPIC, Consumed.with(Serdes.String(), theSerde));

    KStream<String, TheMessage> joinedStream =
      transformedStream.leftJoin(theTable, (valueFromStream, valueFromTable) -> {
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


    KafkaStreams streams = kafkaStreamsTracing.kafkaStreams(topology, properties);
    streams.start();
  }
}
