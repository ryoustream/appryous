"""
server.py — Backend utama ŘΨØŬ v1.0.1
Jalankan: python server.py
"""

import os, re, mimetypes, threading, time, json, sys
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs, unquote

sys.path.insert(0, os.path.dirname(__file__))
import config
import lib.cache    as cache
import lib.scanner  as scanner
import lib.metadata as metadata
import lib.translator as translator

VERSION = "1.0.1"

# ─── STATE ───────────────────────────────────────────────────────────────────
_lock        = threading.Lock()
_library          = []    # [{id, title, type, ...}]
_episodes         = {}    # id → episode list
_scan_status      = {"running": False, "progress": "", "last_scan": 0}
_current_base_url = ''    # diupdate tiap request masuk


# ─── BUILD LIBRARY ────────────────────────────────────────────────────────────

def _build_library(force: bool = False):
    global _library, _episodes

    if not force:
        cached = cache.load_library()
        if cached:
            with _lock:
                _library  = cached["list"]
                _episodes = cached["episodes"]
            print(f"[Server] Library dari cache: {len(_library)} judul.")
            return

    _scan_status["running"]  = True
    _scan_status["progress"] = "Memulai scan..."

    try:
        # Ambil base_url dari Host header request agar video_url absolute
        # Selalu scan dengan base_url kosong → path relatif (/media/...)
        # Base URL ditambahkan saat serve /api/library (tidak disimpan di cache)
        scan_data = scanner.scan_all("")
        all_entries = scan_data["movies"] + scan_data["series"]
        new_lib, new_eps = [], {}

        for idx, entry in enumerate(all_entries, 1):
            eid     = str(idx).zfill(4)
            title   = entry["title_clean"]
            mtype   = entry["media_type"]
            num_eps = len(entry.get("episodes", [])) or 1
            _scan_status["progress"] = f"[{idx}/{len(all_entries)}] {title}"

            meta = metadata.fetch_metadata(
                title_clean        = title,
                title_alts         = entry.get("title_alts", []),
                media_type         = mtype,
                year               = entry.get("year"),
                num_episodes_local = num_eps,
                raw_title          = entry.get("raw_title", ""),
                mal_id_override    = entry.get("mal_id_override"),
            )

            lib_entry = {
                "id"         : eid,
                "title"      : meta["title"] or title,
                "title_en"   : meta.get("title_en")    or meta["title"] or title,
                "title_ja"   : meta.get("title_ja")    or meta.get("title_jp") or "",
                "title_romaji": meta.get("title_romaji") or "",
                "title_local": title,
                "type"       : meta["type"],
                "year"       : meta["year"],
                "episodes"   : meta["episodes"] or num_eps,
                "rating"     : meta["rating"],
                "poster"     : meta["poster"],
                "banner"     : meta.get("banner") or meta["poster"],
                "genres"     : meta["genres"],
                "genres_disp": meta.get("genres_disp") or meta["genres"],
                "description"    : meta["description"],
                "description_id" : meta.get("description_id") or "",
                "description_ja" : meta.get("description_ja") or "",
                "status"     : meta["status"],
                "studio"     : meta["studio"],
                "aired"      : meta.get("aired", "—"),
                "aired_end"  : meta.get("aired_end", ""),
                "source"     : meta.get("source", ""),
                "mal_id"     : meta.get("mal_id"),
                "tmdb_id"    : meta.get("tmdb_id"),
                "mdl_slug"   : meta.get("mdl_slug", ""),
                "title_synonyms": meta.get("title_synonyms", []),
                "trailer"    : meta.get("trailer", ""),
                "themes"     : meta.get("themes", []),
                "demographics": meta.get("demographics", []),
            }

            # Terjemahkan deskripsi on-demand untuk sumber non-TMDB
            if lib_entry["description"] and lib_entry.get("source") in ("mal", "mdl", "undefined"):
                if not lib_entry["description_id"]:
                    lib_entry["description_id"] = (
                        translator.translate(lib_entry["description"], "id")
                        or lib_entry["description"]
                    )
                if not lib_entry["description_ja"]:
                    lib_entry["description_ja"] = (
                        translator.translate(lib_entry["description"], "ja")
                        or lib_entry["description"]
                    )
            elif not lib_entry["description_id"]:
                lib_entry["description_id"] = lib_entry["description"]
                lib_entry["description_ja"] = lib_entry["description"]

            new_lib.append(lib_entry)

            # Episode list
            if mtype == "Movie":
                ep_list = [{
                    "ep": 1, "title": "Play Now", "duration": "—",
                    "thumbnail": meta["poster"],
                    "src": entry["video_url"],
                    "subtitles": entry.get("subtitles", []),
                    "chapter_file": entry.get("chapter_file"),
                }]
            else:
                mal_id  = meta.get("mal_id")
                tmdb_id = meta.get("tmdb_id")

                # Judul episode: Jikan → TMDB → scanner → "Episode N"
                ep_titles = {}
                ep_thumbs = {}    # thumbnail per-episode dari TMDB
                if mal_id:
                    ep_titles = metadata.fetch_episode_titles(mal_id)
                if tmdb_id:
                    tmdb_data = metadata.fetch_episode_data_tmdb(tmdb_id)
                    if not ep_titles:
                        ep_titles = tmdb_data.get("titles", {})
                    # Thumbnail TMDB selalu diambil kalau ada
                    ep_thumbs = tmdb_data.get("thumbs", {})

                ep_list = []
                for ep in entry.get("episodes", []):
                    ep_num        = ep["ep"]
                    scanner_title = ep.get("ep_title_clean") or ""
                    if scanner_title.lower() == (lib_entry["title"] or "").lower():
                        scanner_title = ""
                    ep_title = (ep_titles.get(ep_num)
                                or ep_titles.get(str(ep_num))
                                or scanner_title
                                or f"Episode {ep_num}")
                    # Thumbnail: TMDB still → fallback ke poster anime
                    ep_thumb = (ep_thumbs.get(ep_num)
                                or ep_thumbs.get(str(ep_num))
                                or meta["poster"])
                    ep_list.append({
                        "ep"          : ep_num,
                        "title"       : ep_title,
                        "duration"    : "—",
                        "thumbnail"   : ep_thumb,
                        "src"         : ep["video_url"],
                        "subtitles"   : ep.get("subtitles", []),
                        "chapter_file": ep.get("chapter_file"),
                    })

            new_eps[eid] = {"id": eid, "title": lib_entry["title"], "episodes": ep_list}

        with _lock:
            _library  = new_lib
            _episodes = new_eps

        cache.save_library({"list": new_lib, "episodes": new_eps})
        _scan_status["last_scan"] = int(time.time())
        _scan_status["progress"]  = f"Selesai! {len(new_lib)} judul."
        print(f"[Server] Library selesai: {len(new_lib)} judul.")

    except Exception as e:
        _scan_status["progress"] = f"Error: {e}"
        print(f"[Server] Build error: {e}")
        import traceback; traceback.print_exc()
    finally:
        _scan_status["running"] = False



