"""
lib/metadata.py — Pengambil metadata multi-sumber ŘΨØŬ v1.0.1
Urutan:
  1. Jikan v4 (MAL)   — anime
  2. MDL Scraper      — drama Asia
  3. TMDB             — film/series umum
  4. Undefined        — fallback
"""

import time, re, json, urllib.request, urllib.parse
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))
import config
import lib.cache as cache

# ─── HTTP HELPER ─────────────────────────────────────────────────────────────

_last_req: dict = {}


def _http(url: str, host: str = "default", delay: float = 0.35,
          headers: dict = None) -> dict | None:
    elapsed = time.time() - _last_req.get(host, 0)
    if elapsed < delay:
        time.sleep(delay - elapsed)
    try:
        hdrs = {"User-Agent": "RYOU-MediaServer/1.0", "Accept": "application/json"}
        if headers: hdrs.update(headers)
        req = urllib.request.Request(url, headers=hdrs)
        with urllib.request.urlopen(req, timeout=12) as r:
            _last_req[host] = time.time()
            return json.loads(r.read().decode("utf-8", errors="replace"))
    except urllib.error.HTTPError as e:
        if e.code in (401, 403): print(f"[Meta] {host} auth error HTTP {e.code}")
        elif e.code == 429:      print(f"[Meta] {host} rate limit, skip")
        _last_req[host] = time.time()
        return None
    except Exception:
        _last_req[host] = time.time()
        return None


# ─── CONFIDENCE SCORER ───────────────────────────────────────────────────────

CONFIDENCE_THRESHOLD     = 0.55
MDL_CONFIDENCE_THRESHOLD = 0.40


def _confidence(query: str, candidates: list) -> float:
    q = query.lower().strip()
    qw = set(q.split())
    best = 0.0
    for t in candidates:
        if not t: continue
        t = t.lower().strip()
        if q == t: return 1.0
        if q in t or t in q:
            ratio = len(min(q, t, key=len)) / len(max(q, t, key=len))
            best = max(best, 0.75 + ratio * 0.2); continue
        tw = set(t.split())
        if not qw: continue
        wr = len(qw & tw) / len(qw)
        if wr >= 0.6: best = max(best, wr * 0.7)
    return best


# ═══════════════════════════════════════════════════════════════════════════════
#  JIKAN (MyAnimeList)
# ═══════════════════════════════════════════════════════════════════════════════

def _jikan_by_id(mal_id: int, n: int) -> dict | None:
    resp = _http(f"{config.JIKAN_BASE}/anime/{mal_id}", "jikan", config.JIKAN_DELAY)
    if not resp or not resp.get("data"): return None
    return _parse_jikan(resp["data"], n)


def _jikan_search(query: str, mtype: str, year: str, n: int) -> dict | None:
    params = {"q": query, "limit": 8, "sfw": "false"}
    if mtype == "Movie": params["type"] = "movie"
    url  = f"{config.JIKAN_BASE}/anime?" + urllib.parse.urlencode(params)
    resp = _http(url, "jikan", config.JIKAN_DELAY)
    if not resp or not resp.get("data"): return None
    best = _pick_jikan(resp["data"], query, year)
    return _parse_jikan(best, n) if best else None


def _pick_jikan(items: list, query: str, year: str) -> dict | None:
    scored = []
    for c in items:
        titles = [c.get("title") or "", c.get("title_english") or "",
                  c.get("title_japanese") or ""] + [s.get("title") or "" for s in c.get("titles", [])]
        conf = _confidence(query, titles)
        if conf < CONFIDENCE_THRESHOLD: continue
        sc = conf * 100
        if year:
            cy = str(c.get("year") or "")
            af = (c.get("aired") or {}).get("from") or ""
            if year in cy or year in af: sc += 20
        if c.get("score"): sc += min(float(c["score"]), 10) * 0.3
        scored.append((sc, conf, c))
    if not scored: return None
    scored.sort(key=lambda x: x[0], reverse=True)
    _, cf, best = scored[0]
    print(f"[Meta]   Jikan cf={cf:.2f} → {best.get('title_english') or best.get('title')}")
    return best


