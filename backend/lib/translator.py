"""
lib/translator.py — Terjemahan teks via MyMemory API (gratis, tanpa key)
ŘΨØŬ v1.0.1
"""

import urllib.request, urllib.parse, json, time

_last = 0.0
_DELAY = 1.2  # minimal jeda antar request (MyMemory ~100 req/hari gratis)


def translate(text: str, target_lang: str = "id") -> str | None:
    """
    Terjemahkan teks ke target_lang menggunakan MyMemory.
    Return string terjemahan atau None jika gagal.
    """
    global _last
    if not text or not text.strip():
        return None

    # Batasi panjang teks (MyMemory max 500 char)
    text = text[:500].strip()

    # Hapus tag HTML sebelum translate
    import re
    clean = re.sub(r"<[^>]+>", " ", text).strip()
    clean = re.sub(r"\s{2,}", " ", clean)

    elapsed = time.time() - _last
    if elapsed < _DELAY:
        time.sleep(_DELAY - elapsed)

    try:
        q = urllib.parse.urlencode({
            "q"        : clean,
            "langpair" : f"en|{target_lang}",
            "de"       : "ryou@media.local",
        })
        url = f"https://api.mymemory.translated.net/get?{q}"
        req = urllib.request.Request(url, headers={"User-Agent": "RYOU/1.0"})
        with urllib.request.urlopen(req, timeout=8) as r:
            data = json.loads(r.read().decode("utf-8"))
            _last = time.time()

        resp_data = data.get("responseData") or {}
        translated = resp_data.get("translatedText") or ""
        # MyMemory kadang kembalikan teks asli jika gagal
        if translated and translated.lower() != clean.lower():
            return translated
        return None
    except Exception:
        _last = time.time()
        return None
