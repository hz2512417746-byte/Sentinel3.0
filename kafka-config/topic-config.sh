#!/bin/bash
# Kafka Topic 配置 - 创建DPI反诈日志Topic

KAFKA_HOME="${KAFKA_HOME:-/opt/kafka}"
BOOTSTRAP="localhost:9092"

# 创建主Topic: DPI日志流
$KAFKA_HOME/bin/kafka-topics.sh --create \
  --bootstrap-server $BOOTSTRAP \
  --topic dpi_logs \
  --partitions 4 \
  --replication-factor 1 \
  --config retention.ms=604800000 \
  --config segment.bytes=1073741824 \
  --config max.message.bytes=1048576

# 创建告警Topic
$KAFKA_HOME/bin/kafka-topics.sh --create \
  --bootstrap-server $BOOTSTRAP \
  --topic dpi_alerts \
  --partitions 2 \
  --replication-factor 1

# 创建规则更新Topic
$KAFKA_HOME/bin/kafka-topics.sh --create \
  --bootstrap-server $BOOTSTRAP \
  --topic rule_updates \
  --partitions 1 \
  --replication-factor 1

echo "Topics created."
$KAFKA_HOME/bin/kafka-topics.sh --list --bootstrap-server $BOOTSTRAP
