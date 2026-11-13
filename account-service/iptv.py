"""Link the authorized provider to the current Squi TV account."""

import base64
import binascii
import json
import os
import time
from contextlib import closing
from datetime import datetime, timezone
from http import HTTPStatus
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, urlencode
from urllib.request import HTTPRedirectHandler, Request, build_opener

import psycopg2
from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

ORIGIN = "https://squitv.rafael020703.shop"
ISSUER = ORIGIN + "/auth/realms/squitv"
USERINFO = "http://127.0.0.1:8080/auth/realms/squitv/protocol/openid-connect/userinfo"
NATIVE_CLIENT = "squitv-native-login"
NATIVE_TOKEN_URL = USERINFO.rsplit("/", 1)[0] + "/token"
PROVIDER_BASE = "https://u123.life"
PROVIDER = PROVIDER_BASE + "/player_api.php"
PROVIDER_KEY = "xtream-current"
KEY_VERSION = 1
MAX_BODY = 4096
MAX_LINKS = 20


class ApiError(Exception):
    def __init__(self, status, message):
        self.status = HTTPStatus(status)
        self.message = message


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def get_json(url, headers, timeout, body=None, expected=dict, limit=65536):
    opener = build_opener(NoRedirect())
    with opener.open(Request(url, data=body, headers=headers), timeout=timeout) as response:
        raw = response.read(limit + 1)
        if len(raw) > limit:
            raise ValueError("Response too large")
        data = json.loads(raw)
        if not isinstance(data, expected):
            raise ValueError("Invalid response")
        return data


def identity_profile(environ):
    auth = environ.get("HTTP_AUTHORIZATION", "")
    if not auth.startswith("Bearer ") or not 7 < len(auth) <= 8192:
        raise ApiError(401, "Entre novamente na Conta Squi TV.")
    try:
        data = get_json(USERINFO, {
            "Authorization": auth,
            "X-Forwarded-Proto": "https",
            "X-Forwarded-Host": "squitv.rafael020703.shop",
            "X-Forwarded-Port": "443",
            "Accept": "application/json",
        }, 4)
    except HTTPError as exc:
        if exc.code in (400, 401, 403):
            raise ApiError(401, "Entre novamente na Conta Squi TV.") from None
        raise ApiError(503, "Autenticação temporariamente indisponível.") from None
    except (URLError, OSError, ValueError):
        raise ApiError(503, "Autenticação temporariamente indisponível.") from None
    subject = data.get("sub")
    if not isinstance(subject, str) or not 1 <= len(subject) <= 255 or "\x00" in subject:
        raise ApiError(401, "Entre novamente na Conta Squi TV.")
    # UserInfo has authenticated this exact access token. Bind the endpoint to its
    # signed client, issuer and subject rather than trusting the request origin.
    try:
        payload = auth[7:].split(".")[1]
        claims = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
        if claims["iss"] != ISSUER or claims["sub"] != subject or claims["typ"] != "Bearer":
            raise ValueError("Unexpected token")
        client = claims["azp"]
        if not isinstance(client, str):
            raise ValueError("Invalid client")
    except (IndexError, KeyError, TypeError, ValueError, binascii.Error):
        raise ApiError(401, "Entre novamente na Conta Squi TV.") from None
    name = data.get("preferred_username") or claims.get("preferred_username")
    if not isinstance(name, str) or not 1 <= len(name) <= 255:
        name = None
    return subject, client, name


def identity_for(environ):
    subject, client, _ = identity_profile(environ)
    return subject, client


def read_credentials(environ, *, native=False, username_optional=False):
    if (native and environ.get("HTTP_ORIGIN")) or (not native and environ.get("HTTP_ORIGIN") != ORIGIN):
        raise ApiError(403, "Origem inválida.")
    if environ.get("CONTENT_TYPE", "").split(";", 1)[0] != "application/json":
        raise ApiError(415, "Formato inválido.")
    try:
        size = int(environ.get("CONTENT_LENGTH", "0"))
        if not 1 <= size <= MAX_BODY:
            raise ValueError("Invalid length")
        data = json.loads(environ["wsgi.input"].read(size))
        if not isinstance(data, dict):
            raise ValueError("Invalid fields")
        username, password = data.get("username"), data["password"]
        fields = (password,) if username is None and username_optional else (username, password)
        for value in fields:
            if not isinstance(value, str) or not 1 <= len(value) <= 256 or not value.strip():
                raise ValueError("Invalid field")
            value.encode("utf-8")
            if any(ord(char) < 32 or ord(char) == 127 for char in value):
                raise ValueError("Invalid field")
    except (KeyError, ValueError, TypeError):
        message = "Informe o usuário e a senha da Conta Squi TV." if native else "Informe o usuário e a senha do provedor IPTV."
        raise ApiError(400, message) from None
    return username, password


