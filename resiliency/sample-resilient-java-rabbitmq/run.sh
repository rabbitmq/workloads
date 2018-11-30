export SPRING_PROFILES_ACTIVE=Cloud
export VCAP_APPLICATION='{"application_name":demo}'
export VCAP_SERVICES=$(cat src/main/resources/singleNode.json)

java $JAVA_ARGS -jar target/resilient-spring-rabbitmq-0.0.1-SNAPSHOT.jar
