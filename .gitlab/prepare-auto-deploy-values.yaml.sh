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
podArgs:
  - node
  - --verbose
  - TRACE

podEnv:
  - name: CONFIG_FORCE_drasyl_network_id
    value: "${DRASYL_NETWORK_ID}"
  - name: CONFIG_FORCE_drasyl_identity_proof__of__work
    value: "${DRASYL_PROOF_OF_WORK:-0}"
  - name: CONFIG_FORCE_drasyl_identity_public__key
    value: "$DRASYL_PUBLIC_KEY"
  - name: CONFIG_FORCE_drasyl_identity_private__key
    value: "$DRASYL_PRIVATE_KEY"
  - name: CONFIG_FORCE_drasyl_remote_bind__port
    value: "22527"
  - name: CONFIG_FORCE_drasyl_remote_expose_enabled
    value: "false"
  - name: CONFIG_FORCE_drasyl_remote_super__peer_enabled
    value: "false"
  - name: CONFIG_FORCE_drasyl_monitoring_enabled
    value: "true"
  - name: CONFIG_FORCE_drasyl_monitoring_host__tag
    value: "$(echo $CI_ENVIRONMENT_URL | awk -F[/:] '{print $4}')"
  - name: CONFIG_FORCE_drasyl_monitoring_influx_uri
    value: https://influxdb.incorum.org
  - name: CONFIG_FORCE_drasyl_monitoring_influx_user
    value: drasyl
  - name: CONFIG_FORCE_drasyl_monitoring_influx_password
    value: "$DRASYL_MONITORING_PASSWORD"
  - name: CONFIG_FORCE_drasyl_monitoring_influx_reporting__frequency
    value: 10s
  - name: JAVA_OPTS
    value: "$JAVA_OPTS"

port: $PORT
EOF
