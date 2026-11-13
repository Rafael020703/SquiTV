"""Scoped cloud profile and favorite mutations for Squi TV."""

import base64
import json
import os
import re
import time
from contextlib import closing
from urllib.parse import parse_qs
from uuid import UUID

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

from iptv import ApiError, ISSUER, ORIGIN, connection, credential, identity_for, requested_slot

AAD = b"squitv-favorite-sync-v1"
KINDS = ("live", "movie", "series")
MAX_REVISION = 2**53 - 1


def issue_token(account_id, link_id):
    nonce = os.urandom(12)
    claims = json.dumps({"account": str(account_id), "link": str(link_id), "exp": int(time.time()) + 12 * 3600}).encode()
    cipher = AESGCM(credential("iptv-key-v1"))
    return base64.urlsafe_b64encode(nonce + cipher.encrypt(nonce, claims, AAD)).decode()


def app_scope(header):
    token = header.removeprefix("SquiSync ")
    if not re.fullmatch(r"[A-Za-z0-9_=-]{30,512}", token):
        raise ApiError(401, "Entre novamente com a Conta Squi TV para sincronizar.")
    try:
        data = base64.b64decode(token, altchars=b"-_", validate=True)
        cipher = AESGCM(credential("iptv-key-v1"))
        claims = json.loads(cipher.decrypt(data[:12], data[12:], AAD))
        if int(time.time()) >= claims["exp"]:
            raise ValueError("Expired session")
        return str(UUID(claims["account"])), str(UUID(claims["link"]))
    except (InvalidTag, ValueError, TypeError, KeyError):
        raise ApiError(401, "Entre novamente com a Conta Squi TV para sincronizar.") from None


def read_json(environ):
    if environ.get("CONTENT_TYPE", "").split(";", 1)[0] != "application/json":
        raise ApiError(415, "Formato inválido.")
    try:
        size = int(environ.get("CONTENT_LENGTH", "0"))
        if not 1 <= size <= 32768:
            raise ValueError()
        data = json.loads(environ["wsgi.input"].read(size))
        if not isinstance(data, dict):
            raise ValueError()
        return data
    except (ValueError, TypeError):
        raise ApiError(400, "Dados inválidos.") from None


def bounded_int(value, low, high):
    if type(value) is not int or not low <= value <= high:
        raise ApiError(400, "Número inválido.")
    return value


def read_text(value, limit, required=True):
    if value is None and not required:
        return ""
    if not isinstance(value, str):
        raise ApiError(400, "Texto inválido.")
    text = value.strip()
    if (required and not text) or len(text) > limit or any(ord(char) < 32 or 0xD800 <= ord(char) <= 0xDFFF for char in text):
        raise ApiError(400, "Texto inválido.")
    return text


def id_value(value):
    try:
        if not isinstance(value, str):
            raise ValueError()
        return str(UUID(value))
    except ValueError:
        raise ApiError(400, "Perfil inválido.") from None


def query_value(environ, name, default=None):
    value = parse_qs(environ.get("QUERY_STRING", ""), keep_blank_values=True).get(name, [default])
    if len(value) != 1:
        raise ApiError(400, "Parâmetros inválidos.")
    return value[0]


def resolve_scope(cursor, environ, writing):
    header = environ.get("HTTP_AUTHORIZATION", "")
    if header.startswith("SquiSync "):
        account_id, link_id = app_scope(header)
        cursor.execute(
            """SELECT a.id, l.id FROM squitv.accounts a
               JOIN squitv.provider_links l ON l.account_id=a.id
               WHERE a.id=%s AND l.id=%s AND l.encrypted_credentials IS NOT NULL""",
            (account_id, link_id),
        )
    else:
        subject, client = identity_for(environ)
        if client != "account-console" or (writing and environ.get("HTTP_ORIGIN") != ORIGIN):
            raise ApiError(403, "Autorização inválida.")
        cursor.execute(
            """SELECT a.id, l.id FROM squitv.accounts a
               JOIN squitv.provider_links l ON l.account_id=a.id
               WHERE a.issuer=%s AND a.subject=%s AND l.slot=%s AND l.encrypted_credentials IS NOT NULL""",
            (ISSUER, subject, requested_slot(environ)),
        )
    row = cursor.fetchone()
    if row is None:
        raise ApiError(404, "Vincule sua conta IPTV para sincronizar.")
    if writing:
        cursor.execute("SELECT id FROM squitv.accounts WHERE id=%s FOR UPDATE", (row[0],))
    return row


