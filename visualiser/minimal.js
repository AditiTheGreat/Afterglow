
(() => {
  let bassHistory = [];
  let lastBeat = 0;
  let glow = 0;
  let previousTime = performance.now();

  window.addEventListener("message", event => {
    if (event.source !== window || event.data?.source !== "AFTERGLOW") return;

    const data = event.data;
    if (data.type !== "AUDIO_DATA") return;

    const bass = Number(data.bass) || 0;

    bassHistory.push(bass);
    if (bassHistory.length > 45) bassHistory.shift();

    const average = bassHistory.reduce((a, b) => a + b, 0) /
      Math.max(1, bassHistory.length);

    const now = performance.now();

    // Detect a real bass transient instead of reacting to every sound.
    if (
      bass > Math.max(8, average * 1.22) &&
      now - lastBeat > 220
    ) {
      glow = 1;
      lastBeat = now;
    }
  });

  function animate(now) {
    const delta = Math.min(now - previousTime, 50);
    previousTime = now;

    // Smooth glow in, then a natural fade out.
    glow *= Math.exp(-delta / 430);

    if (glow < 0.005) glow = 0;

    document.documentElement.style.setProperty(
      "--beat-glow",
      glow.toFixed(3)
    );

    requestAnimationFrame(animate);
  }

  requestAnimationFrame(animate);
})();

