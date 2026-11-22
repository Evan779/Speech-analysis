const box = document.getElementById('text'), status = document.getElementById('status');
const editBtn = document.getElementById('editBtn'), resetBtn = document.getElementById('resetBtn');
const nextBtn = document.getElementById('nextBtn'), retryBtn = document.getElementById('retryBtn');
let original = '';

async function load(force) {
  status.className = 'muted'; status.innerHTML = '<span class="spinner"></span>Transcribing with Whisper. This can take a minute…';
  box.value = ''; [editBtn, resetBtn, nextBtn].forEach(b => b.disabled = true); retryBtn.hidden = true;
  try {
    const r = await api('/api/transcribe?force=' + force, { method: 'POST' });
    original = r.text; box.value = r.text;
    status.textContent = 'Check the transcript. Press Edit to fix anything Whisper got wrong.';
    [editBtn, resetBtn, nextBtn].forEach(b => b.disabled = false);
  } catch (e) {
    status.className = 'error'; status.textContent = e.message; retryBtn.hidden = false;
  }
}

editBtn.onclick = () => {
  const editing = box.readOnly;
  box.readOnly = !editing;
  editBtn.textContent = editing ? 'Lock text' : 'Edit text';
  if (editing) box.focus();
};
resetBtn.onclick = () => { box.value = original; };
retryBtn.onclick = () => load(true);
nextBtn.onclick = async () => {
  nextBtn.disabled = true;
  try { location.href = (await api('/api/transcript', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ text: box.value })
  })).redirect; } catch (e) { status.className = 'error'; status.textContent = e.message; nextBtn.disabled = false; }
};
load(false);
