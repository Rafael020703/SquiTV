"""Optional TMDB fallback for missing editorial fields in linked movie/TV metadata."""

import os
import re
import unicodedata
from pathlib import Path
from threading import Lock
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlsplit

from catalog_index import text
from iptv import get_json

API = "https://api.themoviedb.org/3/"
IMAGE = "https://image.tmdb.org/t/p/"
_lock = Lock()
_suspended = False


def settings():
    directory = os.environ.get("CREDENTIALS_DIRECTORY")
    logo = os.environ.get("SQUITV_TMDB_LOGO_URL", "")
    if not directory or not logo:
        return None
    try:
        approved = urlsplit(logo)
        if (approved.scheme != "https" or approved.hostname not in ("www.themoviedb.org", "image.tmdb.org")
                or approved.username or approved.password or approved.query or approved.fragment):
            return None
        token = (Path(directory) / "tmdb-token").read_text(encoding="ascii").strip()
    except (OSError, UnicodeError, ValueError):
        return None
    return (token, logo) if 30 <= len(token) <= 2048 and not any(char.isspace() for char in token) else None


def normalized_title(value):
    folded = unicodedata.normalize("NFKD", value.casefold())
    return " ".join("".join(char if char.isalnum() else " " for char in folded
                            if not unicodedata.combining(char)).split())


def matching_id(rows, title, year, media):
    name_key, original_key, date_key = (
        ("title", "original_title", "release_date") if media == "movie" else ("name", "original_name", "first_air_date")
    )
    matches = set()
    key = normalized_title(title)
    if not key:
        return None
    for row in rows[:50]:
        if not isinstance(row, dict) or row.get("adult") is True:
            continue
        names = {normalized_title(value) for field in (name_key, original_key)
                 if isinstance((value := row.get(field)), str)}
        identifier = row.get("id")
        if key in names and str(row.get(date_key, ""))[:4] == year and type(identifier) is int and identifier > 0:
            matches.add(identifier)
    return next(iter(matches)) if len(matches) == 1 else None


def image(path, size):
    if isinstance(path, str) and re.fullmatch(r"/[A-Za-z0-9_-]+\.(jpg|jpeg|png|webp)", path):
        return IMAGE + size + path
    return None


def enrich(payload, raw, kind):
    global _suspended
    config = settings()
    info = payload.get("info", {})
    poster_key = "movie_image" if kind == "movie" else "cover"
    if not config or all(info.get(key) for key in ("plot", "genre", poster_key, "backdrop_path")):
        return payload
    token, logo = config
    raw_info = raw.get("info") or {}
    raw_movie = raw.get("movie_data")
    if not isinstance(raw_movie, dict):
        raw_movie = {}
    media = "movie" if kind == "movie" else "tv"
    movie_data = payload.get("movie_data") or {}
    title = info.get("name") or movie_data.get("name") or text(raw_info.get("title"), 512)
    suffix = re.search(r"\s*\((\d{4})\)\s*$", title)
    year = str(info.get("releaseDate", "") or raw_info.get("year", ""))[:4]
    if not re.fullmatch(r"\d{4}", year) and suffix:
        year = suffix[1]
    if suffix:
        title = title[:suffix.start()]
    supplied = raw_info.get("tmdb_id") or raw_movie.get("tmdb_id")
    tmdb_id = int(supplied) if isinstance(supplied, (str, int)) and re.fullmatch(r"[1-9][0-9]{0,8}", str(supplied)) else None
    imdb = next((value for source in (raw_info, raw_movie) for key in ("imdb_id", "imdb")
                 if isinstance((value := source.get(key)), str) and re.fullmatch(r"tt[0-9]{5,12}", value)), None)
    if tmdb_id is None and imdb is None and (not title or not re.fullmatch(r"\d{4}", year)):
        return payload
    headers = {"Authorization": "Bearer " + token, "Accept": "application/json"}
    with _lock:
        if _suspended:
            return payload
        try:
            if tmdb_id is None and imdb is not None:
                result = get_json(API + f"find/{imdb}?external_source=imdb_id&language=pt-BR",
                                  headers, 3, limit=256 * 1024)
                matches = result.get("movie_results" if media == "movie" else "tv_results")
                ids = {row["id"] for row in matches if isinstance(row, dict) and row.get("adult") is not True
                       and type(row.get("id")) is int and row["id"] > 0} if isinstance(matches, list) else set()
                tmdb_id = next(iter(ids)) if len(ids) == 1 else None
            if tmdb_id is None:
                if not title or not re.fullmatch(r"\d{4}", year):
                    return payload
                search = urlencode({"query": title, "language": "pt-BR", "include_adult": "false",
                                    "year" if media == "movie" else "first_air_date_year": year})
                result = get_json(API + "search/" + media + "?" + search, headers, 3, limit=256 * 1024)
                rows = result.get("results")
                tmdb_id = matching_id(rows, title, year, media) if isinstance(rows, list) else None
                if tmdb_id is None:
                    return payload
            result = get_json(f"{API}{media}/{tmdb_id}?language=pt-BR", headers, 3, limit=256 * 1024)
        except HTTPError as failure:
            with failure:
                if failure.code in (401, 403, 429):
                    _suspended = True
            return payload
        except (URLError, OSError, ValueError):
            return payload
    if result.get("adult") is True:
        return payload
    genres = result.get("genres")
    genres = ", ".join(text(row.get("name"), 80) for row in genres[:12] if isinstance(row, dict)) if isinstance(genres, list) else ""
    release = result.get("release_date" if media == "movie" else "first_air_date")
    runtime = result.get("runtime") if media == "movie" else None
    duration = f"{runtime // 60:02d}:{runtime % 60:02d}:00" if type(runtime) is int and runtime > 0 else None
    values = {"plot": text(result.get("overview"), 4096), "genre": genres,
              "releaseDate": text(release, 32), "duration": duration,
              poster_key: image(result.get("poster_path"), "w342"),
              "backdrop_path": [banner] if (banner := image(result.get("backdrop_path"), "w780")) else None}
    missing = {key: value for key, value in values.items() if value and not info.get(key)}
    if missing:
        payload["info"] = {**info, **missing}
        payload["metadata_source"] = "tmdb"
        payload["metadata_logo"] = logo
    return payload
