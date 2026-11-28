"""
Audio service: Whisper transcription + acoustic speech-fluency analysis.

Run:  uvicorn app:app --port 8000
Needs ffmpeg on PATH (Whisper and the webm/mp4 -> wav conversion both use it).
"""
import json
import os
import re
import subprocess
import tempfile
from difflib import SequenceMatcher
from typing import List, Optional

import librosa
import numpy as np
import parselmouth
import whisper
from fastapi import FastAPI, File, Form, HTTPException, UploadFile

WHISPER_MODEL = os.getenv("WHISPER_MODEL", "base")   # tiny / base / small / medium / large
SR = 16000                                            # analysis sample rate
MIN_PAUSE = float(os.getenv("MIN_PAUSE", "0.30"))     # seconds of silence that counts as a pause
SILENCE_DB_BELOW_PEAK = float(os.getenv("SILENCE_DB", "25"))  # Praat-style: peak intensity minus N dB
CLAUSE_END = re.compile(r"[,;:.!?\u2014\u2013-]$")    # punctuation that closes a clause
# Matches common filled pauses; Whisper tends to drop them, so the prompt below nudges it to keep them.
FILLER_RE = re.compile(r"\b(?:u+m+|u+h+m*|e+r+m*|h+m+|m+h?m+)\b", re.I)
FILLER_PROMPT = "Umm, uh, so, um, I was, uh, thinking... Hmm, well, you know, er, it's like that."

app = FastAPI(title="Voice analysis service")
_model = None


def get_model():
    global _model
    if _model is None:
        _model = whisper.load_model(WHISPER_MODEL)
    return _model


async def to_wav(upload: UploadFile) -> str:
    """Save the upload and convert to 16 kHz mono WAV (browsers send webm/opus or mp4)."""
    suffix = os.path.splitext(upload.filename or "")[1] or ".webm"
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as src:
        src.write(await upload.read())
    wav = src.name + ".wav"
    try:
        subprocess.run(["ffmpeg", "-y", "-i", src.name, "-ac", "1", "-ar", str(SR), wav],
                       check=True, capture_output=True)
    except (subprocess.CalledProcessError, FileNotFoundError) as e:
        raise HTTPException(400, f"Could not decode audio (is ffmpeg installed?): {e}")
    finally:
        os.unlink(src.name)
    return wav


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/transcribe")
async def transcribe(file: UploadFile = File(...), language: Optional[str] = Form(None)):
    wav = await to_wav(file)
    try:
        result = get_model().transcribe(
            wav, word_timestamps=True, initial_prompt=FILLER_PROMPT,
            language=language, fp16=False, condition_on_previous_text=False)
    finally:
        os.unlink(wav)
    words = [{"word": w["word"].strip(), "start": round(float(w["start"]), 3), "end": round(float(w["end"]), 3)}
             for seg in result["segments"] for w in seg.get("words", [])]
    return {"text": result["text"].strip(), "language": result.get("language"), "words": words}


def runs(mask: np.ndarray, times: np.ndarray, dt: float, total: float):
    """Convert a boolean frame mask into [(start_s, end_s)] runs of True."""
    padded = np.concatenate([[False], mask, [False]]).astype(int)
    edges = np.flatnonzero(np.diff(padded))
    out = []
    for s, e in zip(edges[::2], edges[1::2]):
        out.append((max(0.0, times[s] - dt / 2), min(total, times[e - 1] + dt / 2)))
    return out


def speech_segments(path: str, total: float):
    """Praat intensity-based voiced/silent segmentation, merging gaps shorter than MIN_PAUSE."""
    snd = parselmouth.Sound(path)
    inten = snd.to_intensity(minimum_pitch=75, time_step=0.01)
    vals, times = inten.values[0], inten.xs()
    dt = float(times[1] - times[0]) if len(times) > 1 else 0.01
    threshold = np.percentile(vals, 95) - SILENCE_DB_BELOW_PEAK
    segs = runs(vals > threshold, times, dt, total)

    merged = []
    for s, e in segs:
        if merged and s - merged[-1][1] < MIN_PAUSE:
            merged[-1] = (merged[-1][0], e)
        else:
            merged.append((s, e))
    merged = [(s, e) for s, e in merged if e - s >= 0.05]

    # Mean pitch (Praat autocorrelation method) as a bonus metric
    f0 = snd.to_pitch().selected_array["frequency"]
    f0 = f0[f0 > 0]
    return merged, (float(np.mean(f0)) if len(f0) else None)


def _core(token: str) -> str:
    return re.sub(r"[^\w']+", "", token.lower().replace("\u2019", "'"))


