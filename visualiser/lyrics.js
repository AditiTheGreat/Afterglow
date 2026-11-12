
const titleEl = document.getElementById("track-title");
const artistEl = document.getElementById("track-artist");
const albumArt = document.getElementById("album-art");
const albumBackdrop = document.getElementById("album-backdrop");

const lyricsPanel = document.getElementById("lyrics-panel");
const lyricLines = document.getElementById("lyrics-lines");
const floatingLyrics = document.getElementById("floating-lyrics");
const lyricsMode = document.getElementById("lyrics-mode");

let lyrics = [];
let activeTrackId = null;
let lyricsRequestId = 0;
const lyricsCache = new Map();
let currentIndex = -1;

function applyArtworkPalette(imageUrl) {
  const image = new Image();
  image.crossOrigin = "anonymous";

  image.onload = () => {
    try {
      const canvas = document.createElement("canvas");
      canvas.width = 32;
      canvas.height = 32;

      const context = canvas.getContext("2d", { willReadFrequently: true });
      context.drawImage(image, 0, 0, 32, 32);

      const pixels = context.getImageData(0, 0, 32, 32).data;
      let red = 0, green = 0, blue = 0, totalWeight = 0;

      for (let i = 0; i < pixels.length; i += 16) {
        const r = pixels[i];
        const g = pixels[i + 1];
        const b = pixels[i + 2];

        const maximum = Math.max(r, g, b);
        const minimum = Math.min(r, g, b);

        if (maximum < 38) continue;

        const saturation = (maximum - minimum) / Math.max(maximum, 1);
        const weight = 0.35 + saturation;

        red += r * weight;
        green += g * weight;
        blue += b * weight;
        totalWeight += weight;
      }

      if (!totalWeight) return;

      const color = [
        Math.round(red / totalWeight),
        Math.round(green / totalWeight),
        Math.round(blue / totalWeight)
      ];

      document.documentElement.style.setProperty(
        "--cover-rgb",
        color.join(", ")
      );
    } catch (error) {
      console.warn("AFTERGLOW: Could not extract album colours.", error);
    }
  };

  image.onerror = () => {
    console.warn("AFTERGLOW: Album palette unavailable.");
  };

  image.src = imageUrl;
}


// Floating lyrics are enabled by default
document.body.classList.add("floating-mode");
if (lyricsMode) lyricsMode.textContent = "NORMAL MODE ↙";
floatingLyrics?.classList.add("visible");

let playback = {
  progressMs: 0,
  updatedAt: 0,
  isPlaying: false
};

function updateTrackUI(track) {
  if (titleEl) titleEl.textContent = track.title || "Unknown track";
  if (artistEl) artistEl.textContent = track.artist || "Unknown artist";

  if (track.artwork) {
    if (albumArt) albumArt.src = track.artwork;
    if (albumBackdrop) {
      albumBackdrop.style.backgroundImage = `url("${track.artwork}")`;
      albumBackdrop.style.opacity = "1";
      applyArtworkPalette(track.artwork);
    }

    document.getElementById("album-art-wrap")?.classList.add("visible");
  }
}

async function fetchLyrics(track) {
  const requestId = ++lyricsRequestId;
  const trackKey = track.id || `${track.title}-${track.artist}`;

  lyrics = [];
  currentIndex = -1;
  renderLyrics();

  if (lyricsCache.has(trackKey)) {
    lyrics = lyricsCache.get(trackKey);
    renderLyrics();
    return;
  }

  try {
    const params = new URLSearchParams({
      track_name: track.title,
      artist_name: track.artist,
      album_name: track.album || "",
      duration: String(Math.round((track.durationMs || 0) / 1000))
    });

    const response = await fetch(
      `https://lrclib.net/api/get?${params.toString()}`
    );

    if (!response.ok) throw new Error("Lyrics not found");

    const data = await response.json();
    const loadedLyrics = [];

    if (data.syncedLyrics) {
      const pattern = /\[(\d{2}):(\d{2}(?:\.\d{1,3})?)\](.*)/g;

      for (const match of data.syncedLyrics.matchAll(pattern)) {
        const time = (Number(match[1]) * 60 + Number(match[2])) * 1000;
        loadedLyrics.push({ time, text: match[3].trim() });
      }

      loadedLyrics.sort((a, b) => a.time - b.time);
    } else if (data.plainLyrics) {
      loadedLyrics.push(
        ...data.plainLyrics
          .split("\n")
          .filter(line => line.trim())
          .map(text => ({ time: null, text }))
      );
    }

    if (requestId !== lyricsRequestId) return;

    lyricsCache.set(trackKey, loadedLyrics);

    if (lyricsCache.size > 30) {
      lyricsCache.delete(lyricsCache.keys().next().value);
    }

    lyrics = loadedLyrics;
    renderLyrics();
  } catch (error) {
    if (requestId !== lyricsRequestId) return;

    console.warn("Afterglow lyrics:", error);
    if (lyricsPanel) {
      const current = lyricLines?.querySelector(".current");
      if (current) current.textContent = "Lyrics unavailable for this track";
    }
  }
}
function renderLyrics() {
  if (!lyricLines) return;

  if (!lyrics.length) {
    lyricLines.innerHTML = `
      <p class="lyric-line previous"></p>
      <p class="lyric-line current">Finding the lyrics...</p>
      <p class="lyric-line next"></p>
    `;
    return;
  }

  const previous = lyrics[currentIndex - 1]?.text || "";
  const current = lyrics[currentIndex]?.text || "♪";
  const next = lyrics[currentIndex + 1]?.text || "";

  lyricLines.innerHTML = `
    <p class="lyric-line previous"></p>
    <p class="lyric-line current"></p>
    <p class="lyric-line next"></p>
  `;

  lyricLines.querySelector(".previous").textContent = previous;
  lyricLines.querySelector(".current").textContent = current;
  lyricLines.querySelector(".next").textContent = next;

  if (floatingLyrics) floatingLyrics.textContent = current;
}

function getEstimatedProgress() {
  if (!playback.isPlaying) return playback.progressMs;

  return playback.progressMs + (Date.now() - playback.updatedAt);
}

function syncLyrics() {
  if (lyrics.length && lyrics[0].time !== null) {
    const progress = getEstimatedProgress();
    let index = -1;

    for (let i = 0; i < lyrics.length; i++) {
      if (lyrics[i].time <= progress) index = i;
      else break;
    }

    if (index !== currentIndex) {
      currentIndex = index;
      renderLyrics();
    }
  }

  requestAnimationFrame(syncLyrics);
}

window.addEventListener("afterglow:track", event => {
  const track = event.detail;

  updateTrackUI(track);

  playback.progressMs = track.progressMs || 0;
  playback.updatedAt = Date.now();
  playback.isPlaying = Boolean(track.isPlaying);

  const trackId = track.id || `${track.title}-${track.artist}`;
  if (trackId !== activeTrackId) {
    activeTrackId = trackId;
    fetchLyrics(track);
  }
});

lyricsMode?.addEventListener("click", () => {
document.body.classList.toggle("floating-mode");

  const floating = document.body.classList.contains("floating-mode");

  if (lyricsMode) {
    lyricsMode.textContent = floating
      ? "NORMAL MODE ↙"
      : "FLOATING MODE ↗";
  }

  floatingLyrics?.classList.toggle("visible", floating);
});

requestAnimationFrame(syncLyrics);