def _fmt_date(iso: str) -> str:
    if not iso: return ""
    m = re.match(r"(\d{4})-(\d{2})-(\d{2})", iso)
    if not m: return iso[:10]
    mo = ["","Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"]
    return f"{mo[int(m.group(2))]} {int(m.group(3))}, {m.group(1)}"


def _parse_jikan(a: dict, n: int) -> dict:
    imgs   = a.get("images", {})
    poster = (imgs.get("webp", {}).get("large_image_url")
              or imgs.get("jpg", {}).get("large_image_url")
              or imgs.get("jpg", {}).get("image_url") or "")
    genres = [g["name"] for g in a.get("genres", [])]
    themes = [t["name"] for t in a.get("themes", [])]
    demo   = [d["name"] for d in a.get("demographics", [])]
    raw_t  = a.get("type", "TV")
    mtype  = "Movie" if raw_t == "Movie" else (raw_t or "TV")
    score  = a.get("score")
    rating = round(float(score), 1) if score else 0.0
    status_raw = (a.get("status") or "").lower()
    status = "Ongoing" if (a.get("airing") or "currently airing" in status_raw or "not yet aired" in status_raw) else "Completed"
    aired      = a.get("aired", {})
    aired_from = aired.get("from") or ""
    aired_to   = aired.get("to")   or ""
    year       = None
    if aired_from:
        ym = re.search(r"(\d{4})", aired_from)
        if ym: year = ym.group(1)
    if not year and a.get("year"): year = str(a["year"])
    aired_str = _fmt_date(aired_from)
    if aired_to: aired_str += " to " + _fmt_date(aired_to)
    studios   = a.get("studios", [])
    synopsis  = re.sub(r"\[Written by MAL.*?\]", "", a.get("synopsis") or "").strip()
    synopsis  = synopsis.replace("\n", "<br>")
    return {
        "source"      : "mal",
        "mal_id"      : a.get("mal_id"),
        "title"       : a.get("title_english") or a.get("title") or "",
        "title_en"    : a.get("title_english") or a.get("title") or "",
        "title_romaji": a.get("title") or "",
        "title_ja"    : a.get("title_japanese") or a.get("title") or "",
        "title_jp"    : a.get("title") or "",
        "type"        : mtype, "year": year or "—",
        "episodes"    : a.get("episodes") or n or 0,
        "rating"      : rating, "poster": poster, "banner": poster,
        "genres"      : list(dict.fromkeys(genres + themes)),
        "genres_disp" : list(dict.fromkeys(genres)),
        "themes"      : themes, "demographics": demo,
        "description" : synopsis, "description_id": "", "description_ja": "",
        "title_synonyms": [s for s in (a.get("title_synonyms") or []) if s],
        "status"      : status, "studio": studios[0]["name"] if studios else "",
        "studios"     : [s["name"] for s in studios],
        "trailer"     : (a.get("trailer") or {}).get("url") or "",
        "aired"       : aired_str or "—",
        "aired_end"   : _fmt_date(aired_to) if aired_to else "",
    }


def fetch_episode_titles(mal_id: int) -> dict:
    if not mal_id: return {}
    ck = f"eps:{mal_id}"
    cached = cache.get(ck)
    if cached: return cached

    ep_titles, page, total = {}, 1, 0
    rate_limited = False

    while True:
        url  = f"{config.JIKAN_BASE}/anime/{mal_id}/episodes?page={page}"
        resp = _http(url, "jikan", config.JIKAN_DELAY)

        # Retry sekali jika gagal
        if resp is None:
            if rate_limited:
                print(f"[Meta] Jikan ep titles: rate limit persisten, skip MAL #{mal_id}")
                break
            print(f"[Meta] Jikan ep page {page} gagal, retry 3s…")
            time.sleep(3)
            resp = _http(url, "jikan", config.JIKAN_DELAY)
            if resp is None:
                rate_limited = True
                break

        if not resp or not resp.get("data"): break

        off = (page - 1) * 100
        for idx, ep in enumerate(resp["data"], 1):
            num = ep.get("mal_id")
            if num is None: num = off + idx
            try: num = int(num)
            except: num = off + idx
            en  = (ep.get("title") or "").strip()
            rom = (ep.get("title_romanji") or "").strip()
            jp  = (ep.get("title_japanese") or "").strip()
            # Simpan judul jika ada, skip "Episode N" agar bisa diisi fallback nanti
            title = en or rom or jp
            if title:
                ep_titles[num] = title
            total += 1

        if not resp.get("pagination", {}).get("has_next_page"): break
        page += 1
        if page > 15: break

    # Cache hanya jika dapat data — biarkan kosong agar rescan bisa coba ulang
    if ep_titles:
        cache.set(ck, ep_titles)
        print(f"[Meta] Jikan ep titles: {len(ep_titles)}/{total} judul (MAL #{mal_id})")
    else:
        print(f"[Meta] Jikan ep titles: tidak ada judul untuk MAL #{mal_id}")
    return ep_titles


