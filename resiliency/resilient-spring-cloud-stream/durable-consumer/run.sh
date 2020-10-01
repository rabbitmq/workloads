#!/usr/bin/env bash

SCRIPT="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

java  -jar $SCRIPT/target/durable-consumer-0.0.1-SNAPSHOT.jar $@
