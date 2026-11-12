let offscreenCreating = null;

async function createOffscreenDocument() {
  const existing = await chrome.runtime.getContexts({
    contextTypes: ["OFFSCREEN_DOCUMENT"]
  });

  if (existing.length > 0) return;

  if (!offscreenCreating) {
    offscreenCreating = chrome.offscreen.createDocument({
      url: "offscreen.html",
      reasons: ["USER_MEDIA"],
      justification: "Analyse Spotify audio for the Afterglow visualiser."
    });
  }

  try {
    await offscreenCreating;
  } finally {
    offscreenCreating = null;
  }
}

async function forwardToVisualiser(message) {
  const tabs = await chrome.tabs.query({
    url: [
      "http://localhost/*",
      "http://127.0.0.1/*"
    ]
  });

  for (const tab of tabs) {
    if (tab.id) {
      chrome.tabs.sendMessage(tab.id, message).catch(() => {});
    }
  }
}

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {

  if (message.type === "START_CAPTURE_REQUEST") {
    (async () => {
      const [tab] = await chrome.tabs.query({
        active: true,
        lastFocusedWindow: true
      });

      if (!tab?.id) throw new Error("No active tab found.");

      const streamId = await chrome.tabCapture.getMediaStreamId({
        targetTabId: tab.id
      });

      await createOffscreenDocument();

      await chrome.runtime.sendMessage({
        type: "START_CAPTURE",
        streamId
      });

      sendResponse({ ok: true });
    })().catch(error => {
      sendResponse({ ok: false, error: error.message });
    });

    return true;
  }

  if (
    message.type === "AUDIO_DATA" ||
    message.type === "CAPTURE_STARTED" ||
    message.type === "CAPTURE_ERROR"
  ) {
    forwardToVisualiser(message);
  }

  if (message.type === "SET_CAPTURE_GAIN") {
    chrome.runtime.sendMessage({
      type: "SET_CAPTURE_GAIN_OFFSCREEN",
      gain: message.gain
    });
  }

  if (message.type === "STOP_CAPTURE") {
    chrome.runtime.sendMessage({ type: "STOP_CAPTURE" });
  }
});
