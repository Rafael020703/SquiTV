"""Read the prepared catalog using the existing account/link authorization."""

from iptv import ApiError
from account_sync import MAX_REVISION, query_value


def editorial_info(info, sources):
    placeholders = {"-", "n/a", "null", "none", "sem sinopse", "sem gênero", "não informado"}
    merged = {key: value for key, value in (info or {}).items()
              if value and (not isinstance(value, str) or value.strip().casefold().rstrip(".") not in placeholders)}
    credits = []
    ranked = {"TVmaze": 0, "Wikidata": 1}
    for source in sorted(sources or [], key=lambda entry: ranked.get(entry["source"], 2)):
        values = {key: value for key, value in source["info"].items() if value and not merged.get(key)}
        if values or source.get("episode_artwork"):
            merged.update(values)
            credits.append({key: source[key] for key in ("source", "url", "license")})
    if credits:
        merged["editorialCredits"] = credits
    return merged


def web_artwork(*candidates):
    for url in candidates:
        if isinstance(url, str) and url.startswith("https://"):
            return url
        if isinstance(url, str) and url.startswith("http://image.tmdb.org/"):
            return "https://" + url[7:]
    return None


def number(environ, name, limit, default=None):
    value = query_value(environ, name, default)
    if not isinstance(value, str) or not value.isascii() or not value.isdecimal() or len(value) > 16:
        raise ApiError(400, "Parâmetro do catálogo inválido.")
    result = int(value)
    if result > limit:
        raise ApiError(400, "Parâmetro do catálogo inválido.")
    return result


