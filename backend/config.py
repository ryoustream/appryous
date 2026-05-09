"""
backend/config.py — AppRyous Backend
SD Card di-detect otomatis. Override via env var atau config.json.

Priority (tinggi ke rendah):
  1. ENV var SDCARD_ROOT
  2. File backend/config.json  {"sdcard": "/storage/XXXX-XXXX"}
  3. Auto-detect via df
  4. Fallback ke /storage/emulated/0
"""

import os, re, subprocess, json

_BACKEND_DIR = os.path.dirname(os.path.abspath(__file__))
_CONFIG_FILE = os.path.join(_BACKEND_DIR, "config.json")

# ─── LOAD CONFIG.JSON (override dari app) ─────────────────────────────────────
_json_cfg = {}
if os.path.exists(_CONFIG_FILE):
    try:
        with open(_CONFIG_FILE) as f:
            _json_cfg = json.load(f)
        print(f"[Config] Loaded config.json: {_json_cfg}")
    except Exception as e:
        print(f"[Config] config.json read error: {e}")

# ─── AUTO-DETECT SD CARD ──────────────────────────────────────────────────────
_SD_PATTERN = re.compile(r'^[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}$')

def _detect_sdcard():
    try:
        result = subprocess.run(
            ["df", "-h"], capture_output=True, text=True, timeout=5
        )
        candidates = []
        for line in result.stdout.splitlines():
            parts = line.split()
            if not parts: continue
            mount = parts[-1]
            if not mount.startswith("/storage/"): continue
            name = mount.split("/storage/")[-1].strip("/")
            if _SD_PATTERN.match(name):
                candidates.append(mount)

        if candidates:
            for c in candidates:
                if os.path.isdir(os.path.join(c, "Movies")) or \
                   os.path.isdir(os.path.join(c, "Videos")):
                    return c
            return candidates[0]
    except Exception as e:
        print(f"[Config] df error: {e}")
    return None

# ─── RESOLVE SDCARD_ROOT ──────────────────────────────────────────────────────
def _resolve_sdcard():
    # 1. ENV var
    env = os.environ.get("SDCARD_ROOT", "").strip()
    if env and os.path.isdir(env):
        print(f"[Config] ✅ SD Card via ENV: {env}")
        return env

    # 2. config.json
    cfg = _json_cfg.get("sdcard", "").strip()
    if cfg and os.path.isdir(cfg):
        print(f"[Config] ✅ SD Card via config.json: {cfg}")
        return cfg

    # 3. Auto-detect
    auto = _detect_sdcard()
    if auto:
        print(f"[Config] ✅ SD Card auto-detect: {auto}")
        return auto

    # 4. Fallback emulated
    fallback = "/storage/emulated/0"
    print(f"[Config] ⚠️  SD Card tidak terdeteksi → fallback: {fallback}")
    print(f"[Config]    Set via: export SDCARD_ROOT=/storage/XXXX-XXXX")
    return fallback

SDCARD_ROOT  = _resolve_sdcard()
MOVIES_PATH  = _json_cfg.get("movies_path") or os.path.join(SDCARD_ROOT, "Movies")
VIDEOS_PATH  = _json_cfg.get("videos_path") or os.path.join(SDCARD_ROOT, "Videos")
FONTS_PATH   = os.path.join(SDCARD_ROOT, "Fonts")

# ─── SERVER ───────────────────────────────────────────────────────────────────
HOST  = "0.0.0.0"
PORT  = int(os.environ.get("PORT", _json_cfg.get("port", 8080)))
DEBUG = os.environ.get("DEBUG", "").lower() in ("1", "true")

# ─── GIST ─────────────────────────────────────────────────────────────────────
GIST_ID       = _json_cfg.get("gist_id",       "b324efa90678d7fa4f605c8c425a6596")
GIST_USERNAME = _json_cfg.get("gist_username",  "ryoustream")
# Token opsional — tanpa token masih bisa update Gist jika public
GITHUB_TOKEN  = os.environ.get("GITHUB_TOKEN", _json_cfg.get("github_token", ""))

# ─── CACHE ────────────────────────────────────────────────────────────────────
CACHE_DIR       = os.path.join(_BACKEND_DIR, "cache")
CACHE_TTL_HOURS = int(_json_cfg.get("cache_ttl_hours", 168))

# ─── METADATA API ─────────────────────────────────────────────────────────────
JIKAN_BASE    = "https://api.jikan.moe/v4"
JIKAN_DELAY   = 0.4
MDL_BASE      = "https://my-drama-list-api-ten.vercel.app"
TMDB_API_KEY  = os.environ.get("TMDB_API_KEY", _json_cfg.get("tmdb_api_key",
                "37417939a4b0213ea809e390ab206b62"))
TMDB_BASE     = "https://api.themoviedb.org/3"
TMDB_IMG_BASE = "https://image.tmdb.org/t/p/w500"

# ─── FILE EXTENSIONS ──────────────────────────────────────────────────────────
VIDEO_EXTS = {".mp4", ".mkv", ".avi", ".mov", ".webm", ".m4v", ".ts", ".m3u8"}
SUB_EXTS   = {".vtt", ".srt", ".ass", ".ssa"}

# ─── CORS ─────────────────────────────────────────────────────────────────────
CORS_ORIGINS = "*"

# ─── PRINT SUMMARY ────────────────────────────────────────────────────────────
print(f"[Config] Movies : {MOVIES_PATH}")
print(f"[Config] Videos : {VIDEOS_PATH}")
print(f"[Config] Port   : {PORT}")
print(f"[Config] Gist   : {GIST_USERNAME}/{GIST_ID}")
