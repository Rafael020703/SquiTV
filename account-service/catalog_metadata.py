"""Prepare detail metadata without storing playback URLs or provider credentials."""

import time
from concurrent.futures import ThreadPoolExecutor
from contextlib import closing
from urllib.error import HTTPError
from urllib.parse import urlencode, urljoin, urlsplit

from psycopg2.extras import Json

from catalog_index import artwork, genre, identifier, text
from iptv import PROVIDER, connection, decrypt_link, get_json

ACTIONS = {
    "live": ("get_short_epg", "stream_id"),
    "movie": ("get_vod_info", "vod_id"),
    "series": ("get_series_info", "series_id"),
}
METADATA_REVIEW_VERSION = 1
GENRE_CATEGORIES = {
    "ação": "Ação", "romance": "Romance", "drama": "Drama", "comedia": "Comédia",
    "comédia": "Comédia", "aventura familia fantasia": "Aventura, Família, Fantasia",
    "terror thriller suspense": "Terror, Suspense", "animação infantil": "Animação",
    "serie ∤ ação": "Ação", "serie ∤ comédia": "Comédia", "serie ∤ drama": "Drama",
}


def fields(raw, limits):
    if not isinstance(raw, dict):
        return {}
    result = {}
    for key, limit in limits.items():
        value = raw.get(key)
        if type(value) in (float, int):
            value = str(value)
        value = text(value, limit)
        if value:
            result[key] = value
    return result


def editorial_text(value, limit):
    clean = text(value, limit)
    return "" if clean.casefold().rstrip(".") in {
        "-", "n/a", "null", "none", "sem sinopse", "sem descrição", "no overview found",
    } else clean


def normalize(raw, kind, credentials):
    if kind == "live":
        rows = raw.get("epg_listings")
        if not isinstance(rows, list):
            raise ValueError("missing_epg")
        listings = []
        for row in rows[:10]:
            if not isinstance(row, dict):
                continue
            item = fields(row, {"title": 1024, "description": 4096, "start": 40, "end": 40})
            for key in ("start_timestamp", "stop_timestamp"):
                if identifier(row.get(key)) is not None:
                    item[key] = int(row[key])
            listings.append(item)
        return {"epg_listings": listings}
    raw_info = raw.get("info")
    if not isinstance(raw_info, dict):
        raise ValueError("missing_info")
    info = fields(raw_info, {"name": 512, "cast": 1024, "director": 512, "rating": 16,
                  "releaseDate": 32, "duration": 32, "episode_run_time": 16, "youtube_trailer": 32})
    movie_data = raw.get("movie_data") if isinstance(raw.get("movie_data"), dict) else {}
    for source in (raw_info, movie_data):
        if "plot" not in info:
            for key in ("plot", "description", "overview", "synopsis"):
                if value := editorial_text(source.get(key), 4096):
                    info["plot"] = value
                    break
        if "releaseDate" not in info:
            for key in ("releasedate", "release_date", "first_air_date"):
                if value := text(source.get(key), 32):
                    info["releaseDate"] = value
                    break
    if value := genre(raw_info.get("genre") or raw_info.get("genres") or movie_data.get("genre")):
        info["genre"] = value
    for key in ("cover", "movie_image"):
        if value := artwork(raw_info.get(key), credentials):
            info[key] = value
    raw_backdrops = raw_info.get("backdrop_path")
    if isinstance(raw_backdrops, list):
        backdrops = [safe for value in raw_backdrops[:4] if (safe := artwork(value, credentials))]
        if backdrops:
            info["backdrop_path"] = backdrops
    if kind == "movie":
        return {"info": info, "movie_data": fields(raw.get("movie_data"), {"name": 512, "container_extension": 16})}
    raw_episodes = raw.get("episodes")
    if isinstance(raw_episodes, list):
        raw_episodes = {str(i): row for i, row in enumerate(raw_episodes)}
    if not isinstance(raw_episodes, dict):
        raw_episodes = {}
    episodes = {}
    for season, rows in list(raw_episodes.items())[:500]:
        if identifier(season) is None or not isinstance(rows, list):
            continue
        valid = []
        for row in rows[:1000]:
            if not isinstance(row, dict) or identifier(row.get("id")) in (None, "0"):
                continue
            value = fields(row, {"id": 16, "episode_num": 16, "title": 512, "container_extension": 16,
                                 "added": 24, "season": 16})
            episode_info = row.get("info")
            value["info"] = fields(episode_info, {"plot": 2048, "duration": 32, "release_date": 32, "rating": 16})
            for source in (episode_info, row):
                if not isinstance(source, dict):
                    continue
                image = next((safe for key in ("movie_image", "episode_image", "thumbnail", "image")
                              if (safe := artwork(source.get(key), credentials))), None)
                if image:
                    value["info"]["movie_image"] = image
                    break
            valid.append(value)
        if valid:
            episodes[season] = valid
    seasons = []
    if isinstance(raw.get("seasons"), list):
        for row in raw["seasons"][:500]:
            if not isinstance(row, dict) or identifier(row.get("season_number")) is None:
                continue
            value = fields(row, {"name": 240, "overview": 1024, "air_date": 32})
            for key in ("cover", "cover_big"):
                if image := artwork(row.get(key), credentials):
                    value[key] = image
            for key in ("id", "season_number", "episode_count"):
                if identifier(row.get(key)) is not None:
                    value[key] = int(row[key])
            seasons.append(value)
    return {"info": info, "episodes": episodes, "seasons": seasons, "episodes_pending": not episodes,
            "episode_artwork_version": 1}


