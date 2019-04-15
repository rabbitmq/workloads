export SPRING_PROFILES_ACTIVE=Cloud,datadog
export VCAP_APPLICATION='{"instance_id": "0001", "name": "demo", "space_id": "flamingo"}'
export VCAP_SERVICES=$(cat src/main/resources/singleNode.json)

java $JAVA_ARGS -jar target/resilient-skeleton-spring-rabbitmq-0.0.1-SNAPSHOT.jar  $@