def fetch_episode_data_tmdb(tmdb_id: int) -> dict:
    """
    Ambil judul + thumbnail episode dari TMDB dalam satu pass.
    Return: {"titles": {ep_num: title}, "thumbs": {ep_num: still_url}}
    Cache key: eps_tmdb:{tmdb_id}
    """
    if not tmdb_id or not config.TMDB_API_KEY:
        return {"titles": {}, "thumbs": {}}

    ck = f"eps_tmdb:{tmdb_id}"
    cached = cache.get(ck)
    if cached and isinstance(cached, dict) and "titles" in cached:
        return cached

    # Ambil info series untuk jumlah season
    detail = _http(
        f"{config.TMDB_BASE}/tv/{tmdb_id}?api_key={config.TMDB_API_KEY}&language=en-US",
        "tmdb", 0.25
    )
    if not detail:
        return {"titles": {}, "thumbs": {}}

    seasons   = detail.get("seasons") or []
    n_seasons = len([s for s in seasons if s.get("season_number", 0) > 0])
    if n_seasons == 0: n_seasons = 1

    IMG_W780 = config.TMDB_IMG_BASE.replace("w500", "w780")

    titles, thumbs = {}, {}
    ep_counter = 1

    for season_num in range(1, min(n_seasons + 1, 6)):
        url = (f"{config.TMDB_BASE}/tv/{tmdb_id}/season/{season_num}"
               f"?api_key={config.TMDB_API_KEY}&language=en-US")
        resp = _http(url, "tmdb", 0.25)
        if not resp: continue
        for ep in (resp.get("episodes") or []):
            title = (ep.get("name") or "").strip()
            if title and not re.match(r"^episode\s+\d+$", title, re.IGNORECASE):
                titles[ep_counter] = title
            still = ep.get("still_path") or ""
            if still:
                thumbs[ep_counter] = f"{IMG_W780}{still}"
            ep_counter += 1

    result = {"titles": titles, "thumbs": thumbs}
    if titles or thumbs:
        cache.set(ck, result)
        print(f"[Meta] TMDB ep data: {len(titles)} judul, {len(thumbs)} thumbnail (TMDB #{tmdb_id})")
    return result


def fetch_episode_titles_tmdb(tmdb_id: int) -> dict:
    """Backward-compat wrapper — hanya return titles."""
    return fetch_episode_data_tmdb(tmdb_id).get("titles", {})


# ═══════════════════════════════════════════════════════════════════════════════
#  MDL UNOFFICIAL SCRAPER
# ═══════════════════════════════════════════════════════════════════════════════

def _mdl_variants(query: str) -> list:
    variants = [query]
    clean = re.sub(r"[^\w\s]", " ", query).strip()
    clean = re.sub(r"\s{2,}", " ", clean)
    if clean.lower() != query.lower(): variants.append(clean)
    for sep in (":", " -", "–", "—"):
        if sep in query:
            before = query.split(sep)[0].strip()
            if before.lower() not in [v.lower() for v in variants]: variants.append(before)
            break
    words = query.split()
    if len(words) >= 3:
        two = " ".join(words[:2])
        if two.lower() not in [v.lower() for v in variants]: variants.append(two)
    seen, out = set(), []
    for v in variants:
        v = v.strip()
        if v and v.lower() not in seen:
            seen.add(v.lower()); out.append(v)
    return out


