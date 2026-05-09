"""
lib/scanner.py — Scan direktori SD Card ŘΨØŬ v1.0.1
Mendukung struktur flat dan archive folder.
"""

import os, re, struct
from urllib.parse import quote as _q

import sys
sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))
import config

MOVIES_PATH = config.MOVIES_PATH
VIDEOS_PATH = config.VIDEOS_PATH
VIDEO_EXTS  = config.VIDEO_EXTS
SUB_EXTS    = config.SUB_EXTS

# ─── MUTAGEN (opsional) ───────────────────────────────────────────────────────
try:
    import mutagen
    from mutagen.mp4  import MP4
    _MUTAGEN = True
except ImportError:
    _MUTAGEN = False
    print("[Scanner] mutagen tidak terinstall — tag file tidak dibaca.")

# ─── NOISE PATTERNS ───────────────────────────────────────────────────────────
_NOISE_PATS = [
    r"\bsub\s*indo\b", r"\bbatch\b", r"\bend\b",
    r"\b(360|480|720|1080|2160)p?\b",
    r"\b(x264|x265|hevc|avc|h264|h\.264|h265|h\.265|xvid|divx|vp9|av1)\b",
    r"\b(bluray|blu-ray|bdrip|bdremux|webrip|web-dl|webdl|hdtv|dvdrip|nf|amzn|dsnp)\b",
    r"\b(aac|ac3|dts|flac|mp3|opus|dd5\.1|truehd|atmos|eac3)\b",
    r"\b(dual|dubbed|sub|subtitle|hardsub|softsub|multi)\b",
    r"\b(extended|theatrical|director.?s?\s*cut|unrated|remastered|remux|proper|complete)\b",
    r"\[.*?\]", r"\((?!\d{4}\)).*?\)", r"s\d{1,2}e\d{1,2}",
    r"\b\d{2,3}[._\-]\b", r"[._]", r"\s{2,}",
]

ABBREV_MAP = {
    "aot": "Attack on Titan", "snk": "Attack on Titan",
    "hxh": "Hunter x Hunter", "mha": "My Hero Academia", "bnha": "My Hero Academia",
    "jjk": "Jujutsu Kaisen", "kny": "Demon Slayer", "ds": "Demon Slayer",
    "op":  "One Piece",       "dbs": "Dragon Ball Super", "dbz": "Dragon Ball Z",
    "sao": "Sword Art Online", "csm": "Chainsaw Man",
    "rezero": "Re:ZERO",      "re zero": "Re:ZERO",
    "tpn": "The Promised Neverland", "tb": "Tokyo Revengers", "tr": "Tokyo Revengers",
}


# ─── FILE TAGS ────────────────────────────────────────────────────────────────

def _first(val):
    if val is None: return None
    return str(val[0]) if isinstance(val, (list, tuple)) else str(val)


def read_file_tags(path: str) -> dict:
    result = {"title": None, "show": None, "year": None}
    ext = os.path.splitext(path)[1].lower()
    if _MUTAGEN:
        try:
            if ext in (".mp4", ".m4v", ".mov"):
                audio = MP4(path)
                tags  = audio.tags or {}
                result["title"] = _first(tags.get("\xa9nam"))
                result["show"]  = _first(tags.get("tvsh"))
                result["year"]  = _first(tags.get("\xa9day"))
            elif ext == ".mkv":
                f = mutagen.File(path)
                if f and f.tags:
                    result["title"] = _first(f.tags.get("TITLE") or f.tags.get("title"))
                    result["show"]  = _first(f.tags.get("SHOW") or f.tags.get("show"))
                    result["year"]  = _first(f.tags.get("DATE") or f.tags.get("year"))
        except Exception:
            pass
    if result["year"]:
        m = re.search(r"(\d{4})", str(result["year"]))
        result["year"] = m.group(1) if m else None
    return result


# ─── TITLE CLEANER ────────────────────────────────────────────────────────────

def extract_mal_id(raw: str) -> int | None:
    m = re.search(r"#(\d+)\s*$", raw.strip())
    return int(m.group(1)) if m else None


