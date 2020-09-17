
#export SPRING_PROFILES_ACTIVE=Cloud,datadog

java  -jar target/durable-consumer-0.0.1-SNAPSHOT.jar $@
