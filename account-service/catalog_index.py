"""Build the initial catalog index for linked IPTV accounts."""

import argparse
from contextlib import closing
from urllib.error import HTTPError, URLError
from urllib.parse import unquote, urlencode, urlsplit

from psycopg2.extras import Json

from iptv import PROVIDER, PROVIDER_KEY, connection, decrypt_link, get_json

LOCK_ID = 934188621
MAX_ROWS = 150_000
MAX_LIST_BYTES = 32 * 1024 * 1024
KINDS = {
    "live": ("get_live_categories", "get_live_streams", "stream_id"),
    "movie": ("get_vod_categories", "get_vod_streams", "stream_id"),
    "series": ("get_series_categories", "get_series", "series_id"),
}


def text(value, limit):
    if not isinstance(value, str):
        return ""
    return "".join(ch for ch in value if ch.isprintable()).strip()[:limit]


def identifier(value):
    if type(value) is int:
        value = str(value)
    if not isinstance(value, str) or len(value) > 16 or not value.isascii() or not value.isdecimal():
        return None
    return str(int(value))


def genre(value):
    if isinstance(value, list):
        value = ", ".join(item for item in value[:20] if isinstance(item, str))
    return text(value, 512)


def artwork(value, credentials):
    value = text(value, 1024)
    try:
        url = urlsplit(value)
        if url.scheme not in ("http", "https") or not url.hostname or url.username or url.password or url.query or url.fragment:
            return None
    except ValueError:
        return None
    decoded = unquote(value).casefold()
    if any(secret and secret.casefold() in decoded for secret in credentials.values() if isinstance(secret, str)):
        return None
    return value


def normalized_rows(rows, convert):
    if not 1 <= len(rows) <= MAX_ROWS:
        raise ValueError("unexpected_catalog_size")
    normalized = {}
    for raw in rows:
        if not isinstance(raw, dict):
            continue
        item = convert(raw)
        if item is not None:
            normalized.setdefault(item["id"], item)
    if len(normalized) * 100 < len(rows) * 90:
        raise ValueError("unexpected_catalog_rows")
    return list(normalized.values())


def category(row):
    key = identifier(row.get("category_id"))
    name = text(row.get("category_name"), 240)
    return {"id": key, "name": name} if key is not None and name else None


def entry(row, kind, id_field, credentials):
    key = identifier(row.get(id_field))
    name = text(row.get("name"), 512)
    if key is None or not 1 <= int(key) <= 2**31 - 1 or not name:
        return None
    data = {"id": int(key), "name": name, "categoryId": identifier(row.get("category_id"))}
    fields = {
        "live": {"epgId": ("epg_channel_id", 180), "added": ("added", 24)},
        "movie": {"extension": ("container_extension", 16), "added": ("added", 24),
                  "releaseDate": ("release_date", 32), "rating": ("rating", 16)},
        "series": {"plot": ("plot", 2048), "releaseDate": ("releaseDate", 32),
                   "lastModified": ("last_modified", 24), "rating": ("rating", 16)},
    }[kind]
    for output, (source, limit) in fields.items():
        value = row.get(source)
        if type(value) in (int, float):
            value = str(value)
        value = text(value, limit)
        if value:
            data[output] = value
    if kind != "live":
        value = genre(row.get("genre"))
        if value:
            data["genre"] = value
    if value := artwork(row.get("cover" if kind == "series" else "stream_icon"), credentials):
        data["poster"] = value
    backdrops = row.get("backdrop_path")
    if isinstance(backdrops, list):
        if value := next((safe for path in backdrops[:4] if (safe := artwork(path, credentials))), None):
            data["backdrop"] = value
    return data


def fetch_catalog(credentials, kind):
    category_action, item_action, id_field = KINDS[kind]

    def fetch(action, limit):
        query = urlencode({"username": credentials["username"], "password": credentials["password"], "action": action})
        return get_json(PROVIDER + "?" + query, {"Accept": "application/json", "User-Agent": "SquiTV/1.0"},
                        12, expected=list, limit=limit)

    categories = normalized_rows(fetch(category_action, 512 * 1024), category)
    items = normalized_rows(fetch(item_action, MAX_LIST_BYTES), lambda row: entry(row, kind, id_field, credentials))
    return categories, items