def clean_title(raw: str) -> tuple:
    name = os.path.splitext(raw)[0]
    name = re.sub(r"^[A-Za-z0-9]+\.(net|com|org|id|cc)\s*[_\-]?\s*", "", name, flags=re.IGNORECASE)
    work = re.sub(r"[._]", " ", name)
    work = re.sub(r"\s*--+\s*", " - ", work)
    for pat in _NOISE_PATS:
        work = re.sub(pat, " ", work, flags=re.IGNORECASE)
    year_m = re.search(r"\((\d{4})\)", work)
    year   = year_m.group(1) if year_m else None
    if year_m:
        work = work[:year_m.start()] + work[year_m.end():]
    work = re.sub(r"[^\w\s\-\':]", " ", work)
    work = re.sub(r"\s{2,}", " ", work).strip(" -:")
    return work, year


def resolve_title(raw_name: str, file_path: str = None) -> tuple:
    """Return (primary, alts, year, mal_id_override)"""
    mal_id   = extract_mal_id(raw_name)
    raw_name = re.sub(r"\s*#\d+\s*$", "", raw_name).strip()
    tags     = read_file_tags(file_path) if file_path else {}
    clean, year_from_name = clean_title(raw_name)

    candidates = []
    tag_show  = tags.get("show")
    tag_title = tags.get("title")
    year      = tags.get("year") or year_from_name

    if tag_show  and len(tag_show.strip())  > 1: candidates.append(tag_show.strip())
    if tag_title and len(tag_title.strip()) > 1 and tag_title != tag_show:
        candidates.append(tag_title.strip())

    clean_lo = clean.lower()
    for abbr, full in ABBREV_MAP.items():
        if clean_lo == abbr or clean_lo.startswith(abbr + " ") or clean_lo.endswith(" " + abbr):
            if full not in candidates: candidates.append(full)
            break

    if clean and clean not in candidates:
        candidates.append(clean)
    raw_fb = os.path.splitext(raw_name)[0]
    if raw_fb not in candidates:
        candidates.append(raw_fb)

    primary = candidates[0] if candidates else raw_fb
    alts    = candidates[1:] if len(candidates) > 1 else []
    return primary, alts, year, mal_id


# ─── SUBTITLE FINDER ─────────────────────────────────────────────────────────

def _find_subs(video_path: str, all_files: list) -> list:
    base = os.path.splitext(video_path)[0]
    subs = []
    for f in all_files:
        ext = os.path.splitext(f)[1].lower()
        if ext not in SUB_EXTS: continue
        fb = os.path.splitext(f)[0]
        if fb == base or fb.startswith(base + ".") or fb.startswith(base + "_") or fb.startswith(base + " "):
            lang, label = "id", "Indonesia"
            if re.search(r"[._\-\s](en|eng)[._\-\s]", f, re.IGNORECASE): lang, label = "en", "English"
            elif re.search(r"[._\-\s](jp|jpn|ja)[._\-\s]", f, re.IGNORECASE): lang, label = "ja", "Japanese"
            subs.append({"label": label, "lang": lang, "file": f, "default": lang == "id"})
    return subs


# ─── SCAN MOVIES ─────────────────────────────────────────────────────────────

def scan_movies(base_url: str) -> list:
    results = []
    if not os.path.isdir(MOVIES_PATH):
        print(f"[Scanner] MOVIES_PATH tidak ditemukan: {MOVIES_PATH}")
        return results

    all_files = os.listdir(MOVIES_PATH)
    m3u8  = sorted([f for f in all_files if f.lower().endswith(".m3u8")])
    files = m3u8 if m3u8 else sorted([
        f for f in all_files
        if os.path.splitext(f)[1].lower() in VIDEO_EXTS and not f.lower().endswith(".ts")
    ])

    for fname in files:
        fpath = os.path.join(MOVIES_PATH, fname)
        primary, alts, year, mal_id = resolve_title(fname, fpath)
        subs = _find_subs(os.path.join(MOVIES_PATH, fname),
                          [os.path.join(MOVIES_PATH, f) for f in all_files])
        video_url = f"{base_url}/media/Movies/{_q(fname, safe='')}"
        sub_list  = [{"label": s["label"], "lang": s["lang"],
                      "src": f"{base_url}/media/Movies/{_q(os.path.basename(s['file']), safe='')}",
                      "default": s["default"]} for s in subs]
        # Cari chapter XML: <nama_video_tanpa_ext>.xml
        chapter_file = None
        xml_path = os.path.splitext(fpath)[0] + ".xml"
        if os.path.isfile(xml_path):
            chapter_file = xml_path
        results.append({
            "raw_title": fname, "title_clean": primary, "title_alts": alts,
            "year": year, "media_type": "Movie",
            "video_url": video_url, "subtitles": sub_list,
            "chapter_file": chapter_file,
            "path": fpath, "mal_id_override": mal_id,
        })
    return results


