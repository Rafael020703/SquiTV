#!/bin/sh
set -eu
umask 077

admin=${1:-squitv-admin-temporario}
staged=/usr/local/lib/squitv-account/squitv-iptv.native.service
service=/etc/systemd/system/squitv-iptv.service
workdir=$(mktemp -d)
trap 'rm -f "$workdir/session" "$workdir/secret" "$workdir/service"; rmdir "$workdir"' EXIT

kcadm() {
    /opt/keycloak/bin/kcadm.sh "$@" --config "$workdir/session"
}

test -r "$staged"
kcadm config credentials --server http://127.0.0.1:8080/auth --realm master --user "$admin"
client=$(kcadm get clients -r squitv -q clientId=squitv-native-login --fields id,clientId |
    python3 -c 'import json,sys; print(next((x["id"] for x in json.load(sys.stdin) if x["clientId"] == "squitv-native-login"), ""))')
if [ -z "$client" ]; then
    client=$(kcadm create clients -r squitv -i -s clientId=squitv-native-login \
        -s protocol=openid-connect -s enabled=true -s publicClient=false \
        -s directAccessGrantsEnabled=true -s standardFlowEnabled=false \
        -s implicitFlowEnabled=false -s serviceAccountsEnabled=false)
fi
kcadm update "clients/$client" -r squitv -s enabled=true -s publicClient=false \
    -s directAccessGrantsEnabled=true -s standardFlowEnabled=false \
    -s implicitFlowEnabled=false -s serviceAccountsEnabled=false
kcadm get "clients/$client/client-secret" -r squitv |
    python3 -c 'import json,sys; value=json.load(sys.stdin)["value"]; assert isinstance(value,str) and len(value)>=16; print(value)' > "$workdir/secret"
test -s "$workdir/secret"
install -m 0600 "$workdir/secret" /etc/squitv-account/native-client-secret
cp -p "$service" "$workdir/service"
install -m 0644 "$staged" "$service"
systemctl daemon-reload
if ! systemctl restart squitv-iptv.service; then
    install -m 0644 "$workdir/service" "$service"
    systemctl daemon-reload
    systemctl restart squitv-iptv.service
    exit 1
fi
printf 'Cliente de login do app configurado.\n'