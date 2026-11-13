#!/bin/sh
set -eu

# Executar no console do CT 106. O Keycloak pede a senha temporária no terminal.
unit=squitv-keycloak.service
if ! systemctl is-active --quiet "$unit"; then
    printf 'O serviço deve estar ativo antes de iniciar.\n' >&2
    exit 1
fi

if [ ! -t 0 ]; then
    printf 'Abra o console interativo do CT 106 para digitar a senha.\n' >&2
    exit 1
fi

systemctl stop "$unit"
trap 'systemctl start "$unit"' EXIT
python3 - <<'PY'
import os
import shlex
import subprocess
from pathlib import Path

raw = subprocess.check_output(
    ["systemctl", "show", "--property=Environment", "--value", "squitv-keycloak.service"],
    text=True,
)
options = dict(item.split("=", 1) for item in shlex.split(raw))
env = {key: value for key, value in options.items() if key.startswith("KC_")}
env["JAVA_OPTS_APPEND"] = options["JAVA_OPTS_APPEND"]
env["KC_DB_PASSWORD"] = Path("/etc/squitv-account/keycloak-db-password").read_text().rstrip("\n")
env["PATH"] = os.defpath
with open("/dev/tty", "rb") as terminal:
    subprocess.run(
        ["/sbin/runuser", "--preserve-environment", "-u", "keycloak", "--",
         "/opt/keycloak/bin/kc.sh", "bootstrap-admin", "user", "--optimized",
         "--username", "squitv-admin-temporario"],
        cwd="/opt/keycloak",
        env=env,
        stdin=terminal,
        check=True,
    )
PY