def profiles(cursor, account_id):
    cursor.execute(
        """SELECT id, display_name, revision FROM squitv.profiles
           WHERE account_id=%s AND deleted_at IS NULL ORDER BY lower(display_name), id""",
        (account_id,),
    )
    return [{"id": str(row[0]), "name": row[1], "revision": row[2]} for row in cursor.fetchall()]


def update_profile(cursor, account_id, data):
    name = read_text(data.get("name"), 80)
    action = data.get("action")
    if action == "create":
        if len(profiles(cursor, account_id)) >= 20:
            raise ApiError(409, "Limite de perfis atingido.")
        cursor.execute(
            "INSERT INTO squitv.profiles (account_id,display_name) VALUES (%s,%s) RETURNING id,revision",
            (account_id, name),
        )
    elif action == "rename":
        cursor.execute(
            """UPDATE squitv.profiles SET display_name=%s,revision=nextval('squitv.profile_revision')
               WHERE account_id=%s AND id=%s AND deleted_at IS NULL AND revision=%s RETURNING id,revision""",
            (name, account_id, id_value(data.get("id")), bounded_int(data.get("revision"), 1, MAX_REVISION)),
        )
    else:
        raise ApiError(400, "Ação inválida.")
    row = cursor.fetchone()
    if not row:
        raise ApiError(409, "O perfil foi atualizado em outro dispositivo.")
    return {"id": str(row[0]), "name": name, "revision": row[1]}


def require_profile(cursor, account_id, profile_id):
    cursor.execute(
        "SELECT 1 FROM squitv.profiles WHERE account_id=%s AND id=%s AND deleted_at IS NULL",
        (account_id, profile_id),
    )
    if not cursor.fetchone():
        raise ApiError(404, "Perfil não encontrado.")


def favorite_json(row):
    return {"kind": row[0], "id": row[1], "title": row[2], "revision": row[3], "deleted": row[4]}


def fill_catalog_titles(cursor, scope, items):
    missing = {}
    for item in items:
        if item["deleted"]:
            continue
        title = (item.get("title") or "").strip()
        if title and not re.fullmatch(r"(?i)(?:(?:filme|série|serie|canal|episódio|episodio)\s+)?#\s*\d+", title):
            continue
        kind = item["kind"]
        content_id = item["id"]
        if kind == "episode":
            kind, content_id = "series", item.get("catalogId")
        if content_id:
            missing.setdefault(kind, {}).setdefault(content_id, []).append(item)
    if not missing:
        return
    account, _, link = scope
    cursor.execute(
        """SELECT c.kind, (entry->>'id')::integer, left(entry->>'name', 240)
           FROM squitv.catalog_index c
           JOIN squitv.provider_links l ON (l.account_id,l.id)=(c.account_id,c.link_id)
           CROSS JOIN LATERAL jsonb_array_elements(c.items) AS entry
           WHERE (c.account_id,c.link_id)=(%s,%s) AND c.kind=ANY(%s)
             AND c.source_validated_at IS NOT DISTINCT FROM l.validated_at
             AND l.encrypted_credentials IS NOT NULL
             AND (l.provider_expires_at IS NULL OR l.provider_expires_at>now())
             AND (entry->>'id')::integer=ANY(%s)""",
        (account, link, list(missing), list({key for keys in missing.values() for key in keys})),
    )
    for kind, content_id, title in cursor.fetchall():
        if title:
            for item in missing.get(kind, {}).get(content_id, ()):
                item["title"] = title


def favorite_feed(cursor, scope, after):
    cursor.execute(
        """SELECT content_kind,content_id,title,revision,deleted_at IS NOT NULL
           FROM squitv.favorites WHERE account_id=%s AND profile_id=%s AND link_id=%s AND revision>%s
           ORDER BY revision LIMIT 51""",
        (*scope, after),
    )
    rows = cursor.fetchall()
    items = [favorite_json(row) for row in rows[:50]]
    fill_catalog_titles(cursor, scope, items)
    return {"items": items, "next": items[-1]["revision"] if items else after, "hasMore": len(rows) > 50}


