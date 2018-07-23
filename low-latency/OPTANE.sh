bin/runjava com.rabbitmq.perf.PerfTest \
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
--consumers 1 \
--producers 1 \
--rate $RATE \
--time 330

echo "99th percentile:"
curl -su "admin:$PROM_PASS" -k \
  "https://prometheus.rabbitmq.pivotal.io/api/v1/query?query=max_over_time(perftest_latency_seconds%7Bquantile%3D%220.99%22%7D%5B5m%5D)" |
  jq -r '.data.result[].value[1]' | xargs printf "%.3f\\n"

echo "95th percentile:"
curl -su "admin:$PROM_PASS" -k \
  "https://prometheus.rabbitmq.pivotal.io/api/v1/query?query=max_over_time(perftest_latency_seconds%7Bquantile%3D%220.95%22%7D%5B5m%5D)" |
  jq -r '.data.result[].value[1]' | xargs printf "%.3f\\n"

echo "75th percentile:"
curl -su "admin:$PROM_PASS" -k \
  "https://prometheus.rabbitmq.pivotal.io/api/v1/query?query=max_over_time(perftest_latency_seconds%7Bquantile%3D%220.75%22%7D%5B5m%5D)" |
  jq -r '.data.result[].value[1]' | xargs printf "%.3f\\n"
