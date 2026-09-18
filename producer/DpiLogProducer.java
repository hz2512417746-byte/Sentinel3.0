package com.antifraud.producer;

import org.apache.kafka.clients.producer.*;
import java.util.Properties;
import java.util.concurrent.Future;

/**
 * DPI日志 Kafka 生产者
 * 与LogSimulator配合，将生成好的日志推送至Kafka
 */
public class DpiLogProducer implements AutoCloseable {
    private final KafkaProducer<String, String> producer;
    private final String topic;

    public DpiLogProducer(String bootstrapServers, String topic) {
        this.topic = topic;
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        // 优化吞吐量
        props.put("batch.size", 16384);           // 16KB 批量
        props.put("linger.ms", 5);               // 5ms 等待批处理
        props.put("compression.type", "snappy"); // Snappy 压缩
        props.put("acks", "1");                  // Leader确认即可
        props.put("max.in.flight.requests.per.connection", 5);
        this.producer = new KafkaProducer<>(props);
    }

    /**
     * 发送单条日志（异步）
     */
    public Future<RecordMetadata> send(String key, String jsonLog) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, jsonLog);
        return producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                System.err.println("[Producer] 发送失败: " + exception.getMessage());
            }
        });
    }

    /**
     * 同步发送并确认
     */
    public RecordMetadata sendSync(String key, String jsonLog) throws Exception {
        return producer.send(new ProducerRecord<>(topic, key, jsonLog)).get();
    }

    public void flush() {
        producer.flush();
    }

    @Override
    public void close() {
        producer.close();
    }
}
