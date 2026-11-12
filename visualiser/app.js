const canvas = document.getElementById("visualizer");
const ctx = canvas.getContext("2d");
const orb = document.getElementById("orb");
const glow = document.querySelector(".orb-glow");
const barsContainer = document.getElementById("equalizer");
const connection = document.getElementById("connection");
const level = document.getElementById("audio-level");

let frequencies = new Array(128).fill(0);
let smoothFrequencies = new Array(128).fill(0);
let bass = 0, mids = 0, treble = 0;
let smoothBass = 0;
let bassHistory = [];
let beatPulse = 0;
let lastBeat = 0;
let previousBass = 0;
let bassBuild = 0;
let particles = [];

function resize() {
  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  canvas.width = window.innerWidth * dpr;
  canvas.height = window.innerHeight * dpr;
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
}
window.addEventListener("resize", resize);
resize();

for (let i = 0; i < 64; i++) {
  const bar = document.createElement("div");
  bar.className = "bar";
  barsContainer.appendChild(bar);
}

for (let i = 0; i < 90; i++) {
  particles.push({
    x: Math.random(),
    y: Math.random(),
    size: Math.random() * 1.7 + .4,
    speed: Math.random() * .00012 + .000035,
    phase: Math.random() * Math.PI * 2
  });
}

window.addEventListener("message", event => {
  if (event.source !== window || event.data?.source !== "AFTERGLOW") return;

  const data = event.data;

  if (data.type === "AUDIO_DATA") {
    frequencies = data.frequencies || frequencies;
    bass = Number(data.bass) || 0;
    mids = Number(data.mids) || 0;
    treble = Number(data.treble) || 0;
    connection.textContent = "LIVE AUDIO CONNECTED";

    const now = performance.now();

    const average = bassHistory.length
      ? bassHistory.reduce((a, b) => a + b, 0) / bassHistory.length
      : bass;

    const rise = bass - previousBass;
    const threshold = Math.max(3, average * 1.02, smoothBass * 1.03);

    if (
      bass > threshold &&
      rise > Math.max(0.8, average * 0.015) &&
      now - lastBeat > 350
    ) {
      const strength = Math.max(
        0,
        Math.min((bass - threshold) / Math.max(threshold * 1.2, 8), 1)
      );

      beatPulse = Math.max(beatPulse, 0.90 + strength * 0.10);
      lastBeat = now;
    }

    bassHistory.push(bass);
    if (bassHistory.length > 60) bassHistory.shift();

    previousBass = bass;
  }

  if (data.type === "CAPTURE_STARTED") {
    connection.textContent = "CAPTURE STARTED";
  }

  if (data.type === "CAPTURE_ERROR") {
    connection.textContent = "AUDIO ERROR";
  }
});


function drawBackground(time) {
  const w = window.innerWidth;
  const h = window.innerHeight;
  const cx = w / 2;
  const cy = h * 0.39;

  ctx.save();
  ctx.globalCompositeOperation = "screen";

  // 1. SWEEPING DJ LASER BEAMS
  for (let i = 0; i < 8; i++) {
    const angle =
      (i * Math.PI / 4) + time * 0.00035;

    const length = Math.max(w, h) * 0.9;
    const x2 = cx + Math.cos(angle) * length;
    const y2 = cy + Math.sin(angle) * length;

    const beam = ctx.createLinearGradient(cx, cy, x2, y2);
    beam.addColorStop(0, "rgba(190,100,255,0)");
    beam.addColorStop(0.45, "rgba(190,100,255,0.16)");
    beam.addColorStop(1, "rgba(255,80,210,0)");

    ctx.beginPath();
    ctx.moveTo(cx, cy);
    ctx.lineTo(x2, y2);
    ctx.strokeStyle = beam;
    ctx.lineWidth = 9;
    ctx.stroke();
  }

  // 2. RADIAL FREQUENCY EXPLOSION
  ctx.save();
  ctx.translate(cx, cy);

  for (let i = 0; i < 72; i++) {
    const index = Math.floor(i * 128 / 72);
    const value = Math.min(
      (smoothFrequencies[index] || frequencies[index] || 0) / 255,
      1
    );

    const angle = (i / 72) * Math.PI * 2;
    const inner = Math.min(w, h) * 0.13;
    const outer = inner + value * 105;

    ctx.beginPath();
    ctx.moveTo(
      Math.cos(angle) * inner,
      Math.sin(angle) * inner
    );
    ctx.lineTo(
      Math.cos(angle) * outer,
      Math.sin(angle) * outer
    );

    ctx.strokeStyle =
      `hsla(${275 + value * 55}, 100%, 75%, ${0.25 + value * 0.7})`;

    ctx.lineWidth = 2 + value * 3;
    ctx.stroke();
  }

  // 3. PULSING CONCENTRIC RINGS
  for (let i = 0; i < 4; i++) {
    const radius =
      Math.min(w, h) * (0.15 + i * 0.055) +
      beatPulse * (18 + i * 5);

    ctx.beginPath();
    ctx.arc(0, 0, radius, 0, Math.PI * 2);
    ctx.strokeStyle =
      `rgba(194, 128, 255, ${0.10 + beatPulse * 0.3})`;
    ctx.lineWidth = 1.5;
    ctx.stroke();
  }

  ctx.restore();

  // 4. FLOATING PARTICLES
  particles.forEach(p => {
    p.y -= p.speed * (1 + Math.min(bass / 80, 2));

    if (p.y < 0) p.y = 1;

    const shimmer =
      0.25 + Math.sin(time * 0.001 + p.phase) * 0.3;

    ctx.beginPath();
    ctx.arc(p.x * w, p.y * h, p.size, 0, Math.PI * 2);
    ctx.fillStyle = `rgba(224,190,255,${shimmer})`;
    ctx.fill();
  });

  ctx.restore();
}