def provider_account(username, password):
    query = urlencode({"username": username, "password": password})
    try:
        result = get_json(PROVIDER + "?" + query, {
            "Accept": "application/json",
            "User-Agent": "SquiTV/1.0",
        }, 6)
    except (URLError, OSError, ValueError):
        raise ApiError(503, "O provedor IPTV não respondeu. Tente novamente.") from None
    user = result.get("user_info")
    if not isinstance(user, dict):
        raise ApiError(503, "O provedor IPTV retornou uma resposta inesperada.")
    return user


def provider_expiry(user):
    try:
        expiry = int(str(user.get("exp_date")))
        return datetime.fromtimestamp(expiry, timezone.utc) if expiry > 0 else None
    except (ValueError, OverflowError, OSError):
        return None


def validate_provider(username, password):
    user = provider_account(username, password)
    if str(user.get("auth")) != "1" or str(user.get("status", "")).strip().lower() != "active":
        raise ApiError(422, "O provedor recusou as credenciais ou a conta está inativa.")
    return provider_expiry(user)


def connection_count(value):
    try:
        count = int(str(value))
        return count if 0 <= count <= 10000 else None
    except ValueError:
        return None


def provider_categories(subject, slot, kind):
    actions = {"live": "get_live_categories", "movie": "get_vod_categories", "series": "get_series_categories"}
    action = actions.get(kind)
    if not action:
        raise ApiError(400, "Catálogo inválido.")
    with closing(connection()) as conn, conn, conn.cursor() as cursor:
        cursor.execute(
            """SELECT c.categories, jsonb_array_length(c.items), c.refreshed_at
               FROM squitv.accounts a
               JOIN squitv.provider_links l ON l.account_id=a.id
               JOIN squitv.catalog_index c ON (c.account_id,c.link_id)=(l.account_id,l.id)
               WHERE a.issuer=%s AND a.subject=%s AND l.slot=%s AND c.kind=%s
                 AND l.encrypted_credentials IS NOT NULL AND c.revision>0
                 AND c.source_validated_at IS NOT DISTINCT FROM l.validated_at
                 AND (l.provider_expires_at IS NULL OR l.provider_expires_at>now())""",
            (ISSUER, subject, slot, kind),
        )
        indexed = cursor.fetchone()
    if indexed is not None:
        categories, total, refreshed_at = indexed
        return {"items": categories, "indexedItems": total, "indexedAt": refreshed_at.isoformat()}

    credentials = linked_credentials(subject, slot)
    query = urlencode({"username": credentials["username"], "password": credentials["password"], "action": action})
    try:
        rows = get_json(PROVIDER + "?" + query, {"Accept": "application/json", "User-Agent": "SquiTV/1.0"},
                        7, expected=list, limit=512 * 1024)
    except (URLError, OSError, ValueError):
        raise ApiError(503, "Não foi possível consultar as categorias do provedor.") from None
    items = {}
    for row in rows:
        if not isinstance(row, dict):
            continue
        identifier = str(row.get("category_id", ""))
        name = row.get("category_name")
        if identifier.isascii() and identifier.isdecimal() and len(identifier) <= 32 and isinstance(name, str) and name.strip():
            items.setdefault(identifier, name.strip()[:180])
        if len(items) >= 1000:
            break
    return {"items": [{"id": key, "name": name} for key, name in items.items()]}


def provider_details(subject, slot):
    credentials = linked_credentials(subject, slot)
    user = provider_account(credentials["username"], credentials["password"])
    state = str(user.get("status", "")).strip().lower()
    if state not in ("active", "expired", "disabled", "banned"):
        state = "unreported" if str(user.get("auth")) == "1" else "rejected"
    elif state == "active" and str(user.get("auth")) != "1":
        state = "rejected"
    expires = provider_expiry(user)
    return {
        "status": state,
        "expiresAt": expires.isoformat() if expires else None,
        "activeConnections": connection_count(user.get("active_cons")),
        "maxConnections": connection_count(user.get("max_connections")),
        "checkedAt": datetime.now(timezone.utc).isoformat(),
    }


