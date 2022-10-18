#!/bin/sh
# check SPRING_PROFILES_ACTIVE environment variable
[ -z "$SPRING_PROFILES_ACTIVE" ] && echo "Error: Define SPRING_PROFILES_ACTIVE environment variable" && exit 1;
# setup main class
export START_CLASS=`cat /app/START_CLASS`
# startup
java -XX:+UseContainerSupport \
-XX:InitialRAMPercentage=50 \
-XX:MaxRAMPercentage=85 \
-XX:+UnlockExperimentalVMOptions \
-Djava.security.egd=file:/dev/./urandom \
-Dlog4j2.formatMsgNoLookups=true \
--add-opens=java.base/jdk.internal.loader=ALL-UNNAMED \
--add-opens=java.base/java.net=ALL-UNNAMED \
-cp /app:/app/lib/* $START_CLASS
