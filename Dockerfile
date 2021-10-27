#
# Copyright (C) 2021 Holger Bruch <holger.bruch@systect.de>
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

FROM adoptopenjdk/openjdk11:alpine-slim

ENV OBA_ROOT="/opt/onebusaway"
ENV OBA_VERSION=1.3.103-SNAPSHOT

WORKDIR ${OBA_ROOT}

ADD onebusaway-gtfs-transformer-cli/target/onebusaway-gtfs-transformer-cli.jar ./onebusaway-gtfs-transformer-cli.jar

ADD onebusaway-gtfs-merge-cli/target/onebusaway-gtfs-merge-cli-${OBA_VERSION}.jar ./onebusaway-gtfs-merge-cli.jar