def favorite_key(data):
    kind = data.get("kind")
    if kind not in KINDS:
        raise ApiError(400, "Tipo de conteúdo inválido.")
    return kind, bounded_int(data.get("id"), 1, 2**31 - 1)


def import_favorites(cursor, scope, data):
    items = data.get("items")
    if not isinstance(items, list) or not 1 <= len(items) <= 50:
        raise ApiError(400, "Lote de favoritos inválido.")
    for item in items:
        if not isinstance(item, dict):
            raise ApiError(400, "Favorito inválido.")
        kind, content_id = favorite_key(item)
        title = read_text(item.get("title", ""), 240, required=False)
        cursor.execute(
            """INSERT INTO squitv.favorites (account_id,profile_id,link_id,content_kind,content_id,title)
               VALUES (%s,%s,%s,%s,%s,%s) ON CONFLICT DO NOTHING""",
            (*scope, kind, content_id, title),
        )
    return {"imported": True}


def update_favorite(cursor, scope, data):
    kind, content_id = favorite_key(data)
    revision = bounded_int(data.get("revision"), 0, MAX_REVISION)
    deleted = data.get("deleted")
    if type(deleted) is not bool or (deleted and revision == 0):
        raise ApiError(400, "Alteração de favorito inválida.")
    title = read_text(data.get("title", ""), 240, required=False)
    if revision == 0:
        cursor.execute(
            """INSERT INTO squitv.favorites (account_id,profile_id,link_id,content_kind,content_id,title)
               VALUES (%s,%s,%s,%s,%s,%s) ON CONFLICT DO NOTHING
               RETURNING content_kind,content_id,title,revision,deleted_at IS NOT NULL""",
            (*scope, kind, content_id, title),
        )
    else:
        cursor.execute(
            """UPDATE squitv.favorites SET
                   deleted_at=CASE WHEN %s THEN now() ELSE NULL END,
                   title=CASE WHEN %s='' THEN title ELSE %s END, revision=nextval('squitv.profile_revision')
               WHERE account_id=%s AND profile_id=%s AND link_id=%s
                 AND content_kind=%s AND content_id=%s AND revision=%s
               RETURNING content_kind,content_id,title,revision,deleted_at IS NOT NULL""",
            (deleted, title, title, *scope, kind, content_id, revision),
        )
    row = cursor.fetchone()
    if row is None:
        raise ApiError(409, "O favorito foi atualizado em outro dispositivo.")
    return favorite_json(row)


def update_favorites_batch(cursor, scope, data):
    entries = data.get("items")
    if not isinstance(entries, list) or not 1 <= len(entries) <= 20:
        raise ApiError(400, "Lote de alterações inválido.")
    conflicts = 0
    for entry in entries:
        if not isinstance(entry, dict):
            raise ApiError(400, "Alteração de favorito inválida.")
        try:
            update_favorite(cursor, scope, entry)
        except ApiError as error:
            if error.status != 409:
                raise
            conflicts += 1
    return {"conflicts": conflicts}


def display_name_settings(environ):
    method = environ["REQUEST_METHOD"]
    if method not in ("GET", "POST"):
        raise ApiError(405, "Método inválido.")
    subject, client = identity_for(environ)
    if client != "account-console" or (method == "POST" and environ.get("HTTP_ORIGIN") != ORIGIN):
        raise ApiError(403, "Autorização inválida.")
    if method == "POST":
        name = read_text(read_json(environ).get("displayName"), 80, required=False) or None
        with closing(connection()) as conn, conn, conn.cursor() as cursor:
            cursor.execute(
                """INSERT INTO squitv.accounts (issuer,subject,display_name) VALUES (%s,%s,%s)
                   ON CONFLICT (issuer,subject) DO UPDATE SET display_name=EXCLUDED.display_name""",
                (ISSUER, subject, name),
            )
        return {"displayName": name}
    from iptv import account_display_name
    return {"displayName": account_display_name(subject)}


def category_json(row):
    return {"kind": row[0], "mode": row[1], "order": row[2], "hidden": row[3], "revision": row[4]}


def categories(cursor, scope):
    cursor.execute(
        """SELECT catalog_kind,category_mode,category_order,hidden_categories,revision
           FROM squitv.category_preferences WHERE account_id=%s AND profile_id=%s AND link_id=%s
           ORDER BY catalog_kind""",
        scope,
    )
    return {"items": [category_json(row) for row in cursor.fetchall()]}