def _mdl_search(query: str, mtype: str, year: str, n: int) -> dict | None:
    for variant in _mdl_variants(query):
        q_enc = urllib.parse.quote(variant, safe="")
        resp  = _http(f"{config.MDL_BASE}/api/search/q/{q_enc}", "mdl", 1.1)
        if not resp: continue
        results = resp.get("results") or (resp if isinstance(resp, list) else [])
        results = [r for r in results if isinstance(r, dict)]
        if not results: continue

        best = _pick_mdl(results, query, year)
        if not best:
            print(f"[Meta]   MDL '{variant}' → {len(results)} hasil, tidak lolos threshold")
            continue

        slug = best.get("slug") or ""
        if slug:
            detail = _http(f"{config.MDL_BASE}/api/id/{slug}", "mdl", 1.1)
            if detail and not (isinstance(detail, dict) and detail.get("error")):
                return _parse_mdl_detail(detail, best, n, mtype)
        return _parse_mdl_minimal(best, n, mtype)
    return None


def _pick_mdl(items: list, query: str, year: str) -> dict | None:
    scored = []
    for item in items:
        if not isinstance(item, dict): continue
        names = [item.get("title") or "", item.get("original_title") or ""]
        conf  = _confidence(query, names)
        if conf < MDL_CONFIDENCE_THRESHOLD: continue
        sc = conf * 100
        if year and str(item.get("year") or "") and year in str(item.get("year") or ""): sc += 20
        try: sc += min(float(item.get("rating") or 0), 10) * 0.3
        except: pass
        scored.append((sc, conf, item))
    if not scored: return None
    scored.sort(key=lambda x: x[0], reverse=True)
    _, cf, best = scored[0]
    print(f"[Meta]   MDL cf={cf:.2f} → {best.get('title')}")
    return best


def _yr(raw) -> str:
    try:
        v = str(int(raw)) if raw and str(raw).strip() not in ("", "None", "—", "null") else ""
        return v if len(v) == 4 else "—"
    except: return "—"


def _parse_mdl_minimal(item: dict, n: int, caller_mtype: str = "TV") -> dict:
    try: rating = round(float(item.get("rating") or 0), 1)
    except: rating = 0.0
    # Deteksi type dari field MDL — bisa "Movie", "Drama", "TV Series", dll
    raw_t = (item.get("type") or item.get("category") or "").lower()
    if any(k in raw_t for k in ("movie", "film", "special")):
        mtype = "Movie"
    elif caller_mtype == "Movie":
        mtype = "Movie"
    else:
        mtype = "TV"
    return {
        "source": "mdl", "mal_id": None, "mdl_slug": item.get("slug") or "",
        "title": item.get("title") or "", "title_en": item.get("title") or "",
        "title_ja": item.get("original_title") or "", "title_jp": item.get("original_title") or "",
        "type": mtype, "year": _yr(item.get("year")),
        "episodes": n or 0, "rating": rating, "poster": item.get("image") or "",
        "genres": [], "description": "", "description_id": "", "description_ja": "",
        "status": "Completed", "studio": "—", "trailer": "", "aired": "—",
    }


