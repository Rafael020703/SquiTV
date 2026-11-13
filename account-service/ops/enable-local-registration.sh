#!/bin/sh
set -eu
umask 077

if [ "$#" -ne 1 ]; then
    printf 'Uso: %s USUARIO_ADMINISTRATIVO\n' "$0" >&2
    exit 2
fi

workdir=$(mktemp -d)
trap 'rm -f "$workdir/session" "$workdir/profile.json"; rmdir "$workdir"' EXIT
kcadm() {
    /opt/keycloak/bin/kcadm.sh "$@" --config "$workdir/session"
}

# Aguarda a inicialização após a criação do primeiro administrador.
curl --fail --silent --show-error --max-time 4 --retry 20 --retry-connrefused \
    --retry-delay 1 --retry-max-time 30 --output /dev/null \
    http://127.0.0.1:8080/auth/realms/master/.well-known/openid-configuration

# A senha administrativa é pedida pelo Keycloak no terminal.
kcadm config credentials --server http://127.0.0.1:8080/auth --realm master --user "$1"

kcadm get users/profile -r squitv > "$workdir/profile.json"
python3 - "$workdir/profile.json" <<'PY'
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
profile = json.loads(path.read_text(encoding="utf-8"))
attributes = {attribute["name"]: attribute for attribute in profile["attributes"]}
for name in ("email", "firstName", "lastName"):
    attribute = attributes[name]
    attribute.pop("required", None)
    for permission in ("view", "edit"):
        roles = attribute["permissions"][permission]
        attribute["permissions"][permission] = [role for role in roles if role != "user"]
path.write_text(json.dumps(profile), encoding="utf-8")
PY

kcadm update users/profile -r squitv -n -f "$workdir/profile.json"
kcadm update realms/squitv \
    -s registrationEmailAsUsername=false \
    -s loginWithEmailAllowed=false \
    -s verifyEmail=false \
    -s resetPasswordAllowed=false \
    -s registrationAllowed=true
kcadm get realms/squitv --fields realm,registrationAllowed,registrationEmailAsUsername,loginWithEmailAllowed,verifyEmail,resetPasswordAllowed