# ─── ASS → VTT CONVERTER ─────────────────────────────────────────────────────

def _ass_to_vtt(ass_content: str) -> str:
    """Konversi ASS/SSA subtitle ke WebVTT (strip styling, pertahankan teks)."""
    import re as _re
    lines = ass_content.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    dialogues = []
    for line in lines:
        if not line.startswith("Dialogue:"):
            continue
        parts = line[9:].strip().split(",", 9)
        if len(parts) < 10:
            continue
        start, end, text = parts[1].strip(), parts[2].strip(), parts[9]
        text = _re.sub(r"\{[^}]*\}", "", text)
        text = text.replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ").strip()
        if not text:
            continue
        def _t(s):
            p = s.split(":")
            return f"{int(p[0]):02d}:{int(p[1]):02d}:{float(p[2]):06.3f}" if len(p) == 3 else s
        dialogues.append((_t(start), _t(end), text))
    vtt = "WEBVTT\n\n"
    for i, (s, e, t) in enumerate(dialogues, 1):
        vtt += f"{i}\n{s} --> {e}\n{t}\n\n"
    return vtt

def _srt_to_vtt(srt_content: str) -> str:
    """Konversi SRT subtitle ke WebVTT."""
    import re as _re
    # Normalize line endings
    text = srt_content.replace("\r\n", "\n").replace("\r", "\n").strip()
    blocks = _re.split(r"\n{2,}", text)
    vtt = "WEBVTT\n\n"
    for block in blocks:
        lines = block.strip().split("\n")
        if len(lines) < 3:
            continue
        # Skip cue number line
        time_line = None
        text_lines = []
        for i, line in enumerate(lines):
            if "-->" in line:
                # SRT uses comma for ms, VTT uses dot
                time_line = line.replace(",", ".")
                text_lines = lines[i+1:]
                break
        if not time_line or not text_lines:
            continue
        # Validate timestamp format hh:mm:ss.mmm
        parts = time_line.split("-->")
        if len(parts) != 2:
            continue
        start, end = parts[0].strip(), parts[1].strip()
        if not start or not end:
            continue
        content = "\n".join(text_lines).strip()
        if not content:
            continue
        vtt += f"{start} --> {end}\n{content}\n\n"
    return vtt


