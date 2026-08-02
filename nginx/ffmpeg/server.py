"""FFmpeg 转码微服务（视频压缩、审核抽帧）."""

from __future__ import annotations

import base64
import json
import logging
from logging.handlers import RotatingFileHandler
import os
import shutil
import subprocess
import tempfile
import threading
from functools import wraps

from flask import Flask, Response, abort, jsonify, request

_local_log_file = os.path.abspath(
    os.path.join(os.path.dirname(__file__), "..", "..", "logs", "ffmpeg", "ffmpeg.log")
)
_log_file = os.getenv("FFMPEG_LOG_FILE", _local_log_file)
_log_path = os.path.dirname(_log_file)
if _log_path:
    os.makedirs(_log_path, exist_ok=True)
_log_formatter = logging.Formatter("%(asctime)s [%(levelname)s] %(name)s: %(message)s")
_stream_handler = logging.StreamHandler()
_stream_handler.setFormatter(_log_formatter)
_file_handler = RotatingFileHandler(
    _log_file,
    maxBytes=20 * 1024 * 1024,
    backupCount=14,
    encoding="utf-8",
)
_file_handler.setFormatter(_log_formatter)
logging.basicConfig(
    level=logging.WARNING,
    handlers=[_stream_handler, _file_handler],
    force=True,
)
logger = logging.getLogger(__name__)

app = Flask(__name__)
_compress_lock = threading.Lock()

INTERNAL_KEY = (os.environ.get("FFMPEG_INTERNAL_KEY") or os.environ.get("FORUM_FFMPEG_INTERNAL_KEY") or "").strip()
REQUIRE_INTERNAL_KEY = os.environ.get("FFMPEG_REQUIRE_INTERNAL_KEY", "").strip().lower() in (
    "1",
    "true",
    "yes",
    "on",
)


def _env_truthy(name: str) -> bool:
    return os.environ.get(name, "").strip().lower() in ("1", "true", "yes", "on")


def _require_internal_key() -> bool:
    if REQUIRE_INTERNAL_KEY:
        return True
    return _env_truthy("FFMPEG_REQUIRE_INTERNAL_KEY") or _env_truthy("AI_REQUIRE_INTERNAL_KEY")


def _check_internal_key() -> None:
    if not _require_internal_key() and not INTERNAL_KEY:
        return
    if _require_internal_key() and not INTERNAL_KEY:
        abort(503, "ffmpeg internal key not configured")
    got = (request.headers.get("X-Internal-Key") or "").strip()
    if got != INTERNAL_KEY:
        abort(403, "invalid X-Internal-Key")


def _internal_protected(view):
    @wraps(view)
    def wrapper(*args: object, **kwargs: object):
        _check_internal_key()
        return view(*args, **kwargs)

    return wrapper


def _allowed_url_prefixes() -> list[str]:
    prefixes: list[str] = []
    oss_profile = (os.environ.get("FORUM_OSS_PROFILE") or "local").strip().lower()
    oss_env_prefix = "OSS_SERVER_URL_PREFIX" if oss_profile in {"server", "prod", "production"} else "OSS_LOCAL_URL_PREFIX"
    oss_prefix = (os.environ.get(oss_env_prefix) or "").strip().rstrip("/")
    if oss_prefix:
        prefixes.append(oss_prefix.lower())
    extra = (os.environ.get("FFMPEG_ALLOWED_URL_PREFIXES") or "").strip()
    for item in extra.split(","):
        p = item.strip().rstrip("/").lower()
        if p:
            prefixes.append(p)
    return prefixes


def _url_allowed(url: str) -> bool:
    if _env_truthy("FFMPEG_ALLOW_ANY_URL"):
        return True
    raw = (url or "").strip()
    if not raw.lower().startswith(("http://", "https://")):
        return False
    lowered = raw.lower()
    prefixes = _allowed_url_prefixes()
    if not prefixes:
        return False
    return any(lowered.startswith(p) for p in prefixes)


def _run(cmd: list[str], *, timeout: int | None = None) -> subprocess.CompletedProcess[str]:
    return subprocess.run(cmd, capture_output=True, text=True, check=False, timeout=timeout)


def _probe(in_path: str) -> dict[str, object]:
    proc = _run(
        [
            "ffprobe",
            "-v",
            "quiet",
            "-print_format",
            "json",
            "-show_streams",
            "-show_format",
            in_path,
        ],
        timeout=120,
    )
    if proc.returncode != 0:
        return {}
    try:
        return json.loads(proc.stdout or "{}")
    except json.JSONDecodeError:
        return {}


def _first_stream(probe: dict[str, object], codec_type: str) -> dict[str, object] | None:
    for s in probe.get("streams") or []:
        if s.get("codec_type") == codec_type:
            return s
    return None


def _can_remux_copy(probe: dict[str, object]) -> bool:
    v = _first_stream(probe, "video")
    if not v:
        return False
    vcodec = (v.get("codec_name") or "").lower()
    if vcodec not in ("h264", "avc"):
        return False
    a = _first_stream(probe, "audio")
    if a is None:
        return True
    acodec = (a.get("codec_name") or "").lower()
    return acodec in ("aac", "mp4a")


def _video_height(probe: dict[str, object]) -> int:
    v = _first_stream(probe, "video")
    if not v:
        return 0
    try:
        return int(v.get("height") or 0)
    except (TypeError, ValueError):
        return 0