def _parse_mdl_detail(d: dict, search_item: dict, n: int, caller_mtype: str = "TV") -> dict:
    def _g(*keys):
        for k in keys:
            v = d.get(k)
            if v is not None and v not in ("", "N/A"): return v
        return None

    poster = _g("poster", "image", "thumbnail") or search_item.get("image") or ""
    title  = _g("title") or search_item.get("title") or ""
    t_orig = _g("original_title", "native_title") or ""
    raw_t  = (_g("type") or _g("category") or "Drama").lower()
    if any(k in raw_t for k in ("movie", "film", "special", "korean movie", "chinese movie", "japanese movie")):
        mtype = "Movie"
    elif caller_mtype == "Movie":
        mtype = "Movie"
    else:
        mtype = "TV"

    _yr_raw  = _g("year") or search_item.get("year")
    year_val = _yr(_yr_raw)
    if year_val == "—":
        for fk in ("aired", "release_date", "air_date"):
            fv = d.get(fk) or ""
            if isinstance(fv, dict): fv = fv.get("start") or fv.get("from") or ""
            ym = re.search(r"(20\d{2}|19\d{2})", str(fv))
            if ym: year_val = ym.group(1); break

    try: rating = round(float(_g("score", "rating") or search_item.get("rating") or 0), 1)
    except: rating = 0.0

    episodes = n or 0
    try:
        ep_raw = _g("episodes", "episode_count")
        if ep_raw: episodes = int(ep_raw)
    except: pass
    if not episodes: episodes = n or 0

    status_raw = (_g("status", "air_status") or "").lower()
    status = "Ongoing" if any(k in status_raw for k in ("ongoing", "airing", "releasing")) else "Completed"

    genres_raw = d.get("genres") or []
    genres = []
    for g in genres_raw:
        if isinstance(g, str): genres.append(g)
        elif isinstance(g, dict): genres.append(g.get("name") or g.get("genre") or "")
    genres = [g for g in genres if g]

    synopsis = _g("synopsis", "description", "plot") or ""
    if isinstance(synopsis, str):
        synopsis = synopsis.replace("\\n", "<br>").replace("\n", "<br>")

    network  = _g("network", "channel", "broadcaster") or ""
    country  = _g("country") or ""
    studio   = network or country or "—"

    aired_raw = _g("aired", "air_date", "release_date") or ""
    if isinstance(aired_raw, dict): aired_raw = aired_raw.get("start") or aired_raw.get("from") or ""
    aired_str = str(aired_raw)[:10] if aired_raw else "—"

    end_raw = _g("end_date", "aired_end", "ended") or ""
    if isinstance(end_raw, dict): end_raw = end_raw.get("end") or end_raw.get("to") or ""
    aired_end = str(end_raw)[:10] if end_raw else ""

    return {
        "source": "mdl", "mal_id": None, "mdl_slug": search_item.get("slug") or "",
        "title": title, "title_en": title,
        "title_ja": t_orig, "title_jp": t_orig,
        "type": mtype, "year": year_val or "—",
        "episodes": episodes, "rating": rating, "poster": poster,
        "banner": _g("banner_url", "cover_wide", "backdrop") or poster,
        "genres": genres, "genres_disp": genres,
        "description": synopsis, "description_id": "", "description_ja": "",
        "status": status, "studio": studio, "network": network, "country": country,
        "trailer": "", "aired": aired_str, "aired_end": aired_end,
    }


# ═══════════════════════════════════════════════════════════════════════════════
#  TMDB
# ═══════════════════════════════════════════════════════════════════════════════

def _tmdb_search(query: str, mtype: str, year: str, n: int) -> dict | None:
    if not config.TMDB_API_KEY: return None
    params = {"api_key": config.TMDB_API_KEY, "query": query,
              "include_adult": "true", "language": "en-US", "page": 1}
    if year: params["year"] = year
    resp = _http(f"{config.TMDB_BASE}/search/multi?" + urllib.parse.urlencode(params), "tmdb", 0.25)
    if not resp or not resp.get("results"): return None

    results = resp["results"]
    if mtype == "Movie": results = [r for r in results if r.get("media_type") == "movie"] or results
    else:                results = [r for r in results if r.get("media_type") == "tv"]    or results
    if not results: return None

    best = _pick_tmdb(results, query, year)
    if not best: return None

    mid   = best["id"]
    mt    = best.get("media_type", "movie")
    base  = f"{config.TMDB_BASE}/{mt}/{mid}?api_key={config.TMDB_API_KEY}"
    detail = _http(f"{base}&language=en-US", "tmdb", 0.25)
    if not detail: return _tmdb_basic(best, n)

    detail_id = _http(f"{base}&language=id-ID", "tmdb", 0.25)
    detail_ja = _http(f"{base}&language=ja-JP", "tmdb", 0.25)
    result = _tmdb_detail(detail, mt, n)
    result["description_id"] = ((detail_id or {}).get("overview") or result["description"]).replace("\n", "<br>")
    result["description_ja"] = ((detail_ja or {}).get("overview") or result["description"]).replace("\n", "<br>")
    return result


def _pick_tmdb(items: list, query: str, year: str) -> dict | None:
    scored = []
    for item in items:
        names = [item.get("title") or "", item.get("name") or "",
                 item.get("original_title") or "", item.get("original_name") or ""]
        conf = _confidence(query, names)
        if conf < CONFIDENCE_THRESHOLD: continue
        sc = conf * 100
        date = item.get("release_date") or item.get("first_air_date") or ""
        if year and year in date: sc += 20
        sc += min(item.get("popularity") or 0, 100) * 0.1
        scored.append((sc, conf, item))
    if not scored: return None
    scored.sort(key=lambda x: x[0], reverse=True)
    _, cf, best = scored[0]
    print(f"[Meta]   TMDB cf={cf:.2f} → {best.get('title') or best.get('name')}")
    return best