def category_ids(value):
    if not isinstance(value, list) or len(value) > 1000:
        raise ApiError(400, "Lista de categorias inválida.")
    return list(dict.fromkeys(read_text(item, 128) for item in value))


def update_categories(cursor, scope, data):
    rows = data.get("items")
    if not isinstance(rows, list) or not 1 <= len(rows) <= 3:
        raise ApiError(400, "Organização de categorias inválida.")
    seen = set()
    result = []
    for row in rows:
        if not isinstance(row, dict):
            raise ApiError(400, "Organização de categorias inválida.")
        kind, mode = row.get("kind"), row.get("mode")
        if kind not in KINDS or kind in seen or mode not in ("provider", "genre") or (kind == "live" and mode != "provider"):
            raise ApiError(400, "Modo de categorias inválido.")
        seen.add(kind)
        order, hidden = category_ids(row.get("order")), category_ids(row.get("hidden"))
        revision = bounded_int(row.get("revision"), 0, MAX_REVISION)
        if revision == 0:
            cursor.execute(
                """INSERT INTO squitv.category_preferences
                       (account_id,profile_id,link_id,catalog_kind,category_mode,category_order,hidden_categories)
                   VALUES (%s,%s,%s,%s,%s,%s,%s) ON CONFLICT DO NOTHING
                   RETURNING catalog_kind,category_mode,category_order,hidden_categories,revision""",
                (*scope, kind, mode, order, hidden),
            )
        else:
            cursor.execute(
                """UPDATE squitv.category_preferences SET category_mode=%s,category_order=%s,hidden_categories=%s,
                       revision=nextval('squitv.profile_revision')
                   WHERE account_id=%s AND profile_id=%s AND link_id=%s AND catalog_kind=%s AND revision=%s
                   RETURNING catalog_kind,category_mode,category_order,hidden_categories,revision""",
                (mode, order, hidden, *scope, kind, revision),
            )
        saved = cursor.fetchone()
        if saved is None:
            raise ApiError(409, "A organização foi alterada em outro dispositivo. Consulte novamente.")
        result.append(category_json(saved))
    return {"items": result}


PROGRESS_COLUMNS = """content_kind,content_id,catalog_id,title,position_ms,duration_ms,
                      completed,observed_ms,revision,deleted_at IS NOT NULL"""


def progress_json(row):
    return {
        "kind": row[0], "id": row[1], "catalogId": row[2], "title": row[3], "positionMs": row[4],
        "durationMs": row[5], "completed": row[6], "observedAt": row[7], "revision": row[8], "deleted": row[9],
    }


def progress_feed(cursor, scope, after):
    cursor.execute(
        f"""SELECT {PROGRESS_COLUMNS} FROM squitv.watch_progress
            WHERE account_id=%s AND profile_id=%s AND link_id=%s AND revision>%s
            ORDER BY revision LIMIT 41""",
        (*scope, after),
    )
    rows = cursor.fetchall()
    items = [progress_json(row) for row in rows[:40]]
    fill_catalog_titles(cursor, scope, items)
    return {"items": items, "next": items[-1]["revision"] if items else after, "hasMore": len(rows) > 40}


def progress_key(data):
    kind = data.get("kind")
    if kind not in ("movie", "episode"):
        raise ApiError(400, "Tipo de progresso inválido.")
    return kind, bounded_int(data.get("id"), 1, 2**31 - 1)


