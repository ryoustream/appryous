"""
lib/cache.py — Cache JSON berbasis file untuk ŘΨØŬ v1.0.1
"""

import os, json, time
import sys
sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))
import config

_CACHE_DIR     = config.CACHE_DIR
_TTL           = config.CACHE_TTL_HOURS * 3600
_LIBRARY_FILE  = os.path.join(_CACHE_DIR, "library.json")

os.makedirs(_CACHE_DIR, exist_ok=True)


def _path(key: str) -> str:
    safe = key.replace("/", "_").replace(":", "_").replace(" ", "_")
    return os.path.join(_CACHE_DIR, f"{safe}.json")


def get(key: str):
    p = _path(key)
    if not os.path.isfile(p):
        return None
    try:
        if time.time() - os.path.getmtime(p) > _TTL:
            return None
        with open(p, encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return None


def set(key: str, value):
    p = _path(key)
    try:
        with open(p, "w", encoding="utf-8") as f:
            json.dump(value, f, ensure_ascii=False)
    except Exception as e:
        print(f"[Cache] Gagal tulis cache '{key}': {e}")


def load_library() -> dict | None:
    if not os.path.isfile(_LIBRARY_FILE):
        return None
    try:
        if time.time() - os.path.getmtime(_LIBRARY_FILE) > _TTL:
            return None
        with open(_LIBRARY_FILE, encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return None


def save_library(data: dict):
    try:
        with open(_LIBRARY_FILE, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False)
    except Exception as e:
        print(f"[Cache] Gagal simpan library cache: {e}")


def clear_all():
    try:
        for fname in os.listdir(_CACHE_DIR):
            if fname.endswith(".json"):
                os.remove(os.path.join(_CACHE_DIR, fname))
        print("[Cache] Semua cache dihapus.")
    except Exception as e:
        print(f"[Cache] Gagal hapus cache: {e}")