function animate(time) {
  const w = window.innerWidth;
  const h = window.innerHeight;

  ctx.clearRect(0, 0, w, h);
  drawBackground(time);

  smoothBass += (bass - smoothBass) * .22;
  beatPulse *= 0.94;

  const energy = Math.min(smoothBass / 55, 1);
  const frequencyEnergy = Math.min(
    Math.max(mids, treble) / 255,
    1
  );

  // Slow musical buildup, followed by a stronger transient bloom.
  const buildupTarget = Math.min(smoothBass / 75, 1);
  bassBuild += (buildupTarget - bassBuild) *
    (buildupTarget > bassBuild ? 0.035 : 0.012);

  const ambientGlow = Math.min(
    0.12 + beatPulse * 0.88,
    1
  );

  document.documentElement.style.setProperty(
    "--beat-glow",
    ambientGlow.toFixed(3)
  );

  document.documentElement.style.setProperty(
    "--audio-level",
    energy.toFixed(3)
  );

  document.documentElement.style.setProperty(
    "--frequency-level",
    frequencyEnergy.toFixed(3)
  );

  document.documentElement.style.setProperty(
    "--frequency-glow",
    `${frequencyEnergy * 35}px`
  );

  document.documentElement.style.setProperty(
    "--beat-flash",
    beatPulse.toFixed(3)
  );

  const bars = barsContainer.children;

  for (let i = 0; i < bars.length; i++) {
    const target = frequencies[i * 2] || 0;
    smoothFrequencies[i] += (target - smoothFrequencies[i]) * .35;

    const value = smoothFrequencies[i];
    const height = Math.max(3, Math.min(56, value * .22));
    bars[i].style.height = `${height}px`;
    bars[i].style.opacity = .55 + Math.min(value / 255, .45);
  }

  level.textContent =
    `BASS ${String(Math.round(bass)).padStart(2, "0")} · ` +
    `MID ${String(Math.round(mids)).padStart(2, "0")} · ` +
    `HIGH ${String(Math.round(treble)).padStart(2, "0")}`;

  requestAnimationFrame(animate);
}



const volumeControl = document.getElementById("volume-control");
const brightnessControl = document.getElementById("brightness-control");

volumeControl?.addEventListener("input", () => {
  const gain = Number(volumeControl.value) / 100;

  window.postMessage({
    source: "AFTERGLOW_UI",
    type: "SET_GAIN",
    gain
  }, "*");
});

function updateVisualBrightness() {
  const brightness = Number(brightnessControl?.value || 100) / 100;
  document.documentElement.style.setProperty(
    "--visual-brightness",
    brightness.toFixed(2)
  );
}

brightnessControl?.addEventListener("input", updateVisualBrightness);
updateVisualBrightness();

const fullscreenButton = document.getElementById("fullscreen");

fullscreenButton.onclick = async () => {
  try {
    if (!document.fullscreenElement) {
      await document.documentElement.requestFullscreen();
    } else {
      await document.exitFullscreen();
    }
  } catch (error) {
    console.error("AFTERGLOW fullscreen error:", error);
  }
};

document.addEventListener("fullscreenchange", () => {
  fullscreenButton.textContent = document.fullscreenElement
    ? "EXIT FULLSCREEN ↙"
    : "ENTER FULLSCREEN ↗";
  window.AfterglowNative?.setFullscreen(Boolean(document.fullscreenElement));
});

requestAnimationFrame(animate);
