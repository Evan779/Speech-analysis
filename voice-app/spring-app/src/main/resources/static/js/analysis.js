const $ = id => document.getElementById(id);
const sec = v => v.toFixed(1) + ' s';
let TOTAL = 1;  // MediaRecorder files often report audio.duration = Infinity, so use the server's value

async function run() {
  try {
    const src = document.body.dataset.src;   // set on the history page: load a saved analysis
    const d = src ? await api(src) : await api('/api/analyze', { method: 'POST' });
    $('loading').hidden = true; $('results').hidden = false;
    TOTAL = d.total_time; render(d); drawWave(d);
    addEventListener('resize', () => drawWave(d));
  } catch (e) {
    $('loading').className = 'notice bad'; $('loading').textContent = e.message;
  }
}

function tile(value, label, note) {
  return `<div class="metric"><b>${value}</b><span>${label}</span>${note ? `<small>${note}</small>` : ''}</div>`;
}

function render(d) {
  const b = d.gaps_between_clauses, w = d.gaps_within_clauses, f = d.filled_pauses;
  $('metrics').innerHTML = [
    tile(sec(d.total_time), 'Total time'),
    tile(sec(d.speaking_time), 'Total speech production', Math.round(d.speaking_ratio * 100) + '% of the recording'),
    tile(sec(d.silence_time), 'Total silence time', 'Includes leading and trailing silence'),
    tile(f.count, 'Filled pauses', f.items.length ? [...new Set(f.items)].join(', ') : 'None found in transcript'),
    tile(b.count, 'Gaps between clauses', `${sec(b.total)} total, ${b.mean.toFixed(2)} s average`),
    tile(w.count, 'Gaps within clauses', `${sec(w.total)} total, ${w.mean.toFixed(2)} s average`),
    tile(d.word_count, 'Words', d.speech_rate_wpm ? d.speech_rate_wpm + ' words/min while speaking' : ''),
    tile(d.mean_pitch_hz ? d.mean_pitch_hz + ' Hz' : 'n/a', 'Mean pitch', 'Praat autocorrelation'),
  ].join('');
  $('pauseRows').innerHTML = d.pauses.length ? d.pauses.map(p =>
    `<tr><td>${p.start.toFixed(2)} s</td><td>${p.duration.toFixed(2)} s</td><td>${p.type === 'between' ? 'Between clauses' : 'Within clause'}</td></tr>`
  ).join('') : '<tr><td colspan="3" class="muted">No pauses of ' + d.params.min_pause_s + ' s or longer.</td></tr>';
}

function drawWave(d) {
  const cv = $('wave'), dpr = devicePixelRatio || 1;
  const W = cv.clientWidth, H = cv.clientHeight, axis = 22, mid = (H - axis) / 2;
  cv.width = W * dpr; cv.height = H * dpr;
  const g = cv.getContext('2d'); g.scale(dpr, dpr);
  const css = getComputedStyle(document.documentElement);
  const col = n => css.getPropertyValue(n).trim();
  const x = t => t / d.total_time * W;

  d.pauses.forEach(p => {
    g.fillStyle = p.type === 'between' ? col('--between') + '40' : col('--within') + '40';
    g.fillRect(x(p.start), 0, Math.max(1.5, x(p.end) - x(p.start)), H - axis);
  });
  g.strokeStyle = col('--signal'); g.lineWidth = Math.max(1, W / d.waveform.max.length);
  g.beginPath();
  d.waveform.max.forEach((mx, i) => {
    const px = (i + .5) / d.waveform.max.length * W;
    g.moveTo(px, mid - mx * mid); g.lineTo(px, mid - d.waveform.min[i] * mid);
  });
  g.stroke();
  g.fillStyle = col('--ink-2'); g.font = '12px system-ui'; g.textAlign = 'center';
  const step = d.total_time > 40 ? 10 : 5;
  for (let t = 0; t <= d.total_time; t += step) g.fillText(t + 's', Math.min(Math.max(x(t), 12), W - 12), H - 6);
}

const audio = $('player');
audio.ontimeupdate = () => {
  $('playhead').style.left = (8 + Math.min(1, audio.currentTime / TOTAL) * $('wave').clientWidth) + 'px';
};
$('wave').onclick = e => {
  const r = e.target.getBoundingClientRect();
  audio.currentTime = (e.clientX - r.left) / r.width * TOTAL;
};
run();
