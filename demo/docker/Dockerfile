ARG BASE_IMAGE=openjdk:11-jre
FROM $BASE_IMAGE
ARG PORT=8080
EXPOSE $PORT
WORKDIR /app
COPY entrypoint.sh /app/entrypoint.sh
COPY .tmp/START_CLASS /app/START_CLASS
COPY .tmp/BOOT-INF/lib /app/lib
COPY .tmp/META-INF /app/META-INF
COPY .tmp/BOOT-INF/classes /app
ENTRYPOINT ["/app/entrypoint.sh"]
HEALTHCHECK --start-period=10s --interval=10s --timeout=3s --retries=5 \
            CMD curl --silent --fail --request GET http://localhost:$PORT/actuator/health \
                | jq --exit-status '.status == "UP"' || exit 1