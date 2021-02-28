#!/bin/sh
#
# Copyright (c) 2020-2021.
#
# This file is part of drasyl.
#
#  drasyl is free software: you can redistribute it and/or modify
#  it under the terms of the GNU Lesser General Public License as published by
#  the Free Software Foundation, either version 3 of the License, or
#  (at your option) any later version.
#
#  drasyl is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#  GNU Lesser General Public License for more details.
#
#  You should have received a copy of the GNU Lesser General Public License
#  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
#

if [[ -z "$DRASYL_NETWORK_ID" ]]; then
  DRASYL_NETWORK_ID=$((RANDOM - 32768))
fi

if [[ -z "$PORT" ]]; then
  PORT=$((RANDOM + 32768))
fi

cat << EOF
version: '3.4'

networks:
  proxy:
    external:
      name: proxy_default

services:
  $CI_PROJECT_NAME:
    image: $APP_IMAGE
    restart: always
    container_name: "${CI_PROJECT_NAME}-${CI_PROJECT_ID}-${CI_ENVIRONMENT_SLUG}"
    hostname: ${APP_DEPLOY_HOST}
    command: node --verbose DEBUG
    environment:
      CONFIG_FORCE_drasyl_network_id: "$DRASYL_NETWORK_ID"
      CONFIG_FORCE_drasyl_identity_proof__of__work: $DRASYL_PROOF_OF_WORK
      CONFIG_FORCE_drasyl_identity_public__key: $DRASYL_PUBLIC_KEY
      CONFIG_FORCE_drasyl_identity_private__key: $DRASYL_PRIVATE_KEY
      CONFIG_FORCE_drasyl_remote_endpoints_0: udp://${APP_DEPLOY_HOST}:${PORT}?publicKey=${DRASYL_PUBLIC_KEY}&networkId=${DRASYL_NETWORK_ID}
      CONFIG_FORCE_drasyl_remote_expose_enabled: "false"
      CONFIG_FORCE_drasyl_remote_super__peer_enabled: "false"
      CONFIG_FORCE_drasyl_monitoring_enabled: "true"
      CONFIG_FORCE_drasyl_monitoring_influx_uri: https://influxdb.incorum.org
      CONFIG_FORCE_drasyl_monitoring_influx_user: drasyl
      CONFIG_FORCE_drasyl_monitoring_influx_password: $DRASYL_MONITORING_PASSWORD
      CONFIG_FORCE_drasyl_monitoring_influx_reporting__frequency: 10s
      JAVA_OPTS: ${JAVA_OPTS}
    ports:
      - "${PORT}:22527/udp"
    networks:
      - default
      - proxy
EOF