def fetch(job):
    _, _, kind, item, _, encrypted, version, slot = job
    credentials = decrypt_link(job[0], encrypted, version, slot)
    action, parameter = ACTIONS[kind]
    args = {"username": credentials["username"], "password": credentials["password"], "action": action, parameter: item}
    if kind == "live":
        args["limit"] = 5
    origin = urlsplit(PROVIDER)
    url = PROVIDER + "?" + urlencode(args)
    deadline = time.monotonic() + 14
    for hop in range(4):
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise TimeoutError("provider_timeout")
        try:
            raw = get_json(url, {"Accept": "application/json", "User-Agent": "SquiTV/1.0"},
                           min(remaining, 8), limit=3 * 1024 * 1024)
            payload = normalize(raw, kind, credentials)
            if kind != "live":
                from open_metadata import enrich_open
                from tmdb_metadata import enrich
                payload["metadata_review_version"] = METADATA_REVIEW_VERSION
                return enrich_open(enrich(payload, raw, kind), raw, kind)
            return payload
        except HTTPError as failure:
            with failure:
                location = failure.headers.get("Location")
                if failure.code not in (301, 302, 303, 307, 308) or not location or hop == 3:
                    raise
                target = urlsplit(urljoin(url, location))
                if (target.scheme != "https" or target.hostname != origin.hostname or
                        (target.port or 443) != (origin.port or 443) or target.username or target.password):
                    raise ValueError("provider_redirect_rejected") from None
                url = target._replace(fragment="").geturl()
    raise ValueError("provider_redirect_limit")


def next_jobs(conn):
    with conn, conn.cursor() as cursor:
        cursor.execute(
            """SELECT m.account_id,m.link_id,m.kind,m.item_id,l.validated_at,
                      l.encrypted_credentials,l.credential_key_version,l.slot
               FROM squitv.catalog_metadata m
               JOIN squitv.provider_links l ON (l.account_id,l.id)=(m.account_id,m.link_id)
               WHERE m.next_attempt_at<=now() AND l.encrypted_credentials IS NOT NULL
                 AND (m.kind<>'live' OR m.priority)
                 AND (l.provider_expires_at IS NULL OR l.provider_expires_at>now())
               ORDER BY m.priority DESC,
                        row_number() OVER (PARTITION BY m.account_id,m.link_id,m.kind
                                           ORDER BY m.priority DESC,m.next_attempt_at,m.item_id),
                        m.next_attempt_at,m.item_id LIMIT 5"""
        )
        return cursor.fetchall()


def seed_jobs(conn):
    with conn, conn.cursor() as cursor:
        cursor.execute(
            """INSERT INTO squitv.catalog_metadata (account_id,link_id,kind,item_id)
               SELECT c.account_id,c.link_id,c.kind,(item->>'id')::integer FROM squitv.catalog_index c
               JOIN squitv.provider_links l ON (l.account_id,l.id)=(c.account_id,c.link_id)
               CROSS JOIN LATERAL jsonb_array_elements(c.items) AS item
               WHERE c.kind IN ('movie','series') AND c.revision>0
                 AND c.metadata_seeded_revision<>c.revision
                 AND c.source_validated_at IS NOT DISTINCT FROM l.validated_at
                 AND (l.provider_expires_at IS NULL OR l.provider_expires_at>now())
               ON CONFLICT DO NOTHING"""
        )
        cursor.execute(
            """UPDATE squitv.catalog_index c SET metadata_seeded_revision=c.revision
               FROM squitv.provider_links l
               WHERE (l.account_id,l.id)=(c.account_id,c.link_id)
                 AND c.kind IN ('movie','series') AND c.revision>0 AND c.metadata_seeded_revision<>c.revision
                 AND c.source_validated_at IS NOT DISTINCT FROM l.validated_at
                 AND (l.provider_expires_at IS NULL OR l.provider_expires_at>now())"""
        )


