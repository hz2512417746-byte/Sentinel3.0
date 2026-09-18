package com.antifraud.flink;

import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.streaming.api.functions.source.RichSourceFunction;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;

/** Flink 实时流处理：消费 dpi_logs → ZengKuai 增量特征计算 → 输出 dpi_enriched */
public class DpiFraudDetectionJob {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();
        env.setParallelism(1);
        DataStream<String> src = env.addSource(new DpiKafkaSource());
        DataStream<String> enriched = src.map(new ZengKuaiEnrichFunction());
        enriched.addSink(new DpiKafkaSink());
        env.execute("Flink+ZengKuai Pipeline");
    }
}

/** 增块特征富化算子：每条日志调用 ZengKuaiEngine 计算增量特征并注入 JSON */
class ZengKuaiEnrichFunction extends RichMapFunction<String, String> {
    private transient ZengKuaiEngine engine;
    private transient ObjectMapper mapper;

    @Override
    public void open(Configuration cfg) {
        engine = new ZengKuaiEngine();
        mapper = new ObjectMapper();
    }

    @Override
    @SuppressWarnings("unchecked")
    public String map(String value) throws Exception {
        Map<String, Object> entry = mapper.readValue(value, Map.class);
        long t0 = System.nanoTime();
        engine.process(entry);
        Map<String, Double> features = engine.getFeatures(entry);
        long t1 = System.nanoTime();
        entry.put("features", features);
        entry.put("zengkuai_ms", Math.round((t1 - t0) / 1e6 * 1000) / 1000.0);
        return mapper.writeValueAsString(entry);
    }
}

/** Kafka 数据源：从 dpi_logs 消费原始日志 */
class DpiKafkaSource extends RichSourceFunction<String> {
    private volatile boolean running = true;
    private KafkaConsumer<String, String> consumer;

    @Override
    public void open(Configuration cfg) {
        Properties p = new Properties();
        p.put("bootstrap.servers", "localhost:9092");
        p.put("group.id", "flink-zengkuai");
        p.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        p.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        p.put("auto.offset.reset", "latest");
        consumer = new KafkaConsumer<>(p);
        consumer.subscribe(List.of("dpi_logs"));
    }

    @Override
    public void run(SourceContext<String> ctx) throws Exception {
        while (running) {
            for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofMillis(500))) {
                ctx.collect(r.value());
            }
        }
    }

    @Override
    public void cancel() { running = false; if (consumer != null) consumer.close(); }
}

/** Kafka 数据汇：将带 features 的富化日志写入 dpi_enriched */
class DpiKafkaSink extends RichSinkFunction<String> {
    private KafkaProducer<String, String> producer;

    @Override
    public void open(Configuration cfg) {
        Properties p = new Properties();
        p.put("bootstrap.servers", "localhost:9092");
        p.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        p.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producer = new KafkaProducer<>(p);
    }

    @Override
    public void invoke(String value, Context ctx) {
        producer.send(new ProducerRecord<>("dpi_enriched", value));
    }

    @Override
    public void close() { if (producer != null) producer.close(); }
}
