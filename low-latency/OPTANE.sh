#!/usr/bin/env bash

PERFTEST_PATH=/root/rabbitmq-perf-test-2.2.0.M1
RABBITMQ_PASS=PASS
RABBITMQ_HOST=10.99.244.3
PROM_PASS=PASS
TEST_DURATION="${TEST_DURATION:-180}"
METRICS_OFFSET=30
METRICS_WINDOW=$((TEST_DURATION - METRICS_OFFSET*2))

TEST_RATES="100 1000 10000 20000 30000 50000 70000"
TEST_CONSUMERS="1 2 5 10 100 500"
TEST_PRODUCERS="1 2 5 10 100 500"

set_defaults() {
  RATE=10000
  CONSUMERS=1
  PRODUCERS=1
}

perf_test() {

sleep 1

# Maybe echo the arguments to this test run.

$PERFTEST_PATH/bin/runjava com.rabbitmq.perf.PerfTest \
--autoack \
--interval 15 \
--size 1000 \
--queue-args 'x-max-length=10000' \
--routing-key perf-test \
--uri "amqp://admin:$RABBITMQ_PASS@$RABBITMQ_HOST:5672/%2F" \
--metrics-prometheus \
--metrics-tags 'type=publisher,type=consumer,deployment=low-latency' \
--queue-pattern 'perf-test-%d' \
--queue-pattern-from 0 \
--queue-pattern-to 0 \
--consumers $CONSUMERS \
--producers $PRODUCERS \
--rate $RATE \
--time $TEST_DURATION 1>/dev/null

_99th=$(curl -su "admin:$PROM_PASS" -k \
  "https://prometheus.rabbitmq.pivotal.io/api/v1/query?query=max_over_time(perftest_latency_seconds%7Bquantile%3D%220.99%22%7D%5B${METRICS_WINDOW}s%5D%20offset%20${METRICS_OFFSET}s)%20*%201000" |
  jq -r '.data.result[].value[1]' |
  xargs printf "%.2f\\n")

_95th=$(curl -su "admin:$PROM_PASS" -k \
  "https://prometheus.rabbitmq.pivotal.io/api/v1/query?query=max_over_time(perftest_latency_seconds%7Bquantile%3D%220.95%22%7D%5B${METRICS_WINDOW}s%5D%20offset%20${METRICS_OFFSET}s)%20*%201000" |
  jq -r '.data.result[].value[1]' |
  xargs printf "%.2f\\n")

_75th=$(curl -su "admin:$PROM_PASS" -k \
  "https://prometheus.rabbitmq.pivotal.io/api/v1/query?query=max_over_time(perftest_latency_seconds%7Bquantile%3D%220.75%22%7D%5B${METRICS_WINDOW}s%5D%20offset%20${METRICS_OFFSET}s)%20*%201000" |
  jq -r '.data.result[].value[1]' |
  xargs printf "%.2f\\n")

  printf "| %s ms | %s ms | %s ms |\\n" $_99th $_95th $_75th
}

# Publish rates.

set_defaults

printf "%s\n" "" \
        "## How does publish rate affect message latency?" \
        "" \
        "| Publish Rate | Max 99th | Max 95th | Max 75th |" \
        "| -:           | -:       | -:       | -:       |"

for RATE in $TEST_RATES; do
  printf "| %s msg/s " $RATE;
  perf_test;
done

exit 0

## How does credit flow affect message latency (Erlang/OTP 21)?
# TODO we need to restart the node for this

## How does credit flow affect message latency (Erlang/OTP 20)?
# TODO same

# Consumers.

set_defaults

printf "%s\n" "" \
        "## How do multiple consumers affect message latency?" \
        "" \
        "Publish rate: $RATE msg/s" \
        "| Consumers | Max 99th | Max 95th | Max 75th |" \
        "| -:        | -:       | -:       | -:       |"

for CONSUMERS in $TEST_CONSUMERS; do
  printf "| %s " $CONSUMERS;
  perf_test;
done

# Producers.

set_defaults

printf "%s\n" "" \
        "## How do multiple producers affect message latency?" \
        "" \
        "Publish rate: $RATE msg/s" \
        "| Producers | Max 99th | Max 95th | Max 75th |" \
        "| -:        | -:       | -:       | -:       |"

for PRODUCERS in $TEST_PRODUCERS; do
  printf "| %s " $PRODUCERS;
  perf_test;
done

## How do multiple queues affect message latency?