# ─── SCAN SERIES ─────────────────────────────────────────────────────────────

def _scan_title_folder(series_path: str, dir_name: str,
                        archive_prefix: str, base_url: str) -> dict | None:
    primary, alts, year, mal_id = resolve_title(dir_name, None)
    all_files = os.listdir(series_path)

    m3u8 = sorted([f for f in all_files if f.lower().endswith(".m3u8")])
    raws = m3u8 if m3u8 else sorted([
        f for f in all_files
        if os.path.splitext(f)[1].lower() in VIDEO_EXTS and not f.lower().endswith(".ts")
    ])
    if not raws: return None

    episodes = []
    for idx, ep_file in enumerate(raws, 1):
        ep_path = os.path.join(series_path, ep_file)
        m = re.search(r"(?:ep?|episode|--|[\s_\-]|E)(\d{1,3})(?:[._\-\s]|$)", ep_file, re.IGNORECASE)
        ep_num = int(m.group(1)) if m else idx

        ep_tags  = read_file_tags(ep_path)
        ep_title_tag = ep_tags.get("title")
        ep_clean, _  = clean_title(ep_file)
        ep_title = (ep_title_tag if ep_title_tag and ep_title_tag.lower() != primary.lower()
                    else ep_clean or f"Episode {ep_num}")

        subs = _find_subs(ep_path, [os.path.join(series_path, f) for f in all_files])
        _ap  = "/".join(_q(p, safe="") for p in archive_prefix.rstrip("/").split("/") if p)
        _ap  = (_ap + "/") if _ap else ""
        video_url = f"{base_url}/media/Videos/{_ap}{_q(dir_name, safe='')}/{_q(ep_file, safe='')}"
        sub_list  = [{"label": s["label"], "lang": s["lang"],
                      "src": f"{base_url}/media/Videos/{_ap}{_q(dir_name, safe='')}/{_q(os.path.basename(s['file']), safe='')}",
                      "default": s["default"]} for s in subs]
        # Cari chapter XML: <nama_video_tanpa_ext>.xml
        chapter_file = None
        xml_path = os.path.splitext(ep_path)[0] + ".xml"
        if os.path.isfile(xml_path):
            chapter_file = xml_path

        episodes.append({
            "ep": ep_num, "ep_file": ep_file, "ep_title_clean": ep_title,
            "video_url": video_url, "subtitles": sub_list,
            "chapter_file": chapter_file,
        })

    return {
        "raw_title": dir_name, "title_clean": primary, "title_alts": alts,
        "year": year, "media_type": "TV", "episodes": episodes,
        "mal_id_override": mal_id, "path": series_path,
    }


def _is_archive(folder_path: str) -> bool:
    try:
        items = os.listdir(folder_path)
    except PermissionError:
        return False
    if not items: return False
    dirs  = sum(1 for i in items if os.path.isdir(os.path.join(folder_path, i)))
    files = sum(1 for i in items if os.path.splitext(i)[1].lower() in VIDEO_EXTS)
    return dirs > 0 and files == 0


def scan_series(base_url: str) -> list:
    results = []
    if not os.path.isdir(VIDEOS_PATH):
        print(f"[Scanner] VIDEOS_PATH tidak ditemukan: {VIDEOS_PATH}")
        return results

    for top_name in sorted(os.listdir(VIDEOS_PATH)):
        top_path = os.path.join(VIDEOS_PATH, top_name)
        if not os.path.isdir(top_path): continue

        if _is_archive(top_path):
            for title_name in sorted(os.listdir(top_path)):
                title_path = os.path.join(top_path, title_name)
                if not os.path.isdir(title_path): continue
                entry = _scan_title_folder(title_path, title_name, f"{top_name}/", base_url)
                if entry: results.append(entry)
        else:
            entry = _scan_title_folder(top_path, top_name, "", base_url)
            if entry: results.append(entry)

    return results


def scan_all(base_url: str) -> dict:
    print("[Scanner] Memulai scan SD card...")
    movies = scan_movies(base_url)
    series = scan_series(base_url)
    print(f"[Scanner] Ditemukan {len(movies)} film, {len(series)} series.")
    return {"movies": movies, "series": series}