def save_progress(cursor, scope, data, portal):
    now = int(time.time() * 1000)
    if portal:
        kind, content_id = progress_key(data)
        if data.get("action") != "delete":
            raise ApiError(400, "Ação inválida.")
        cursor.execute(
            f"""UPDATE squitv.watch_progress SET deleted_at=now(), observed_ms=%s,
                   revision=nextval('squitv.profile_revision')
                WHERE account_id=%s AND profile_id=%s AND link_id=%s
                  AND content_kind=%s AND content_id=%s AND revision=%s
                RETURNING {PROGRESS_COLUMNS}""",
            (now, *scope, kind, content_id, bounded_int(data.get("revision"), 1, MAX_REVISION)),
        )
        row = cursor.fetchone()
        if row is None:
            raise ApiError(409, "O progresso foi atualizado em outro dispositivo.")
        return progress_json(row)

    rows = data.get("items")
    if not isinstance(rows, list) or not 1 <= len(rows) <= 20:
        raise ApiError(400, "Lote de progresso inválido.")
    for entry in rows:
        if not isinstance(entry, dict):
            raise ApiError(400, "Progresso inválido.")
        kind, content_id = progress_key(entry)
        catalog_id = entry.get("catalogId")
        if catalog_id is not None:
            catalog_id = bounded_int(catalog_id, 1, 2**31 - 1)
        title = read_text(entry.get("title"), 240, required=False)
        duration = entry.get("durationMs")
        if duration is not None:
            duration = bounded_int(duration, 1, 172800000)
        position = bounded_int(entry.get("positionMs"), 0, duration or 172800000)
        observed = bounded_int(entry.get("observedAt"), 1, now + 300000)
        known_revision = bounded_int(entry.get("revision", 0), 0, MAX_REVISION)
        complete = entry.get("completed")
        if type(complete) is not bool:
            raise ApiError(400, "Progresso inválido.")
        cursor.execute(
            """INSERT INTO squitv.watch_progress
                   (account_id,profile_id,link_id,content_kind,content_id,catalog_id,title,
                    position_ms,duration_ms,completed,observed_ms)
               VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
               ON CONFLICT (account_id,profile_id,link_id,content_kind,content_id) DO UPDATE SET
                   catalog_id=COALESCE(EXCLUDED.catalog_id,watch_progress.catalog_id),
                   title=CASE WHEN EXCLUDED.title='' THEN watch_progress.title ELSE EXCLUDED.title END,
                   position_ms=EXCLUDED.position_ms,duration_ms=EXCLUDED.duration_ms,
                   completed=EXCLUDED.completed,observed_ms=EXCLUDED.observed_ms,
                   deleted_at=NULL,revision=nextval('squitv.profile_revision')
               WHERE EXCLUDED.observed_ms > watch_progress.observed_ms
                 AND (watch_progress.deleted_at IS NULL OR watch_progress.revision=%s)""",
            (*scope, kind, content_id, catalog_id, title, position, duration, complete, min(observed, now), known_revision),
        )
    return {"saved": True}


def handle(environ):
    method = environ["REQUEST_METHOD"]
    if method not in ("GET", "POST"):
        raise ApiError(405, "Método inválido.")
    path = environ["PATH_INFO"]
    if path == "/api/sync/account":
        return display_name_settings(environ)
    writing = method == "POST"
    data = read_json(environ) if writing else None
    with closing(connection()) as conn, conn, conn.cursor() as cursor:
        account_id, link_id = resolve_scope(cursor, environ, writing)
        if path == "/api/sync/catalog":
            if writing:
                raise ApiError(405, "Método inválido.")
            from catalog_api import read_catalog
            return read_catalog(cursor, account_id, link_id, environ)
        if path == "/api/sync/profiles":
            return update_profile(cursor, account_id, data) if writing else {"profiles": profiles(cursor, account_id)}
        if path not in ("/api/sync/favorites", "/api/sync/categories", "/api/sync/progress"):
            raise ApiError(404, "Endereço inválido.")
        profile_id = id_value(query_value(environ, "profile"))
        require_profile(cursor, account_id, profile_id)
        scope = (account_id, profile_id, link_id)
        if path == "/api/sync/categories":
            return update_categories(cursor, scope, data) if writing else categories(cursor, scope)
        if path == "/api/sync/progress":
            if writing:
                return save_progress(cursor, scope, data, not environ.get("HTTP_AUTHORIZATION", "").startswith("SquiSync "))
            value = query_value(environ, "after", "0")
            if not re.fullmatch(r"[0-9]{1,16}", value):
                raise ApiError(400, "Revisão inválida.")
            return progress_feed(cursor, scope, bounded_int(int(value), 0, MAX_REVISION))
        if writing:
            if data.get("action") == "import":
                return import_favorites(cursor, scope, data)
            if data.get("action") == "update":
                return update_favorites_batch(cursor, scope, data)
            return update_favorite(cursor, scope, data)
        cursor_value = query_value(environ, "after", "0")
        if not re.fullmatch(r"[0-9]{1,16}", cursor_value):
            raise ApiError(400, "Revisão inválida.")
        return favorite_feed(cursor, scope, bounded_int(int(cursor_value), 0, MAX_REVISION))