def credential(name):
    return (Path(os.environ["CREDENTIALS_DIRECTORY"]) / name).read_bytes()


def connection():
    return psycopg2.connect(
        host="squitv-db.internal",
        port=5432,
        dbname="squitv_account_pilot",
        user="squitv_iptv_portal",
        password=credential("db-password").decode("ascii").strip(),
        sslmode="verify-full",
        sslrootcert="/etc/squitv-account/postgresql-ca.pem",
        connect_timeout=3,
        options="-c statement_timeout=3000 -c lock_timeout=2000",
    )


def associated_data(account_id, slot):
    value = f"{account_id}:{PROVIDER_KEY}:{KEY_VERSION}"
    return (value if slot == 1 else f"{value}:{slot}").encode()


def decrypt_link(account_id, encrypted, version, slot):
    if version != KEY_VERSION:
        raise ApiError(503, "O vínculo IPTV está temporariamente indisponível.")
    data = bytes(encrypted)
    associated = associated_data(account_id, slot)
    return json.loads(AESGCM(credential("iptv-key-v1")).decrypt(data[:12], data[12:], associated))


def link_rows(subject):
    with closing(connection()) as conn, conn, conn.cursor() as cursor:
        cursor.execute(
            """SELECT a.id, l.encrypted_credentials, l.credential_key_version,
                      l.validated_at, l.provider_expires_at, l.slot, l.id
               FROM squitv.accounts a JOIN squitv.provider_links l ON l.account_id = a.id
               WHERE a.issuer = %s AND a.subject = %s AND l.encrypted_credentials IS NOT NULL
               ORDER BY l.slot""",
            (ISSUER, subject),
        )
        return cursor.fetchall()


def link_summary(username, validated_at, expires_at):
    return {
        "linked": True,
        "providerUsername": username,
        "validatedAt": validated_at.isoformat() if validated_at else None,
        "expiresAt": expires_at.isoformat() if expires_at else None,
    }


def link_status(subject, slot):
    rows = link_rows(subject)
    selected = None
    choices = []
    for row in rows:
        credentials = decrypt_link(*row[:3], row[5])
        username = credentials["username"]
        choices.append({"slot": row[5], "username": username})
        if row[5] == slot:
            selected = link_summary(username, row[3], row[4])
    return {**(selected or {"linked": False}), "links": choices, "maxLinks": MAX_LINKS}


def save_link(subject, username, password, expires_at, slot):
    cipher = AESGCM(credential("iptv-key-v1"))
    with closing(connection()) as conn, conn, conn.cursor() as cursor:
        cursor.execute(
            """INSERT INTO squitv.accounts (issuer, subject) VALUES (%s, %s)
               ON CONFLICT (issuer, subject) DO NOTHING""",
            (ISSUER, subject),
        )
        cursor.execute(
            "SELECT id FROM squitv.accounts WHERE issuer = %s AND subject = %s FOR UPDATE",
            (ISSUER, subject),
        )
        account_id = cursor.fetchone()[0]
        associated = associated_data(account_id, slot)
        cursor.execute(
            "SELECT encrypted_credentials, credential_key_version, slot FROM squitv.provider_links WHERE account_id = %s",
            (account_id,),
        )
        for encrypted, version, previous_slot in cursor.fetchall():
            if encrypted is None:
                continue
            current = decrypt_link(account_id, encrypted, version, previous_slot)
            if previous_slot == slot and current["username"] != username:
                raise ApiError(409, "Já existe outro usuário IPTV vinculado nesta posição.")
            if previous_slot != slot and current["username"] == username:
                raise ApiError(409, "Esse usuário IPTV já está vinculado na outra posição.")
        nonce = os.urandom(12)
        plain = json.dumps({"username": username, "password": password}).encode()
        encrypted = nonce + cipher.encrypt(nonce, plain, associated)
        cursor.execute(
            """INSERT INTO squitv.provider_links
                 (account_id, provider_key, encrypted_credentials, credential_key_version, slot,
                  validated_at, provider_expires_at)
               VALUES (%s, %s, %s, %s, %s, now(), %s)
               ON CONFLICT (account_id, slot) DO UPDATE SET
                 encrypted_credentials = EXCLUDED.encrypted_credentials,
                 credential_key_version = EXCLUDED.credential_key_version,
                 validated_at = EXCLUDED.validated_at,
                 provider_expires_at = EXCLUDED.provider_expires_at
               RETURNING validated_at, provider_expires_at""",
            (account_id, PROVIDER_KEY, encrypted, KEY_VERSION, slot, expires_at),
        )
        return link_summary(username, *cursor.fetchone())


