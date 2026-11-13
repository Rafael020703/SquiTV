#!/bin/sh
set -eu
umask 077

admin=${1:-squitv-admin-temporario}
test -r /opt/keycloak/themes/squitv/login/theme.properties

workdir=$(mktemp -d)
trap 'rm -f "$workdir/session"; rmdir "$workdir"' EXIT
kcadm() {
    /opt/keycloak/bin/kcadm.sh "$@" --config "$workdir/session"
}

kcadm config credentials --server http://127.0.0.1:8080/auth --realm master --user "$admin"
kcadm update realms/squitv -s loginTheme=squitv
kcadm get realms/squitv --fields realm,loginTheme