def finish(conn, job, future):
    account, link, kind, item, validated_at, *_ = job
    try:
        payload = future.result()
    except Exception:
        with conn, conn.cursor() as cursor:
            cursor.execute(
                """UPDATE squitv.catalog_metadata SET failures=failures+1,priority=false,
                   next_attempt_at=now()+make_interval(mins => least(1440,5*(1 << least(failures,8))))
                   WHERE (account_id,link_id,kind,item_id)=(%s,%s,%s,%s)""", (account, link, kind, item),
            )
        return
    with conn, conn.cursor() as cursor:
        if kind == "series" and payload.get("episodes"):
            cursor.execute(
                """SELECT payload FROM squitv.catalog_metadata
                   WHERE (account_id,link_id,kind,item_id)=(%s,%s,%s,%s)
                   AND source_validated_at IS NOT DISTINCT FROM %s""",
                (account, link, kind, item, validated_at),
            )
            row = cursor.fetchone()
            previous = (row[0] or {}) if row else {}
            images = {
                (season, ep["id"]): ep["info"]["movie_image"]
                for season, episodes in previous.get("episodes", {}).items() for ep in episodes
                if ep.get("info", {}).get("movie_image")
            }
            for season, episodes in payload["episodes"].items():
                for episode in episodes:
                    if image := images.get((season, episode["id"])):
                        episode["info"].setdefault("movie_image", image)
            credits = {source["source"]: source for source in previous.get("external_editorial", [])}
            for source in payload.get("external_editorial", []):
                old = credits.get(source["source"], {})
                credits[source["source"]] = {**source, "info": {**old.get("info", {}), **source["info"]},
                                             "episode_artwork": bool(old.get("episode_artwork") or source.get("episode_artwork"))}
            if credits:
                payload["external_editorial"] = list(credits.values())
        info = payload.get("info")
        if info is not None and not info.get("genre"):
            cursor.execute(
                """SELECT item->>'genre',cat->>'name' FROM squitv.catalog_index c
                   CROSS JOIN LATERAL jsonb_array_elements(c.items) item
                   LEFT JOIN LATERAL jsonb_array_elements(c.categories) cat ON cat->>'id'=item->>'categoryId'
                   WHERE (c.account_id,c.link_id,c.kind)=(%s,%s,%s)
                     AND (item->>'id')::integer=%s
                     AND c.source_validated_at IS NOT DISTINCT FROM %s LIMIT 1""",
                (account, link, kind, item, validated_at),
            )
            if result := cursor.fetchone():
                listing_genre, category_name = result
                if listing_genre:
                    payload["listing_genre"] = listing_genre
                fallback = GENRE_CATEGORIES.get(" ".join((category_name or "").lower().split()))
                if fallback:
                    # A category is only a fallback. The merge retains any genre already saved.
                    payload["category_genre"] = fallback
        cursor.execute(
            """UPDATE squitv.catalog_metadata m SET
                      payload=CASE WHEN m.kind<>'live' AND m.payload IS NOT NULL
                                    AND m.source_validated_at IS NOT DISTINCT FROM l.validated_at
                              THEN m.payload || incoming.data || jsonb_build_object('info',
                                   (m.payload->'info') || (incoming.data->'info'))
                              ELSE incoming.data END,
                      updated_at=now(),source_validated_at=%s,
                      revision=nextval('squitv.catalog_metadata_revision'),
                      failures=CASE WHEN incoming.data @> '{"episodes_pending":true}' THEN m.failures+1 ELSE 0 END,
                      priority=false,next_attempt_at=CASE WHEN incoming.data @> '{"episodes_pending":true}'
                        THEN now()+make_interval(mins => least(1440,5*(1 << least(m.failures,8))))
                        ELSE now()+%s::interval END
               FROM squitv.provider_links l CROSS JOIN (SELECT %s::jsonb AS data) incoming
               WHERE (l.account_id,l.id)=(m.account_id,m.link_id)
                 AND (m.account_id,m.link_id,m.kind,m.item_id)=(%s,%s,%s,%s)
                 AND l.validated_at IS NOT DISTINCT FROM %s AND l.encrypted_credentials IS NOT NULL
                 AND (l.provider_expires_at IS NULL OR l.provider_expires_at>now())
                 AND (m.kind<>'series' OR m.payload IS NULL OR m.source_validated_at IS DISTINCT FROM l.validated_at
                      OR NOT EXISTS (
                        SELECT episode->>'id' FROM jsonb_each(m.payload->'episodes') AS season
                        CROSS JOIN LATERAL jsonb_array_elements(season.value) AS episode
                        EXCEPT
                        SELECT episode->>'id' FROM jsonb_each(incoming.data->'episodes') AS season
                        CROSS JOIN LATERAL jsonb_array_elements(season.value) AS episode
                      ))""",
            (validated_at, "10 minutes" if kind == "live" else "7 days", Json(payload),
             account, link, kind, item, validated_at),
        )
        if cursor.rowcount == 0:
            cursor.execute(
                """UPDATE squitv.catalog_metadata SET priority=false,next_attempt_at=now()+interval '6 hours'
                   WHERE (account_id,link_id,kind,item_id)=(%s,%s,%s,%s)""",
                (account, link, kind, item),
            )


def main():
    with closing(connection()) as conn:
        with conn, conn.cursor() as cursor:
            cursor.execute("SELECT pg_try_advisory_lock(%s)", (934188622,))
            if not cursor.fetchone()[0]:
                return
        seed_jobs(conn)
        deadline = time.monotonic() + 35
        with ThreadPoolExecutor(max_workers=5) as pool:
            while time.monotonic() < deadline:
                jobs = next_jobs(conn)
                if not jobs:
                    return
                for job, result in zip(jobs, [pool.submit(fetch, job) for job in jobs]):
                    finish(conn, job, result)
                time.sleep(1)


if __name__ == "__main__":
    main()