def align_to_edited(words: List[dict], edited: str) -> List[dict]:
    """
    Re-label Whisper's timed words with the user's edited text, so clause punctuation
    the user fixed or added is respected. Matching words keep their real timings, replaced
    spans share the original span's time, and inserted words are spread over the gap
    between their neighbours (their exact timing is unknown).
    """
    toks = edited.split()
    if not toks or not words:
        return words
    a = [_core(w["word"]) for w in words]
    b = [_core(t) for t in toks]
    spans = [None] * len(toks)
    prev_end = 0.0
    for tag, i1, i2, j1, j2 in SequenceMatcher(None, a, b, autojunk=False).get_opcodes():
        n = j2 - j1
        if tag == "equal":
            for k in range(n):
                spans[j1 + k] = (words[i1 + k]["start"], words[i1 + k]["end"])
        elif tag == "replace":
            s0, e0 = words[i1]["start"], words[i2 - 1]["end"]
            for k in range(n):
                spans[j1 + k] = (s0 + (e0 - s0) * k / n, s0 + (e0 - s0) * (k + 1) / n)
        elif tag == "insert":
            nxt = words[i1]["start"] if i1 < len(words) else prev_end
            lo, hi = prev_end, max(prev_end, nxt)
            for k in range(n):
                spans[j1 + k] = (lo + (hi - lo) * k / n, lo + (hi - lo) * (k + 1) / n)
        if i2 > i1:
            prev_end = words[i2 - 1]["end"]
    return [{"word": toks[j], "start": round(spans[j][0], 3), "end": round(spans[j][1], 3)} for j in range(len(toks))]


def classify_pause(start: float, words: List[dict]) -> str:
    """'between' if the word just before the pause ends a clause (punctuation), else 'within'."""
    prev = [w for w in words if w["end"] <= start + 0.15]
    if not prev:
        return "within"
    return "between" if CLAUSE_END.search(prev[-1]["word"]) else "within"


def gap_stats(items: List[float]):
    return {"count": len(items), "total": round(sum(items), 2),
            "mean": round(sum(items) / len(items), 2) if items else 0.0}


@app.post("/analyze")
async def analyze(file: UploadFile = File(...), words: str = Form("[]"), transcript: str = Form("")):
    wav = await to_wav(file)
    try:
        y, sr = librosa.load(wav, sr=SR, mono=True)
        total = len(y) / sr
        if total < 0.5:
            raise HTTPException(400, "Recording is too short to analyse")
        segs, mean_f0 = speech_segments(wav, total)
    finally:
        os.unlink(wav)

    try:
        word_list = json.loads(words)
    except json.JSONDecodeError:
        word_list = []
    if transcript.strip():   # use the user's corrected text (punctuation included) for clause boundaries
        word_list = align_to_edited(word_list, transcript)

    speaking = sum(e - s for s, e in segs)
    silence = max(0.0, total - speaking)

    # Pauses = silent gaps *between* speech segments (leading/trailing silence is not a pause).
    pauses = []
    for (_, e1), (s2, _) in zip(segs, segs[1:]):
        if word_list:
            kind = classify_pause(e1, word_list)
        else:  # no word timings: fall back to duration heuristic
            kind = "between" if s2 - e1 >= 0.5 else "within"
        pauses.append({"start": round(e1, 2), "end": round(s2, 2), "duration": round(s2 - e1, 2), "type": kind})

    between = [p["duration"] for p in pauses if p["type"] == "between"]
    within = [p["duration"] for p in pauses if p["type"] == "within"]

    fillers = [m.group(0).lower() for m in FILLER_RE.finditer(transcript)]
    n_words = len(re.findall(r"[\w']+", transcript))

    # Waveform envelope: min/max per bin so the frontend can draw it cheaply.
    bins = 1200
    chunks = np.array_split(y, min(bins, len(y)))
    peak = float(np.max(np.abs(y))) or 1.0
    wave = {"min": [round(float(c.min()) / peak, 3) for c in chunks],
            "max": [round(float(c.max()) / peak, 3) for c in chunks]}

    return {
        "total_time": round(total, 2),
        "speaking_time": round(speaking, 2),
        "silence_time": round(silence, 2),
        "speaking_ratio": round(speaking / total, 3),
        "filled_pauses": {"count": len(fillers), "items": fillers},
        "gaps_between_clauses": gap_stats(between),
        "gaps_within_clauses": gap_stats(within),
        "word_count": n_words,
        "speech_rate_wpm": round(n_words / (speaking / 60), 1) if speaking > 0 and n_words else None,
        "mean_pitch_hz": round(mean_f0, 1) if mean_f0 else None,
        "pauses": pauses,
        "speech_segments": [[round(s, 2), round(e, 2)] for s, e in segs],
        "waveform": wave,
        "params": {"min_pause_s": MIN_PAUSE, "silence_threshold_db_below_peak": SILENCE_DB_BELOW_PEAK},
    }