def index_due(conn, maximum):
    with conn, conn.cursor() as cursor:
        cursor.execute(
            """INSERT INTO squitv.catalog_index (account_id, link_id, kind)
               SELECT l.account_id, l.id, kinds.kind FROM squitv.provider_links l
               CROSS JOIN unnest(%s::text[]) AS kinds(kind)
               WHERE l.provider_key=%s AND l.encrypted_credentials IS NOT NULL
                 AND (l.provider_expires_at IS NULL OR l.provider_expires_at>now())
               ON CONFLICT DO NOTHING""",
            (list(KINDS), PROVIDER_KEY),
        )
        cursor.execute(
            """SELECT c.account_id, c.link_id, c.kind, l.encrypted_credentials,
                      l.credential_key_version, l.slot, l.validated_at
               FROM squitv.catalog_index c JOIN squitv.provider_links l
                 ON (l.account_id,l.id)=(c.account_id,c.link_id)
               WHERE l.provider_key=%s AND l.encrypted_credentials IS NOT NULL
                 AND (l.provider_expires_at IS NULL OR l.provider_expires_at>now())
                 AND (c.next_attempt_at<=now() OR
                      (c.failures=0 AND c.source_validated_at IS DISTINCT FROM l.validated_at))
               ORDER BY c.next_attempt_at, c.account_id, c.link_id, c.kind LIMIT %s""",
            (PROVIDER_KEY, maximum),
        )
        return cursor.fetchall()


def refresh(conn, job):
    account, link, kind, encrypted, version, slot, validated_at = job
    try:
        categories, items = fetch_catalog(decrypt_link(account, encrypted, version, slot), kind)
    except HTTPError as failure:
        error = "provider_http_" + str(failure.code)
    except (URLError, OSError, ValueError):
        error = "provider_unavailable_or_invalid"
    else:
        with conn, conn.cursor() as cursor:
            cursor.execute(
                """UPDATE squitv.catalog_index c SET revision=revision+1,
                   categories=%s, items=%s, refreshed_at=now(), source_validated_at=%s,
                   failures=0, last_error=NULL, next_attempt_at=now()+interval '6 hours'
                   FROM squitv.provider_links l
                   WHERE (l.account_id,l.id)=(c.account_id,c.link_id)
                     AND (c.account_id,c.link_id,c.kind)=(%s,%s,%s)
                     AND l.validated_at IS NOT DISTINCT FROM %s
                     AND l.encrypted_credentials IS NOT NULL
                     AND (l.provider_expires_at IS NULL OR l.provider_expires_at>now())""",
                (Json(categories), Json(items), validated_at, account, link, kind, validated_at),
            )
            applied = cursor.rowcount == 1
        if applied:
            print(f"{kind}: {len(categories)} categorias, {len(items)} itens", flush=True)
        return
    with conn, conn.cursor() as cursor:
        cursor.execute(
            """UPDATE squitv.catalog_index SET failures=failures+1,last_error=%s,
               next_attempt_at=now()+make_interval(mins => least(240, 5 * (1 << least(failures, 6))))
               WHERE (account_id,link_id,kind)=(%s,%s,%s)""",
            (error, account, link, kind),
        )
    print(f"{kind}: {error}", flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--max-jobs", type=int, choices=range(1, 4), default=3)
    options = parser.parse_args()
    with closing(connection()) as conn:
        with conn, conn.cursor() as cursor:
            cursor.execute("SELECT pg_try_advisory_lock(%s)", (LOCK_ID,))
            if not cursor.fetchone()[0]:
                return
        for job in index_due(conn, options.max_jobs):
            try:
                refresh(conn, job)
            except Exception:
                # Neither the provider request nor SQL parameters belong in service logs.
                print(f"{job[2]}: erro interno na atualização", flush=True)


if __name__ == "__main__":
    main()
