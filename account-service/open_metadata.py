"""Editorial metadata from public sources, kept separate from provider data."""

import html
import re
from threading import Lock
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlsplit

from catalog_index import text
from iptv import get_json
from tmdb_metadata import normalized_title

HEADERS = {"User-Agent": "SquiTV/1.0 (https://squitv.rafael020703.shop)", "Accept": "application/json"}
WIKIDATA = "https://query.wikidata.org/sparql"
_lock = Lock()
_remaining = 8
_suspended = set()
# Confirmed localized titles for records supplied without an IMDb ID or year.
SERIES_IMDB_ALIASES = {"eu sou groot": "tt13623148", "i am groot": "tt13623148"}


def lookup(source, url, headers=HEADERS, expected=dict):
    global _remaining
    with _lock:
        if _remaining <= 0 or source in _suspended:
            return None
        _remaining -= 1
        try:
            return get_json(url, headers, 3, expected=expected, limit=128 * 1024)
        except HTTPError as error:
            with error:
                if error.code in (403, 429, 503):
                    _suspended.add(source)
                if source == "tvmaze" and error.code in (301, 302, 307, 308):
                    target = error.headers.get("Location", "")
                    if re.fullmatch(r"https://api\.tvmaze\.com/shows/[1-9][0-9]*", target):
                        try:
                            return get_json(target, headers, 3, limit=128 * 1024)
                        except (URLError, OSError, ValueError):
                            return None
        except (URLError, OSError, ValueError):
            return None
    return None


def identifiers(raw):
    info = raw.get("info")
    movie = raw.get("movie_data")
    fields = [value for value in (info, movie) if isinstance(value, dict)]
    imdb = next((str(value) for row in fields for key in ("imdb_id", "imdb")
                 if re.fullmatch(r"tt[0-9]{5,12}", str(value := row.get(key)))), None)
    tmdb = next((str(value) for row in fields
                 if re.fullmatch(r"[1-9][0-9]{0,8}", str(value := row.get("tmdb_id")))), None)
    return imdb, tmdb


def tvmaze(imdb, info):
    if imdb:
        data = lookup("tvmaze", "https://api.tvmaze.com/lookup/shows?" + urlencode({"imdb": imdb}))
        if not data or (data.get("externals") or {}).get("imdb") != imdb:
            return None
    else:
        title = info.get("name", "")
        suffix = re.search(r"\s*\((\d{4})\)\s*$", title)
        year = info.get("releaseDate", "")[:4]
        if suffix:
            year = year or suffix[1]
            title = title[:suffix.start()]
        if not title or not re.fullmatch(r"\d{4}", year):
            return None
        rows = lookup("tvmaze", "https://api.tvmaze.com/search/shows?" + urlencode({"q": title}), expected=list)
        shows = [row.get("show") for row in rows or [] if isinstance(row, dict)]
        matches = {show["id"]: show for show in shows if isinstance(show, dict) and type(show.get("id")) is int
                   and normalized_title(str(show.get("name") or "")) == normalized_title(title)
                   and str(show.get("premiered") or "")[:4] == year}
        data = next(iter(matches.values())) if len(matches) == 1 else None
    if not data:
        return None
    show = data.get("id")
    if type(show) is not int or show <= 0:
        return None
    summary = text(html.unescape(re.sub(r"<[^>]*>", " ", str(data.get("summary") or ""))), 4096)
    genres = data.get("genres")
    cover = tvmaze_image(data)
    genres = [text(item, 80) for item in genres[:12] if isinstance(item, str)] if isinstance(genres, list) else []
    if data.get("type") == "Animation":
        genres.append("Animação")
    return {"source": "TVmaze", "url": f"https://www.tvmaze.com/shows/{show}",
            "license": "https://creativecommons.org/licenses/by-sa/4.0/",
            "info": {"plot": summary, "cover": cover,
                     "genre": ", ".join(filter(None, genres)),
                     "releaseDate": text(data.get("premiered"), 32)}}


def tvmaze_image(data):
    image = data.get("image")
    url = image.get("medium") if isinstance(image, dict) else None
    if isinstance(url, str):
        try:
            parsed = urlsplit(url)
            if parsed.scheme == "https" and parsed.netloc == "static.tvmaze.com" and not parsed.query and not parsed.fragment:
                return url
        except ValueError:
            pass
    return ""


