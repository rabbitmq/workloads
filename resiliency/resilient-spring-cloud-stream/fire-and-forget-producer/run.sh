#!/usr/bin/env bash

SCRIPT="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

java  -jar $SCRIPT/target/fire-and-forget-producer-0.0.1-SNAPSHOT.jar $@
