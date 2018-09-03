

DEBUG=false PUBLISH=false \
  FROM_QUEUE_INDEX=1 \
  TO_QUEUE_INDEX=10 \
  BASIC_GET_RATE_PER_QUEUE_PER_SECOND=10 \
  ruby synchronous_consumer.rb