def _tmdb_basic(item: dict, n: int) -> dict:
    pp    = item.get("poster_path") or ""
    poster = f"{config.TMDB_IMG_BASE}{pp}" if pp else ""
    title  = item.get("title") or item.get("name") or ""
    date   = item.get("release_date") or item.get("first_air_date") or ""
    mtype  = "Movie" if item.get("media_type") == "movie" else "TV"
    return {
        "source": "tmdb", "mal_id": None, "title": title, "title_en": title,
        "title_ja": item.get("original_title") or item.get("original_name") or "",
        "title_jp": item.get("original_title") or item.get("original_name") or "",
        "type": mtype, "year": date[:4] if date else "—",
        "episodes": n or 0, "rating": round(float(item.get("vote_average") or 0), 1),
        "poster": poster, "genres": [], "description": (item.get("overview") or "").replace("\n", "<br>"),
        "description_id": "", "description_ja": "",
        "status": "Completed", "studio": "—", "trailer": "", "tmdb_id": item.get("id"),
    }


def _tmdb_detail(d: dict, mt: str, n: int) -> dict:
    pp    = d.get("poster_path") or ""
    bp    = d.get("backdrop_path") or ""
    IMG_W = config.TMDB_IMG_BASE.replace("w500", "w1280")
    poster = f"{config.TMDB_IMG_BASE}{pp}" if pp else ""
    banner = f"{IMG_W}{bp}" if bp else poster
    genres = [g["name"] for g in d.get("genres", [])]
    date   = d.get("release_date") or d.get("first_air_date") or ""
    year   = date[:4] if date else "—"
    mtype  = "Movie" if mt == "movie" else "TV"
    sr     = (d.get("status") or "").lower()
    if any(k in sr for k in ("returning", "continuing", "airing", "in production", "planned")): status = "Ongoing"
    elif any(k in sr for k in ("ended", "cancelled", "canceled", "finished", "released")): status = "Completed"
    else: status = "Ongoing" if d.get("in_production") else "Completed"
    companies = d.get("production_companies") or []
    networks  = d.get("networks") or []
    studio_src= networks or companies
    studio    = studio_src[0]["name"] if studio_src else "—"
    trailer   = ""
    for v in (d.get("videos") or {}).get("results") or []:
        if v.get("type") == "Trailer" and v.get("site") == "YouTube":
            trailer = f"https://www.youtube.com/watch?v={v['key']}"; break
    title = d.get("title") or d.get("name") or ""
    return {
        "source": "tmdb", "mal_id": None, "title": title, "title_en": title,
        "title_ja": d.get("original_title") or d.get("original_name") or "",
        "title_jp": d.get("original_title") or d.get("original_name") or "",
        "type": mtype, "year": year,
        "episodes": n or d.get("number_of_episodes") or 0,
        "rating": round(float(d.get("vote_average") or 0), 1),
        "poster": poster, "banner": banner, "genres": genres,
        "description": (d.get("overview") or "").replace("\n", "<br>"),
        "description_id": "", "description_ja": "",
        "status": status, "studio": studio, "trailer": trailer,
        "tmdb_id": d.get("id"),
        "aired": date or "—",
        "aired_end": (d.get("last_air_date") or d.get("end_date") or "")[:10],
    }


# ─── LIVE ACTION & SEASON DETECTION ──────────────────────────────────────────

_LIVE_RE = re.compile(
    r"\b(live[\s._-]?action|drama|dorama|liveaction|jdrama|kdrama|cdrama"
    r"|j-drama|k-drama|c-drama|tokusatsu|reality|variety|documentary|docuseries)\b",
    re.IGNORECASE
)

_SEASON_RE = re.compile(
    r"\b(?:season|s)(\s*)(\d{1,2})\b|\bpart[\s._-]?(\d{1,2})\b"
    r"|\b(\d{1,2})(?:st|nd|rd|th)\s+season\b|\bs(\d{1,2})\b",
    re.IGNORECASE
)