def credentials_response(row, display_name=None):
    from account_sync import issue_token
    credentials = decrypt_link(*row[:3], row[5])
    return {
        "username": credentials["username"], "password": credentials["password"],
        "baseUrl": PROVIDER_BASE, "slot": row[5], "displayName": display_name,
        "syncToken": issue_token(row[0], row[6]), "syncScope": str(row[6]),
    }


def linked_credentials(subject, slot, display_name=None):
    row = next((row for row in link_rows(subject) if row[5] == slot), None)
    if row is None:
        raise ApiError(404, "Vincule sua conta IPTV no portal Squi TV antes de entrar.")
    return credentials_response(row, display_name)


def requested_slot(environ):
    options = parse_qs(environ.get("QUERY_STRING", ""), keep_blank_values=True)
    value = options.get("slot", ["1"])
    if len(value) != 1 or value[0] not in {str(slot) for slot in range(1, MAX_LINKS + 1)}:
        raise ApiError(400, "Vínculo inválido.")
    return int(value[0])


def account_display_name(subject):
    with closing(connection()) as conn, conn, conn.cursor() as cursor:
        cursor.execute("SELECT display_name FROM squitv.accounts WHERE issuer=%s AND subject=%s", (ISSUER, subject))
        row = cursor.fetchone()
    return row[0] if row else None


def login_reply(subject, environ, display_name=None):
    rows = link_rows(subject)
    if not rows:
        raise ApiError(404, "Vincule sua conta IPTV no portal Squi TV antes de entrar.")
    display_name = account_display_name(subject) or display_name
    capability = environ.get("HTTP_X_SQUITV_SELECT_LINK")
    if capability == "1":
        rows = [row for row in rows if row[5] <= 2] or rows[:1]
    if len(rows) == 1 or capability not in ("1", "2"):
        return credentials_response(rows[0], display_name)

    nonce = os.urandom(12)
    payload = json.dumps({"sub": subject, "exp": int(time.time()) + 300,
                          "slots": [row[5] for row in rows], "name": display_name}).encode()
    cipher = AESGCM(credential("iptv-key-v1"))
    token = nonce + cipher.encrypt(nonce, payload, b"squitv-link-selection-v1")
    return {
        "choices": [
            {"slot": row[5], "username": decrypt_link(*row[:3], row[5])["username"]}
            for row in rows
        ],
        "selectionToken": base64.urlsafe_b64encode(token).decode(),
    }


def select_link(environ):
    token = environ.get("HTTP_AUTHORIZATION", "")
    if not token.startswith("SquiLink ") or not 30 <= len(token) <= 1024:
        raise ApiError(401, "A seleção IPTV expirou. Entre novamente.")
    try:
        data = base64.b64decode(token[9:], altchars=b"-_", validate=True)
        cipher = AESGCM(credential("iptv-key-v1"))
        claims = json.loads(cipher.decrypt(data[:12], data[12:], b"squitv-link-selection-v1"))
        if int(time.time()) >= claims["exp"]:
            raise ValueError("Expired selection")
    except (InvalidTag, ValueError, TypeError, KeyError, binascii.Error):
        raise ApiError(401, "A seleção IPTV expirou. Entre novamente.") from None
    slot = requested_slot(environ)
    if slot not in claims.get("slots", []):
        raise ApiError(403, "Vínculo inválido.")
    return linked_credentials(claims["sub"], slot, claims.get("name"))


