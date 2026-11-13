#!/bin/sh
set -eu

KC_DB_PASSWORD=$(cat "${CREDENTIALS_DIRECTORY:?}/keycloak-db-password")
export KC_DB_PASSWORD
exec /opt/keycloak/bin/kc.sh start --optimized --import-realm
