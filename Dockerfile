FROM adoptopenjdk/openjdk11:alpine-slim

ENV OBA_ROOT="/opt/onebusaway"
ENV OBA_VERSION=1.3.97-SNAPSHOT

WORKDIR ${OBA_ROOT}

ADD onebusaway-gtfs-transformer-cli/target/onebusaway-gtfs-transformer-cli.jar ./onebusaway-gtfs-transformer-cli.jar

ADD onebusaway-gtfs-merge-cli/target/onebusaway-gtfs-merge-cli-${OBA_VERSION}.jar ./onebusaway-gtfs-merge-cli.jar