def native_login(environ):
    username, password = read_credentials(environ, native=True)
    try:
        secret = credential("native-client-secret").decode().strip()
    except FileNotFoundError:
        raise ApiError(503, "O login direto ainda não está disponível.") from None
    form = urlencode({
        "grant_type": "password", "client_id": NATIVE_CLIENT, "client_secret": secret,
        "username": username, "password": password, "scope": "openid profile",
    }).encode("ascii")
    try:
        tokens = get_json(NATIVE_TOKEN_URL, {
            "X-Forwarded-Proto": "https",
            "X-Forwarded-Host": "squitv.rafael020703.shop",
            "X-Forwarded-Port": "443",
            "Content-Type": "application/x-www-form-urlencoded",
        }, 6, form)
    except HTTPError as exc:
        if exc.code == 400:
            raise ApiError(401, "Usuário ou senha da Conta Squi TV inválidos.") from None
        raise ApiError(503, "Autenticação temporariamente indisponível.") from None
    except (URLError, OSError, ValueError):
        raise ApiError(503, "Autenticação temporariamente indisponível.") from None
    token = tokens.get("access_token")
    if not isinstance(token, str):
        raise ApiError(503, "Autenticação temporariamente indisponível.")
    subject, client, name = identity_profile({"HTTP_AUTHORIZATION": "Bearer " + token})
    if client != NATIVE_CLIENT:
        raise ApiError(403, "Autorização inválida.")
    return login_reply(subject, environ, name or username)


def application(environ, start_response):
    try:
        path = environ["PATH_INFO"]
        method = environ["REQUEST_METHOD"]
        if path.startswith("/api/sync/"):
            from account_sync import handle
            result = handle(environ)
        elif path == "/api/iptv/native-login":
            if method != "POST":
                raise ApiError(405, "Método inválido.")
            result = native_login(environ)
        elif path == "/api/iptv/credentials":
            if method != "GET":
                raise ApiError(405, "Método inválido.")
            subject, client, name = identity_profile(environ)
            if client != "squitv-tv":
                raise ApiError(403, "Use o aplicativo Squi TV para entrar.")
            result = login_reply(subject, environ, name)
        elif path == "/api/iptv/select":
            if method != "GET":
                raise ApiError(405, "Método inválido.")
            result = select_link(environ)
        elif path == "/api/iptv/reveal":
            if method != "POST":
                raise ApiError(405, "Método inválido.")
            if environ.get("HTTP_ORIGIN") != ORIGIN:
                raise ApiError(403, "Origem inválida.")
            subject, client = identity_for(environ)
            if client != "account-console":
                raise ApiError(403, "Abra o portal Squi TV para consultar o vínculo.")
            result = {"password": linked_credentials(subject, requested_slot(environ))["password"]}
        elif path == "/api/iptv/categories":
            if method != "GET":
                raise ApiError(405, "Método inválido.")
            subject, client = identity_for(environ)
            if client != "account-console":
                raise ApiError(403, "Abra o portal Squi TV para consultar o catálogo.")
            kind = parse_qs(environ.get("QUERY_STRING", "")).get("kind", [""])[0]
            result = provider_categories(subject, requested_slot(environ), kind)
        elif path == "/api/iptv/details":
            if method != "GET":
                raise ApiError(405, "Método inválido.")
            subject, client = identity_for(environ)
            if client != "account-console":
                raise ApiError(403, "Abra o portal Squi TV para consultar o provedor.")
            result = provider_details(subject, requested_slot(environ))
        elif path == "/api/iptv/link":
            if method not in ("GET", "POST"):
                raise ApiError(405, "Método inválido.")
            subject, client = identity_for(environ)
            if client != "account-console":
                raise ApiError(403, "Abra o portal Squi TV para gerenciar o vínculo.")
            slot = requested_slot(environ)
            if method == "POST":
                username, password = read_credentials(environ, username_optional=True)
                if username is None:
                    username = linked_credentials(subject, slot)["username"]
                expires_at = validate_provider(username, password)
                save_link(subject, username, password, expires_at, slot)
                result = link_status(subject, slot)
            else:
                result = link_status(subject, slot)
        else:
            raise ApiError(404, "Endereço inválido.")
        status = HTTPStatus.OK
    except ApiError as exc:
        status, result = exc.status, {"message": exc.message}
    except Exception as exc:
        # Do not log input, tokens, SQL or upstream URLs.
        print("iptv-link:", type(exc).__name__, file=environ["wsgi.errors"])
        status = HTTPStatus.SERVICE_UNAVAILABLE
        result = {"message": "Vínculo IPTV temporariamente indisponível."}
    start_response(f"{status.value} {status.phrase}", [
        ("Content-Type", "application/json; charset=utf-8"),
        ("Cache-Control", "no-store"),
        ("X-Content-Type-Options", "nosniff"),
    ])
    return [json.dumps(result, ensure_ascii=False).encode()]