def _parse_xml_chapters(xml_content: str) -> list:
    """Parse Matroska XML chapters → list of {time, title}."""
    import xml.etree.ElementTree as _ET
    import re as _re
    try:
        root = _ET.fromstring(xml_content)
    except Exception:
        return []
    chapters = []
    for atom in root.iter("ChapterAtom"):
        t = atom.findtext("ChapterTimeStart") or ""
        name = ""
        for disp in atom.iter("ChapterDisplay"):
            s = disp.findtext("ChapterString")
            if s: name = s; break
        if not t:
            continue
        try:
            p = t.split(":")
            secs = int(p[0]) * 3600 + int(p[1]) * 60 + float(p[2])
            chapters.append({"time": round(secs, 3), "title": name.strip()})
        except Exception:
            continue
    return chapters

# ─── HTTP HANDLER ─────────────────────────────────────────────────────────────

class Handler(BaseHTTPRequestHandler):
    server_version = f"RYOU/{VERSION}"

    def log_message(self, fmt, *args):
        print(f"[HTTP] {self.address_string()} – {fmt % args}")

    def _cors(self):
        origin = self.headers.get("Origin", "*")
        self.send_header("Access-Control-Allow-Origin",       origin if origin != "*" else "*")
        self.send_header("Access-Control-Allow-Methods",      "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers",      "Content-Type, Range, Authorization, Origin")
        self.send_header("Access-Control-Expose-Headers",     "Content-Range, Accept-Ranges, Content-Length")
        self.send_header("Access-Control-Allow-Credentials",  "false")
        self.send_header("Vary",                              "Origin")


    def do_OPTIONS(self):
        # Handle preflight untuk SEMUA path termasuk /media/
        self.send_response(204)
        self._cors()
        self.send_header("Access-Control-Max-Age", "86400")
        self.end_headers()

    def do_POST(self):
        global _current_base_url
        _h = self.headers.get('Host', f'localhost:{config.PORT}')
        _p = 'https' if self.headers.get('X-Forwarded-Proto') == 'https' else 'http'
        _current_base_url = f'{_p}://{_h}'
        parsed = urlparse(self.path)
        path   = parsed.path.rstrip("/")
        if path == "/api/scan":
            return self._api_scan(False)
        return self._json(404, {"error": "Not found"})

    def send_error(self, code, message=None, explain=None):
        try:
            self.send_response(code)
            self._cors()
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            if message:
                self.wfile.write(str(message).encode())
        except Exception:
            pass

    def do_GET(self):
        global _current_base_url
        _h = self.headers.get('Host', f'localhost:{config.PORT}')
        _p = 'https' if self.headers.get('X-Forwarded-Proto') == 'https' else 'http'
        _current_base_url = f'{_p}://{_h}'
        parsed = urlparse(self.path)
        path   = parsed.path.rstrip("/")
        params = parse_qs(parsed.query)

        # ── API ──
        if path == "/api/library":          return self._api_library()
        if path.startswith("/api/episodes/"): return self._api_episodes(path.split("/")[-1])
        if path == "/api/scan":
            force = params.get("force", ["0"])[0] == "1"
            return self._api_scan(force)
        if path == "/api/scan/status":      return self._json(200, _scan_status)
        if path == "/api/settings":         return self._api_settings()
        if path == "/api/clear_cache":      return self._api_clear_cache(params)
        if path == "/api/dirlist":
            dir_path = unquote(params.get("path", [config.SDCARD_ROOT])[0])
            return self._api_dirlist(dir_path)

        # ── API Chapters (parse XML) ──
        if path == "/api/chapters":
            mp = unquote(params.get("path", [""])[0])
            return self._api_chapters(mp)

        # ── API Fonts (list font files di FONTS_PATH) ──
        if path == "/api/fonts":
            return self._api_fonts()

        # ── Media ──
        if path.startswith("/media/"):
            raw = params.get("raw", ["0"])[0] == "1"
            return self._serve_media(path, raw=raw)

        # ── Font files ──
        if path.startswith("/fonts/"):      return self._serve_font(path)

        # ── Static ──
        return self._serve_static(path)

    # ─── API Handlers ────────────────────────────────────────────────────────

    def _api_library(self):
        with _lock: lib = list(_library)
        if not lib and not _scan_status["running"]:
            threading.Thread(target=_build_library, daemon=True).start()
            return self._json(202, {
                "status": "scanning",
                "message": "Library kosong, scan otomatis dimulai. Coba lagi dalam 30 detik.",
                "data": []
            })
        # Inject base_url ke src saat serve — path di cache adalah relatif
        base = _current_base_url or f'http://localhost:{config.PORT}'
        import copy as _copy
        out = _copy.deepcopy(lib)
        for entry in out:
            if entry.get('src') and entry['src'].startswith('/'):
                entry['src'] = base + entry['src']
        return self._json(200, {"status": "ok", "data": out, "total": len(out)})

    def _api_episodes(self, eid: str):
        with _lock: ep = _episodes.get(eid)
        if not ep: return self._json(404, {"error": f"ID {eid} tidak ditemukan."})
        # Inject base_url ke src episode
        base = _current_base_url or f'http://localhost:{config.PORT}'
        import copy as _copy
        out = _copy.deepcopy(ep)
        for episode in out.get('episodes', []):
            if episode.get('src') and episode['src'].startswith('/'):
                episode['src'] = base + episode['src']
            for sub in episode.get('subtitles', []):
                if sub.get('src') and sub['src'].startswith('/'):
                    sub['src'] = base + sub['src']
        return self._json(200, out)

    def _api_scan(self, force: bool):
        if _scan_status["running"]:
            return self._json(200, {"status": "already_running", "progress": _scan_status["progress"]})
        if force:
            # Hapus cache episode titles agar judul episode diambil ulang dari Jikan
            try:
                for fname in os.listdir(config.CACHE_DIR):
                    if fname.startswith("eps_") and fname.endswith(".json"):
                        os.remove(os.path.join(config.CACHE_DIR, fname))
            except Exception:
                pass
        threading.Thread(target=_build_library, args=(force,), daemon=True).start()
        return self._json(200, {"status": "started", "force": force})

    def _api_chapters(self, media_path: str):
        """Parse XML chapters dari path media di SD card."""
        if not media_path:
            return self._json(400, {"error": "path diperlukan"})
        # Keamanan: hanya izinkan path dalam SDCARD_ROOT (dengan trailing sep)
        real = os.path.realpath(media_path)
        root = os.path.realpath(config.SDCARD_ROOT)
        if real != root and not real.startswith(root + os.sep):
            return self._json(403, {"error": "Akses ditolak"})
        if not os.path.isfile(real):
            return self._json(404, {"error": "File tidak ditemukan"})
        try:
            with open(real, encoding="utf-8-sig", errors="replace") as f:
                xml_content = f.read()
            chapters = _parse_xml_chapters(xml_content)
            return self._json(200, {"chapters": chapters, "count": len(chapters)})
        except Exception as e:
            return self._json(500, {"error": str(e)})

    def _api_fonts(self):
        """List font files di FONTS_PATH untuk libass subtitle renderer."""
        font_exts = {".ttf", ".otf", ".woff2", ".woff"}
        fonts = []
        fonts_dir = config.FONTS_PATH
        if os.path.isdir(fonts_dir):
            for fn in sorted(os.listdir(fonts_dir)):
                if os.path.splitext(fn)[1].lower() in font_exts:
                    fonts.append({
                        "name": fn,
                        "url" : f"/fonts/{fn}",
                    })
        return self._json(200, {"fonts": fonts, "count": len(fonts)})

    def _serve_font(self, url_path: str):
        """Serve font file dari FONTS_PATH."""
        filename = unquote(url_path[len("/fonts/"):])
        # Keamanan: tidak boleh ada path traversal
        if "/" in filename or "\\" in filename or filename.startswith("."):
            return self._json(403, {"error": "Akses ditolak"})
        fp = os.path.join(config.FONTS_PATH, filename)
        if not os.path.isfile(fp):
            self.send_response(404)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(b"Font not found")
            return
        ext  = os.path.splitext(fp)[1].lower()
        mime = {".ttf": "font/ttf", ".otf": "font/otf",
                ".woff2": "font/woff2", ".woff": "font/woff"}.get(ext, "application/octet-stream")
        self.send_response(200)
        self.send_header("Content-Type", mime)
        self.send_header("Content-Length", str(os.path.getsize(fp)))
        self.send_header("Cache-Control", "public, max-age=86400")
        self._cors()
        self.end_headers()
        with open(fp, "rb") as f:
            self.wfile.write(f.read())

    def _api_settings(self):
        return self._json(200, {
            "version"        : VERSION,
            "sdcard_root"    : config.SDCARD_ROOT,
            "movies_path"    : config.MOVIES_PATH,
            "videos_path"    : config.VIDEOS_PATH,
            "port"           : config.PORT,
            "cache_ttl_hours": config.CACHE_TTL_HOURS,
            "video_exts"     : sorted(config.VIDEO_EXTS),
            "sub_exts"       : sorted(config.SUB_EXTS),
            "has_tmdb_key"   : bool(config.TMDB_API_KEY),
            "mdl_base"       : config.MDL_BASE,
            "cache_dir"      : config.CACHE_DIR,
        })

    def _api_clear_cache(self, params: dict):
        ctype = params.get("type", ["all"])[0]
        if ctype == "library":
            lp = os.path.join(config.CACHE_DIR, "library.json")
            if os.path.exists(lp): os.remove(lp)
            return self._json(200, {"ok": True, "msg": "Library cache dihapus."})
        elif ctype == "meta":
            deleted = 0
            try:
                for f in os.listdir(config.CACHE_DIR):
                    if f.endswith(".json") and f != "library.json":
                        os.remove(os.path.join(config.CACHE_DIR, f)); deleted += 1
            except FileNotFoundError:
                pass
            return self._json(200, {"ok": True, "msg": f"{deleted} cache metadata dihapus."})
        else:
            cache.clear_all()
            return self._json(200, {"ok": True, "msg": "Semua cache dihapus."})

    def _api_dirlist(self, dir_path: str):
        real = os.path.realpath(dir_path)
        root = os.path.realpath(config.SDCARD_ROOT)
        if real != root and not real.startswith(root + os.sep):
            return self._json(403, {"error": "Akses ditolak: path di luar SD card."})
        if not os.path.isdir(real):
            return self._json(404, {"error": f"Direktori tidak ditemukan: {dir_path}"})
        items = []
        try:
            for name in sorted(os.listdir(real)):
                full = os.path.join(real, name)
                stat = os.stat(full)
                items.append({
                    "name": name, "path": full, "is_dir": os.path.isdir(full),
                    "size": stat.st_size, "modified": int(stat.st_mtime),
                    "ext": os.path.splitext(name)[1].lower()
                })
        except PermissionError as e:
            return self._json(403, {"error": str(e)})
        return self._json(200, {"path": real, "items": items, "count": len(items)})

    # ─── Static File Serving ─────────────────────────────────────────────────

    def _serve_static(self, url_path: str):
        www = os.path.realpath(config.WWW_PATH)
        rel = "index.html" if url_path in ("", "/") else url_path.lstrip("/")
        fp  = os.path.realpath(os.path.join(www, rel))
        if not fp.startswith(www):
            return self._json(403, {"error": "Akses ditolak."})
        if os.path.isdir(fp):
            fp = os.path.join(fp, "index.html")
        if not os.path.isfile(fp):
            self.send_response(404)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write(b"<h2>404 Not Found</h2><p><a href='/'>Kembali ke Home</a></p>")
            return
        mime, _ = mimetypes.guess_type(fp)
        if not mime:
            ext = os.path.splitext(fp)[1].lower()
            mime = {
                ".html": "text/html; charset=utf-8",
                ".js"  : "application/javascript; charset=utf-8",
                ".css" : "text/css; charset=utf-8",
                ".svg" : "image/svg+xml",
                ".ico" : "image/x-icon",
                ".json": "application/json",
                ".woff2": "font/woff2",
                ".woff" : "font/woff",
                ".wasm" : "application/wasm",
            }.get(ext, "application/octet-stream")
        self.send_response(200)
        self.send_header("Content-Type", mime)
        self.send_header("Content-Length", str(os.path.getsize(fp)))
        self._cors()
        self.end_headers()
        with open(fp, "rb") as f:
            self.wfile.write(f.read())

    # ─── Media Serving dengan Range Support ──────────────────────────────────

    def _serve_media(self, url_path: str, raw: bool = False):
        rel   = unquote(url_path[len("/media/"):])
        parts = rel.split("/", 1)
        if parts[0] == "Movies":
            base = config.MOVIES_PATH
        elif parts[0] == "Videos":
            base = config.VIDEOS_PATH
        else:
            return self._json(404, {"error": "Direktori media tidak dikenali."})

        sub_path = parts[1] if len(parts) > 1 else ""
        fp       = os.path.join(base, sub_path)

        if not os.path.realpath(fp).startswith(os.path.realpath(base)):
            return self._json(403, {"error": "Akses ditolak."})
        if not os.path.isfile(fp):
            return self._json(404, {"error": f"File tidak ditemukan: {fp}"})

        file_size = os.path.getsize(fp)
        ext = os.path.splitext(fp)[1].lower()
        MIME_MAP = {
            ".vtt": "text/vtt", ".srt": "text/vtt",   # served as VTT after conversion
            ".ass": "text/vtt",   # serve as VTT after conversion (unless raw=1)
            ".ssa": "text/vtt",
            ".mp4": "video/mp4", ".mkv": "video/x-matroska",
            ".webm": "video/webm", ".m3u8": "application/vnd.apple.mpegurl",
            ".ts": "video/mp2t", ".m4s": "video/iso.segment",
        }
        # SRT → convert ke VTT on-the-fly (Video.js hanya bisa baca VTT)
        if ext == ".srt":
            try:
                with open(fp, encoding="utf-8-sig", errors="replace") as f:
                    srt_content = f.read()
                vtt_bytes = _srt_to_vtt(srt_content).encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "text/vtt; charset=utf-8")
                self.send_header("Content-Length", str(len(vtt_bytes)))
                self.send_header("Cache-Control", "no-cache")
                self._cors()
                self.end_headers()
                self.wfile.write(vtt_bytes)
                return
            except Exception as e:
                return self._json(500, {"error": f"SRT convert error: {e}"})

        # ASS/SSA → convert on-the-fly ke VTT (untuk Artplayer built-in)
        # Jika raw=1, skip konversi — serve file mentah untuk libass worker
        if ext in (".ass", ".ssa") and not raw:
            try:
                with open(fp, encoding="utf-8-sig", errors="replace") as f:
                    ass_content = f.read()
                vtt_bytes = _ass_to_vtt(ass_content).encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "text/vtt; charset=utf-8")
                self.send_header("Content-Length", str(len(vtt_bytes)))
                self.send_header("Cache-Control", "no-cache")
                self._cors()
                self.end_headers()
                self.wfile.write(vtt_bytes)
                return
            except Exception as e:
                return self._json(500, {"error": f"ASS convert error: {e}"})

        mime = MIME_MAP.get(ext)
        if not mime: mime, _ = mimetypes.guess_type(fp)
        if not mime: mime = "application/octet-stream"

        range_header = self.headers.get("Range")
        if range_header:
            m = re.match(r"bytes=(\d+)-(\d*)", range_header)
            if m:
                start  = int(m.group(1))
                end    = int(m.group(2)) if m.group(2) else file_size - 1
                end    = min(end, file_size - 1)
                length = end - start + 1
                self.send_response(206)
                self.send_header("Content-Type", mime)
                self.send_header("Content-Range",  f"bytes {start}-{end}/{file_size}")
                self.send_header("Content-Length", str(length))
                self.send_header("Accept-Ranges",  "bytes")
                self._cors()
                self.end_headers()
                try:
                    with open(fp, "rb") as f:
                        f.seek(start)
                        remaining = length
                        while remaining > 0:
                            chunk = f.read(min(65536, remaining))
                            if not chunk: break
                            self.wfile.write(chunk)
                            remaining -= len(chunk)
                except (ConnectionResetError, BrokenPipeError):
                    pass
                return

        # Full file
        self.send_response(200)
        self.send_header("Content-Type", mime)
        self.send_header("Content-Length", str(file_size))
        self.send_header("Accept-Ranges", "bytes")
        if ext in (".vtt", ".srt", ".ass", ".ssa"): self.send_header("Cache-Control", "no-cache")
        self._cors()
        self.end_headers()
        try:
            with open(fp, "rb") as f:
                while True:
                    chunk = f.read(65536)
                    if not chunk: break
                    self.wfile.write(chunk)
        except (ConnectionResetError, BrokenPipeError):
            pass

    # ─── JSON Helper ─────────────────────────────────────────────────────────

    def _json(self, code: int, data: dict):
        try:
            body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        except (TypeError, ValueError) as e:
            # Fallback: serialisasi gagal (e.g. NaN float, non-JSON type)
            print(f"[Server] _json serialization error: {e}")
            err = json.dumps({"error": f"Serialization failed: {e}"}).encode("utf-8")
            self.send_response(500)
            self.send_header("Content-Type",   "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(err)))
            self._cors()
            self.end_headers()
            self.wfile.write(err)
            return
        self.send_response(code)
        self.send_header("Content-Type",   "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self._cors()
        self.end_headers()
        self.wfile.write(body)


# ─── ENTRY POINT ─────────────────────────────────────────────────────────────

if __name__ == "__main__":
    import ssl, socket as _socket

    _here     = os.path.dirname(os.path.abspath(__file__))
    CERT_FILE = os.path.join(_here, "cert.pem")
    KEY_FILE  = os.path.join(_here, "key.pem")
    use_https = os.path.isfile(CERT_FILE) and os.path.isfile(KEY_FILE)

    try:
        s = _socket.socket(_socket.AF_INET, _socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        LOCAL_IP = s.getsockname()[0]; s.close()
    except Exception:
        LOCAL_IP = "localhost"

    scheme = "http"   # HTTP lokal, HTTPS via Cloudflare Tunnel

    print("=" * 58)
    print(f"  ŘΨØŬ Media Server v{VERSION}")
    print(f"  Protokol : {scheme.upper()}{' (SSL)' if use_https else ''}")
    print(f"  HP ini   : {scheme}://localhost:{config.PORT}")
    print(f"  LAN      : {scheme}://{LOCAL_IP}:{config.PORT}")
    print(f"  Movies   : {config.MOVIES_PATH}")
    print(f"  Videos   : {config.VIDEOS_PATH}")
    if not use_https:
        print()
        print("  ⚠  Jalankan generate_cert.py untuk PWA dari perangkat lain.")
    print("=" * 58)

    threading.Thread(target=_build_library, args=(False,), daemon=True).start()

    # Backend pakai HTTP lokal — Cloudflare Tunnel yang handle HTTPS ke publik
    # SSL di backend tidak diperlukan dan bisa menyebabkan handshake issue
    server = ThreadingHTTPServer((config.HOST, config.PORT), Handler)

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[Server] Dihentikan.")
        server.server_close()