def fill_episode_images(payload, source):
    groups = payload.get("episodes", {})
    missing = [(season, episode) for season, episodes in groups.items() for episode in episodes
               if not episode.get("info", {}).get("movie_image")]
    if not missing:
        return False
    show_id = source["url"].rsplit("/", 1)[-1]
    episodes = lookup("tvmaze", f"https://api.tvmaze.com/shows/{show_id}/episodes", expected=list)
    if not episodes:
        return False
    indexed = {}
    for episode in episodes:
        if not isinstance(episode, dict):
            continue
        key = str(episode.get("season")), str(episode.get("number"))
        indexed.setdefault(key, []).append(episode)
    updated = False
    for season, episode in missing:
        matches = indexed.get((str(int(season)), str(episode.get("episode_num"))), [])
        if len(matches) != 1:
            continue
        match = matches[0]
        date = episode.get("info", {}).get("release_date", "")[:10]
        if re.fullmatch(r"\d{4}-\d{2}-\d{2}", date) and match.get("airdate") and date != match["airdate"]:
            continue
        if image := tvmaze_image(match):
            episode.setdefault("info", {})["movie_image"] = image
            updated = True
    return updated


def wikidata(imdb, tmdb, kind):
    prop, identifier = ("P345", imdb) if imdb else ("P4947" if kind == "movie" else "P4983", tmdb)
    if not identifier:
        return None
    if not re.fullmatch(r"tt[0-9]{5,12}" if imdb else r"[1-9][0-9]{0,8}", identifier):
        return None
    headers = {**HEADERS, "Accept": "application/sparql-results+json"}
    query = f'SELECT DISTINCT ?item WHERE {{ ?item wdt:{prop} "{identifier}" }} LIMIT 2'
    data = lookup("wikidata", WIKIDATA + "?" + urlencode({"query": query}), headers)
    rows = (data.get("results") or {}).get("bindings", []) if data else []
    entities = {row.get("item", {}).get("value", "") for row in rows}
    if len(entities) != 1:
        return None
    entity = entities.pop().rsplit("/", 1)[-1]
    if not re.fullmatch(r"Q[1-9][0-9]*", entity):
        return None
    query = f'''SELECT ?property ?value ?valueLabel WHERE {{
      VALUES ?property {{ wdt:P577 wdt:P136 wdt:P57 wdt:P161 }}
      wd:{entity} ?property ?value .
      SERVICE wikibase:label {{ bd:serviceParam wikibase:language "pt-br,pt,en". }}
    }} LIMIT 100'''
    data = lookup("wikidata", WIKIDATA + "?" + urlencode({"query": query}), headers)
    rows = (data.get("results") or {}).get("bindings", []) if data else []
    names = {"P136": "genre", "P57": "director", "P161": "cast", "P577": "releaseDate"}
    fields = {"genre": [], "director": [], "cast": [], "releaseDate": []}
    for row in rows:
        field = names.get(row.get("property", {}).get("value", "").rsplit("/", 1)[-1])
        value = text(row.get("valueLabel" if field != "releaseDate" else "value", {}).get("value"), 120)
        if field not in fields or not value or re.fullmatch(r"Q[0-9]+", value):
            continue
        if field == "releaseDate":
            if not re.match(r"^[0-9]{4}-[0-9]{2}-[0-9]{2}", value):
                continue
            value = value[:10]
        if value not in fields[field]:
            fields[field].append(value)
    return {"source": "Wikidata", "url": f"https://www.wikidata.org/wiki/{entity}",
            "license": "https://creativecommons.org/publicdomain/zero/1.0/",
            "info": {key: (min(values) if key == "releaseDate" else ", ".join(values[:8]))
                     for key, values in fields.items() if values}}


def enrich_open(payload, raw, kind):
    info = payload.get("info", {})
    missing = {field for field in ("plot", "genre", "cover", "releaseDate", "director", "cast")
               if not info.get(field)}
    imdb, tmdb = identifiers(raw)
    if kind == "series" and not imdb:
        imdb = SERIES_IMDB_ALIASES.get(normalized_title(info.get("name", "")))
    needs_previews = kind == "series" and any(
        not episode.get("info", {}).get("movie_image") for rows in payload.get("episodes", {}).values() for episode in rows
    )
    if not missing and not needs_previews:
        return payload
    sources = []
    if kind == "series" and (needs_previews or missing & {"plot", "genre", "cover", "releaseDate"}):
        source = tvmaze(imdb, info)
        if source:
            source["info"] = {key: value for key, value in source["info"].items() if key in missing and value}
            preview_credit = needs_previews and fill_episode_images(payload, source)
            if source["info"] or preview_credit:
                source["episode_artwork"] = preview_credit
                sources.append(source)
                missing -= source["info"].keys()
    if missing & {"genre", "releaseDate", "director", "cast"}:
        source = wikidata(imdb, tmdb, kind)
        if source:
            source["info"] = {key: value for key, value in source["info"].items() if key in missing and value}
            if source["info"]:
                sources.append(source)
    if sources:
        payload["external_editorial"] = sources
    return payload