def read_catalog(cursor, account, link, environ):
    kind = query_value(environ, "kind")
    if kind not in ("live", "movie", "series"):
        raise ApiError(400, "Catálogo inválido.")
    view = query_value(environ, "view", "status")
    cursor.execute(
        """SELECT c.revision,jsonb_array_length(c.items),c.refreshed_at
           FROM squitv.catalog_index c JOIN squitv.provider_links l
              ON (l.account_id,l.id)=(c.account_id,c.link_id)
           WHERE (c.account_id,c.link_id,c.kind)=(%s,%s,%s) AND c.revision>0
             AND c.source_validated_at IS NOT DISTINCT FROM l.validated_at
             AND l.encrypted_credentials IS NOT NULL
             AND (l.provider_expires_at IS NULL OR l.provider_expires_at>now())""",
        (account, link, kind),
    )
    indexed = cursor.fetchone()
    if indexed is None:
        return {"available": False}
    revision, total, updated = indexed
    scope = account, link, kind
    if view == "browse":
        offset = number(environ, "offset", 150_000, "0")
        requested_revision = number(environ, "revision", MAX_REVISION, str(revision))
        if offset and requested_revision != revision:
            return {"available": True, "changed": True}
        query = query_value(environ, "q", "").strip()
        if len(query) > 100:
            raise ApiError(400, "Busca do catálogo muito longa.")
        cursor.execute(
            """SELECT page.item,m.payload->'info',m.payload->>'metadata_logo',m.payload->'external_editorial',
                      m.payload->>'category_genre',m.payload->>'listing_genre'
               FROM squitv.catalog_index c
               CROSS JOIN LATERAL (
                 SELECT item FROM jsonb_array_elements(c.items) WITH ORDINALITY AS rows(item, position)
                 WHERE strpos(lower(item->>'name'),lower(%s))>0 OR %s=''
                 ORDER BY position LIMIT 25 OFFSET %s
               ) page
               LEFT JOIN squitv.catalog_metadata m ON (m.account_id,m.link_id,m.kind)=(c.account_id,c.link_id,c.kind)
                 AND m.item_id=(page.item->>'id')::integer
                 AND m.source_validated_at IS NOT DISTINCT FROM c.source_validated_at
               WHERE (c.account_id,c.link_id,c.kind)=(%s,%s,%s) AND c.revision=%s""",
            (query, query, offset, *scope, revision),
        )
        rows = cursor.fetchall()
        items = []
        for base, detail, _, sources, fallback, listing_genre in rows[:24]:
            detail = editorial_info({**{key: base.get(key) for key in ("plot", "genre", "releaseDate")},
                                     **editorial_info({"genre": listing_genre or base.get("genre"), **(detail or {})}, None)}, sources)
            backdrops = detail.get("backdrop_path") or []
            items.append({
                "id": base["id"], "name": base["name"], "genre": detail.get("genre") or base.get("genre") or fallback,
                "releaseDate": detail.get("releaseDate") or base.get("releaseDate"),
                "plot": (detail.get("plot") or base.get("plot") or "")[:640],
                "poster": web_artwork(base.get("poster"), detail.get("cover"), detail.get("movie_image")),
                "backdrop": web_artwork(backdrops[0] if backdrops else None, base.get("backdrop")),
            })
        return {"available": True, "revision": revision, "items": items, "next": offset + len(items),
                "hasMore": len(rows)>24, "tmdb": next((row[2] for row in rows[:24] if row[2]), None)}
    if view == "status":
        cursor.execute(
            """SELECT count(*) FILTER (WHERE m.payload IS NOT NULL
                        AND NOT m.payload @> '{"episodes_pending":true}'
                        AND m.source_validated_at IS NOT DISTINCT FROM c.source_validated_at),
                      count(*) FILTER (WHERE m.failures>0)
               FROM squitv.catalog_metadata m JOIN squitv.catalog_index c USING (account_id,link_id,kind)
               WHERE (m.account_id,m.link_id,m.kind)=(%s,%s,%s)""", scope,
        )
        prepared, failed = cursor.fetchone()
        return {"available": True, "revision": revision, "total": total, "prepared": min(total, prepared),
                "failed": min(total, failed), "indexedAt": updated.isoformat()}
    if view == "genres" and kind != "live":
        after = number(environ, "after", MAX_REVISION, "0")
        cursor.execute(
            """SELECT m.item_id,m.payload->'info'->>'genre',m.revision,m.payload->'external_editorial',
                      m.payload->>'category_genre',m.payload->>'listing_genre'
               FROM squitv.catalog_metadata m JOIN squitv.catalog_index c USING (account_id,link_id,kind)
               WHERE (m.account_id,m.link_id,m.kind)=(%s,%s,%s) AND m.revision>%s AND m.payload IS NOT NULL
                 AND m.source_validated_at IS NOT DISTINCT FROM c.source_validated_at
               ORDER BY m.revision LIMIT 101""", (*scope, after),
        )
        rows = cursor.fetchall()
        return {"available": True, "items": [{"id": item_id, "genre": editorial_info(
                    {"genre": value}, None).get("genre") or listing or editorial_info({}, sources).get("genre") or fallback}
                    for item_id, value, _, sources, fallback, listing in rows[:100]],
                "next": rows[min(len(rows), 100)-1][2] if rows else after, "hasMore": len(rows)>100}
    if view == "detail":
        item_id = number(environ, "id", 2**31 - 1)
        cursor.execute(
            """SELECT m.payload FROM squitv.catalog_metadata m
               JOIN squitv.catalog_index c USING (account_id,link_id,kind)
               WHERE (m.account_id,m.link_id,m.kind,m.item_id)=(%s,%s,%s,%s)
                 AND m.source_validated_at IS NOT DISTINCT FROM c.source_validated_at
                 AND NOT m.payload @> '{"episodes_pending":true}'
                 AND (m.kind<>'live' OR m.updated_at>now()-interval '10 minutes')
                 AND c.items @> jsonb_build_array(jsonb_build_object('id',%s::integer))""",
            (*scope, item_id, item_id),
        )
        row = cursor.fetchone()
        if row and row[0] is not None:
            payload = row[0]
            if kind == "series" and not payload.get("episode_artwork_version"):
                cursor.execute(
                    """UPDATE squitv.catalog_metadata SET priority=true,next_attempt_at=now()
                       WHERE (account_id,link_id,kind,item_id)=(%s,%s,%s,%s)
                         AND NOT priority AND failures=0 AND NOT (payload ? 'episode_artwork_version')""",
                    (*scope, item_id),
                )
            sources = payload.pop("external_editorial", None)
            listing = payload.pop("listing_genre", None)
            if kind != "live":
                info = editorial_info(payload.get("info"), None)
                if listing and not info.get("genre"):
                    info["genre"] = listing
                payload["info"] = editorial_info(info, sources)
            fallback = payload.pop("category_genre", None)
            if fallback and not payload.get("info", {}).get("genre"):
                payload["info"] = {**(payload.get("info") or {}), "genre": fallback}
            return {"available": True, "data": payload}
        cursor.execute(
            """INSERT INTO squitv.catalog_metadata (account_id,link_id,kind,item_id,priority)
               SELECT account_id,link_id,kind,%s,true FROM squitv.catalog_index
               WHERE (account_id,link_id,kind)=(%s,%s,%s)
                 AND items @> jsonb_build_array(jsonb_build_object('id',%s::integer))
               ON CONFLICT (account_id,link_id,kind,item_id) DO UPDATE SET priority=true
               WHERE NOT catalog_metadata.priority AND catalog_metadata.next_attempt_at<=now()""",
            (item_id, *scope, item_id),
        )
        return {"available": True}
    raise ApiError(400, "Consulta do catálogo inválida.")