def is_live_action(title: str, raw: str = "") -> bool:
    return bool(_LIVE_RE.search(title + " " + raw))


def extract_season(title: str) -> tuple:
    m = _SEASON_RE.search(title)
    if not m: return title.strip(), None, None
    groups = m.groups()
    num_str, is_part = None, False
    for i, g in enumerate(groups):
        if g is not None and str(g).isdigit():
            num_str = g; is_part = (i == 2); break
    num   = int(num_str) if num_str else None
    clean = (title[:m.start()] + title[m.end():])
    clean = re.sub(r"[\s:,\-]{2,}", " ", clean).strip(" :,-")
    return (clean, None, num) if is_part else (clean, num, None)


# ─── ENTRYPOINT ──────────────────────────────────────────────────────────────

def fetch_metadata(title_clean: str, title_alts: list = None,
                   media_type: str = "TV", year: str = None,
                   num_episodes_local: int = None,
                   raw_title: str = "",
                   mal_id_override: int = None) -> dict:
    """
    Cari metadata dari semua sumber. Selalu return dict (tidak pernah None).
    """
    n = num_episodes_local or 0

    # MAL ID override — fetch langsung
    if mal_id_override:
        print(f"[Meta] MAL ID #{mal_id_override} override")
        direct = _jikan_by_id(mal_id_override, n)
        if direct: print(f"[Meta] OK MAL override: {direct['title']}"); return direct
        print(f"[Meta] MAL ID #{mal_id_override} tidak ditemukan, fallback ke search")

    # Buat daftar query dari title + season info
    base_clean, season_num, part_num = extract_season(title_clean)
    season_sfx = ""
    if season_num and season_num > 1: season_sfx = f" Season {season_num}"
    elif part_num and part_num > 1:   season_sfx = f" Part {part_num}"

    raw_queries = []
    if season_sfx: raw_queries.append(base_clean + season_sfx)
    raw_queries.append(base_clean)
    raw_queries += (title_alts or [])

    seen, queries = set(), []
    for q in raw_queries:
        q = q.strip()
        if q and q.lower() not in seen:
            seen.add(q.lower()); queries.append(q)
    for q in [base_clean] + (title_alts or [])[:1]:
        for alt in (re.sub(r"[^\w\s]", " ", q).strip(), " ".join(q.split()[:2])):
            if alt and alt.lower() not in seen:
                seen.add(alt.lower()); queries.append(alt)

    ck = f"meta:{queries[0].lower()}:{media_type}"
    cached = cache.get(ck)
    if cached:
        print(f"[Meta] Cache hit: {queries[0]}"); return cached

    live_action = is_live_action(title_clean, raw_title)
    print(f"[Meta] Mencari '{queries[0]}' ({media_type}){' [LA]' if live_action else ''}")

    result = None
    for q in queries:
        if result: break
        # Jikan hanya untuk konten anime (bukan live-action)
        if not live_action:
            result = _jikan_search(q, media_type, year, n)
            if result: print(f"[Meta] OK Jikan: {result['title']}"); break
        # MDL hanya untuk konten live-action (drama Asia)
        if live_action and config.MDL_BASE:
            result = _mdl_search(q, media_type, year, n)
            if result: print(f"[Meta] OK MDL: {result['title']}"); break
        # TMDB sebagai fallback untuk semua tipe konten
        if config.TMDB_API_KEY:
            result = _tmdb_search(q, media_type, year, n)
            if result: print(f"[Meta] OK TMDB: {result['title']}"); break

    if not result:
        result = {
            "source": "undefined", "mal_id": None,
            "title": title_clean, "title_jp": "",
            "type": media_type, "year": year or "—",
            "episodes": n or (1 if media_type == "Movie" else 0),
            "rating": 0.0,
            "poster": f"https://placehold.co/300x420/161b22/546e7a?text={urllib.parse.quote(title_clean[:20])}",
            "genres": ["Lainnya"],
            "description": (
                f"<b>{title_clean}</b><br><br>"
                f"Metadata tidak ditemukan.<br>"
                f"Periksa koneksi internet atau rename folder sesuai judul asli."
            ),
            "description_id": "", "description_ja": "",
            "status": "Completed", "studio": "—", "trailer": "",
        }

    cache.set(ck, result)
    return result
