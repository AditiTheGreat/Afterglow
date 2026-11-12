
const config = window.AFTERGLOW_CONFIG;

const authButton = document.createElement("button");
authButton.id = "spotify-connect";
authButton.textContent = "CONNECT SPOTIFY";
authButton.className = "spotify-status";
authButton.type = "button";
document.getElementById("connection-stack").prepend(authButton);

const scopes = "user-read-currently-playing user-read-playback-state user-modify-playback-state";
const storage = window.localStorage;
let currentIsPlaying = false;

function randomString(length) {
  const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
  const bytes = crypto.getRandomValues(new Uint8Array(length));
  return Array.from(bytes, b => chars[b % chars.length]).join("");
}

async function challengeFromVerifier(verifier) {
  const data = new TextEncoder().encode(verifier);
  const digest = await crypto.subtle.digest("SHA-256", data);

  return btoa(String.fromCharCode(...new Uint8Array(digest)))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

async function login() {
  const verifier = randomString(64);
  const challenge = await challengeFromVerifier(verifier);
  const state = randomString(16);

  storage.setItem("afterglow_verifier", verifier);
  storage.setItem("afterglow_state", state);

  const params = new URLSearchParams({
    client_id: config.spotifyClientId,
    response_type: "code",
    redirect_uri: config.redirectUri,
    scope: scopes,
    state,
    code_challenge_method: "S256",
    code_challenge: challenge
  });

  window.location.href =
    "https://accounts.spotify.com/authorize?" + params.toString();
}

async function handleCallback() {
  const params = new URLSearchParams(window.location.search);
  const code = params.get("code");
  const returnedState = params.get("state");
  const error = params.get("error");

  if (error) {
    authButton.textContent = "SPOTIFY LOGIN FAILED";
    console.error("Spotify authorization:", error);
    return;
  }

  if (!code) return;

  const savedState = storage.getItem("afterglow_state");
  const verifier = storage.getItem("afterglow_verifier");

  if (!savedState || returnedState !== savedState || !verifier) {
    authButton.textContent = "AUTHENTICATION ERROR";
    return;
  }

  const response = await fetch("https://accounts.spotify.com/api/token", {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded"
    },
    body: new URLSearchParams({
      client_id: config.spotifyClientId,
      grant_type: "authorization_code",
      code,
      redirect_uri: config.redirectUri,
      code_verifier: verifier
    })
  });

  const data = await response.json();

  if (!response.ok) {
    console.error("Spotify token error:", data);
    authButton.textContent = "TOKEN ERROR";
    return;
  }

  storage.setItem("afterglow_access_token", data.access_token);
  storage.setItem("afterglow_controls_authorized", "true");
  storage.setItem(
    "afterglow_token_expiry",
    String(Date.now() + data.expires_in * 1000)
  );

  storage.removeItem("afterglow_verifier");
  storage.removeItem("afterglow_state");

  history.replaceState({}, document.title, window.location.pathname);
  authButton.textContent = "SPOTIFY CONNECTED";
  getCurrentlyPlaying();
}



async function getCurrentlyPlaying() {
  const token = storage.getItem("afterglow_access_token");
  const expiry = Number(storage.getItem("afterglow_token_expiry") || 0);

  if (!token || Date.now() >= expiry) {
    authButton.textContent = "RECONNECT SPOTIFY";
    return;
  }

  try {
    const response = await fetch(
      "https://api.spotify.com/v1/me/player/currently-playing",
      {
        headers: { Authorization: `Bearer ${token}` }
      }
    );

    if (response.status === 204) return;
    if (!response.ok) throw new Error(`Spotify API: ${response.status}`);

    const data = await response.json();
    const track = data.item;

    if (!track) return;

    currentIsPlaying = Boolean(data.is_playing);
    window.dispatchEvent(new CustomEvent("afterglow:playback", {
      detail: { isPlaying: currentIsPlaying }
    }));

    window.dispatchEvent(new CustomEvent("afterglow:track", {
      detail: {
        id: track.id,
        title: track.name,
        artist: track.artists.map(a => a.name).join(", "),
        album: track.album.name,
        artwork: track.album.images?.[0]?.url || "",
        durationMs: track.duration_ms,
        progressMs: data.progress_ms || 0,
        isPlaying: data.is_playing
      }
    }));

  } catch (error) {
    console.error("Afterglow Spotify error:", error);
  }
}

async function spotifyPlaybackRequest(path, method) {
  const token = storage.getItem("afterglow_access_token");

  if (!token) {
    authButton.textContent = "CONNECT SPOTIFY";
    throw new Error("Connect Spotify first.");
  }

  const response = await fetch("https://api.spotify.com/v1/me/player" + path, {
    method,
    headers: { Authorization: `Bearer ${token}` }
  });

  if (!response.ok) {
    const detail = await response.text();
    throw new Error(`Spotify playback error ${response.status}: ${detail}`);
  }
}

async function runPlaybackAction(action) {
  try {
    if (action === "toggle") {
      const shouldPlay = !currentIsPlaying;
      await spotifyPlaybackRequest(
        shouldPlay ? "/play" : "/pause",
        "PUT"
      );
      currentIsPlaying = shouldPlay;
      updatePlayButton();
    } else {
      await spotifyPlaybackRequest(
        action === "next" ? "/next" : "/previous",
        "POST"
      );
    }

    setTimeout(getCurrentlyPlaying, 900);
  } catch (error) {
    console.error("AFTERGLOW playback:", error);
    const button = document.getElementById("play-toggle");
    if (button) button.title = error.message;
  }
}

function updatePlayButton() {
  const button = document.getElementById("play-toggle");
  if (!button) return;

  button.textContent = currentIsPlaying ? "Ⅱ" : "▶";
  button.title = currentIsPlaying ? "Pause Spotify" : "Play Spotify";
  button.setAttribute("aria-label", button.title);
}

document.getElementById("play-toggle")?.addEventListener("click", () => {
  runPlaybackAction("toggle");
});

document.getElementById("previous-track")?.addEventListener("click", () => {
  runPlaybackAction("previous");
});

document.getElementById("next-track")?.addEventListener("click", () => {
  runPlaybackAction("next");
});

window.addEventListener("afterglow:playback", event => {
  currentIsPlaying = Boolean(event.detail?.isPlaying);
  updatePlayButton();
});

authButton.addEventListener("click", login);

(async () => {
  await handleCallback();

  if (storage.getItem("afterglow_access_token")) {
    authButton.textContent =
      storage.getItem("afterglow_controls_authorized") === "true"
        ? "SPOTIFY CONNECTED"
        : "RECONNECT FOR CONTROLS";
    getCurrentlyPlaying();
    setInterval(getCurrentlyPlaying, 5000);
  }
})();
