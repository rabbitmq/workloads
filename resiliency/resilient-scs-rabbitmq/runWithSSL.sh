export SPRING_PROFILES_ACTIVE=Cloud
export VCAP_APPLICATION='{"application_name":demo}'
export VCAP_SERVICES=$(cat src/main/resources/singleNodeWithSSL.json)

# Use these JAva_ARGS to use key-store.p12 which does not have the server's cA certificate
# JAVA_ARGS=" -Djavax.net.ssl.trustStoreType=PKCS12 -Djavax.net.ssl.trustStore=/tmp/docker-test/key-store.p12  -Djavax.net.ssl.trustStorePassword=roboconf"

# Use these Java_ARgs to use trust-store.p12 which does have the server's CA certificate
JAVA_ARGS=" -Djavax.net.ssl.trustStoreType=PKCS12 -Djavax.net.ssl.trustStore=/tmp/docker-test/trust-store.p12  -Djavax.net.ssl.trustStorePassword=roboconf"

echo "Requires RabbitMQ server running AMQPS on port 12000."
echo "It expects server's CA PKCS12 certificate under /tmp/docker-test/key-store.p12 with a password roboconf"
echo "Check out https://github.com/roboconf/rabbitmq-with-ssl-in-docker to bootstrap RabbitMQ server with SSL"
echo ""

java $JAVA_ARGS -jar target/resilient-scs-rabbitmq-0.0.1-SNAPSHOT.jar
