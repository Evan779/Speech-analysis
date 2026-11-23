const MAX_SECONDS = 60;
const recBtn = document.getElementById('recBtn'), timer = document.getElementById('timer');
const barFill = document.querySelector('.bar i'), preview = document.getElementById('preview');
const player = document.getElementById('player'), msg = document.getElementById('msg');
const sendBtn = document.getElementById('sendBtn'), redoBtn = document.getElementById('redoBtn');

let recorder, stream, chunks = [], blob, fileName = null, startedAt, tick;
const fileInput = document.getElementById('fileInput'), uploadBtn = document.getElementById('uploadBtn');
const MAX_BYTES = 25 * 1024 * 1024;

const pickMime = () => ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4', 'audio/ogg']
  .find(t => window.MediaRecorder && MediaRecorder.isTypeSupported(t)) || '';
const fmt = s => `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(Math.floor(s % 60)).padStart(2, '0')}`;

async function start() {
  msg.textContent = ''; fileInput.value = '';
  try {
    stream = await navigator.mediaDevices.getUserMedia({ audio: { channelCount: 1, echoCancellation: true } });
  } catch (e) {
    msg.className = 'error'; msg.textContent = 'Microphone blocked. Allow microphone access in your browser and try again.'; return;
  }
  chunks = [];
  recorder = new MediaRecorder(stream, pickMime() ? { mimeType: pickMime() } : undefined);
  recorder.ondataavailable = e => e.data.size && chunks.push(e.data);
  recorder.onstop = onStop;
  recorder.start();
  startedAt = Date.now();
  recBtn.textContent = 'Stop'; recBtn.classList.add('live'); preview.hidden = true;
  tick = setInterval(() => {
    const s = (Date.now() - startedAt) / 1000;
    timer.textContent = `${fmt(s)} / ${fmt(MAX_SECONDS)}`;
    barFill.style.width = Math.min(100, s / MAX_SECONDS * 100) + '%';
    if (s >= MAX_SECONDS) stop();   // hard cap at 60 s
  }, 200);
}

function stop() {
  if (recorder && recorder.state !== 'inactive') recorder.stop();
  clearInterval(tick);
  stream.getTracks().forEach(t => t.stop());
  recBtn.textContent = 'Record'; recBtn.classList.remove('live');
}

function onStop() {
  fileName = null;
  blob = new Blob(chunks, { type: recorder.mimeType || 'audio/webm' });
  player.src = URL.createObjectURL(blob);
  preview.hidden = false;
}

recBtn.onclick = () => (recorder && recorder.state === 'recording') ? stop() : start();
redoBtn.onclick = () => { fileInput.value = ''; fileName = null; msg.textContent = ''; preview.hidden = true; timer.textContent = `00:00 / ${fmt(MAX_SECONDS)}`; barFill.style.width = '0'; };

sendBtn.onclick = async () => {
  sendBtn.disabled = true; msg.className = 'muted'; msg.textContent = 'Uploading…';
  const ext = blob.type.includes('mp4') ? 'mp4' : blob.type.includes('ogg') ? 'ogg' : 'webm';
  const fd = new FormData(); fd.append('audio', blob, fileName || `recording.${ext}`);
  try {
    const r = await api('/api/recording', { method: 'POST', body: fd });
    location.href = r.redirect;
  } catch (e) { msg.className = 'error'; msg.textContent = e.message; sendBtn.disabled = false; }
};
timer.textContent = `00:00 / ${fmt(MAX_SECONDS)}`;

// ---- upload an existing file ----
function reject(text) { msg.className = 'error'; msg.textContent = text; fileInput.value = ''; }
function accept(file, url) {
  blob = file; fileName = file.name; player.src = url;
  msg.className = 'muted'; msg.textContent = `Selected: ${file.name}`;
  preview.hidden = false;
}
uploadBtn.onclick = () => { if (!(recorder && recorder.state === 'recording')) fileInput.click(); };
fileInput.onchange = () => {
  const file = fileInput.files[0];
  if (!file) return;
  if (file.size > MAX_BYTES) return reject('That file is larger than 25 MB.');
  const url = URL.createObjectURL(file), probe = new Audio();
  probe.preload = 'metadata';
  probe.onloadedmetadata = () => {
    if (isFinite(probe.duration) && probe.duration > MAX_SECONDS + 1)
      return reject(`That file is ${Math.round(probe.duration)} seconds long. The limit is ${MAX_SECONDS}.`);
    accept(file, url);
  };
  probe.onerror = () => accept(file, url);   // browser can't preview it; the server (ffmpeg) will check it
  probe.src = url;
};