def _transcode(in_path: str, out_path: str, *, mode: str, probe: dict[str, object]) -> None:
    if mode == "remux":
        cmd = [
            "ffmpeg",
            "-y",
            "-i",
            in_path,
            "-c",
            "copy",
            "-movflags",
            "+faststart",
            out_path,
        ]
        timeout = int(os.getenv("FFMPEG_REMUX_TIMEOUT", "600"))
    else:
        preset = os.getenv("FFMPEG_PRESET", "ultrafast")
        crf = os.getenv("FFMPEG_CRF", "28")
        max_h = int(os.getenv("FFMPEG_MAX_HEIGHT", "1080"))
        height = _video_height(probe)
        cmd = ["ffmpeg", "-y", "-i", in_path]
        if height > max_h > 0:
            cmd.extend(["-vf", f"scale=-2:{max_h}"])
        cmd.extend(
            [
                "-c:v",
                "libx264",
                "-preset",
                preset,
                "-crf",
                crf,
                "-c:a",
                "aac",
                "-b:a",
                os.getenv("FFMPEG_AUDIO_BITRATE", "128k"),
                "-movflags",
                "+faststart",
                out_path,
            ]
        )
        timeout = int(os.getenv("FFMPEG_ENCODE_TIMEOUT", "3600"))

    proc = _run(cmd, timeout=timeout)
    if proc.returncode != 0:
        err = (proc.stderr or proc.stdout or "")[-800:]
        logger.error("ffmpeg %s failed code=%s stderr=%s", mode, proc.returncode, err)
        abort(500, f"ffmpeg {mode} failed")


@app.post("/compress")
@_internal_protected
def compress() -> Response:
    if not request.files or "file" not in request.files:
        abort(400, "missing file")
    f = request.files["file"]
    if not f.filename:
        abort(400, "empty filename")

    if not _compress_lock.acquire(blocking=False):
        logger.warning("compress busy, reject name=%s", f.filename)
        return jsonify({"error": "compress_busy", "message": "已有视频正在处理，请稍后再试"}), 503

    tmp_dir = tempfile.mkdtemp(prefix="ffmpeg-")
    in_path = os.path.join(tmp_dir, "in")
    out_path = os.path.join(tmp_dir, "out.mp4")
    try:
        f.save(in_path)
        probe = _probe(in_path)
        mode = "remux" if _can_remux_copy(probe) else "reencode"
        _transcode(in_path, out_path, mode=mode, probe=probe)

        if not os.path.isfile(out_path) or os.path.getsize(out_path) == 0:
            logger.error("ffmpeg produced empty output mode=%s", mode)
            abort(500, "empty output")

        with open(out_path, "rb") as out_f:
            data = out_f.read()
        return Response(data, mimetype="video/mp4")
    except subprocess.TimeoutExpired:
        logger.error("compress timeout name=%s", f.filename)
        abort(504, "ffmpeg timeout")
    except Exception:
        logger.exception("compress error name=%s", f.filename)
        raise
    finally:
        shutil.rmtree(tmp_dir, ignore_errors=True)
        _compress_lock.release()


@app.get("/healthz")
def healthz() -> dict[str, bool]:
    return {"ok": True}


def _probe_url(url: str) -> dict[str, object]:
    proc = _run(
        [
            "ffprobe",
            "-v",
            "quiet",
            "-print_format",
            "json",
            "-show_format",
            url,
        ],
        timeout=120,
    )
    if proc.returncode != 0:
        return {}
    try:
        return json.loads(proc.stdout or "{}")
    except json.JSONDecodeError:
        return {}


@app.post("/extract-audit-frames")
@_internal_protected
def extract_audit_frames() -> Response:
    """从视频 URL 抽取若干 JPEG 帧，供 ai-server 视频审核兜底（OSS 私有桶需传签名 URL）。"""
    data = request.get_json(force=True, silent=True) or {}
    url = (data.get("url") or "").strip()
    if not url:
        abort(400, "missing url")
    if not _url_allowed(url):
        abort(403, "url not allowed")
    try:
        count = int(data.get("count") or 4)
    except (TypeError, ValueError):
        count = 4
    count = max(1, min(count, 8))

    probe = _probe_url(url)
    try:
        duration = float((probe.get("format") or {}).get("duration") or 0)
    except (TypeError, ValueError):
        duration = 0.0
    if duration <= 0:
        duration = 30.0

    tmp_dir = tempfile.mkdtemp(prefix="ffmpeg-frames-")
    frames_b64: list[str] = []
    try:
        for i in range(count):
            t = max(0.0, (duration * (i + 0.5) / count) - 0.05)
            out_path = os.path.join(tmp_dir, f"f{i}.jpg")
            cmd = [
                "ffmpeg",
                "-y",
                "-ss",
                f"{t:.3f}",
                "-i",
                url,
                "-frames:v",
                "1",
                "-q:v",
                "2",
                out_path,
            ]
            proc = _run(cmd, timeout=int(os.getenv("FFMPEG_FRAME_TIMEOUT", "180")))
            if proc.returncode != 0:
                logger.warning("extract frame %s failed: %s", i, (proc.stderr or "")[-200:])
                continue
            if not os.path.isfile(out_path) or os.path.getsize(out_path) == 0:
                continue
            with open(out_path, "rb") as f:
                frames_b64.append(base64.b64encode(f.read()).decode("ascii"))
        if not frames_b64:
            abort(422, "no frames extracted")
        return jsonify({"frames": frames_b64, "count": len(frames_b64)})
    finally:
        shutil.rmtree(tmp_dir, ignore_errors=True)
