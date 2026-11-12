chrome.runtime.onMessage.addListener((message) => {
  if (message.type === "AUDIO_DATA") {
    window.postMessage({
      source: "AFTERGLOW",
      type: "AUDIO_DATA",
      frequencies: message.frequencies,
      bass: message.bass,
      mids: message.mids,
      treble: message.treble,
      rawPeak: message.rawPeak,
      contextState: message.contextState,
      trackState: message.trackState
    }, "*");
  }

  if (message.type === "CAPTURE_STARTED") {
    window.postMessage({
      source: "AFTERGLOW",
      type: "CAPTURE_STARTED"
    }, "*");
  }

  if (message.type === "CAPTURE_ERROR") {
    window.postMessage({
      source: "AFTERGLOW",
      type: "CAPTURE_ERROR",
      error: message.error
    }, "*");
  }
});


window.addEventListener("message", event => {
  if (event.source !== window || event.data?.source !== "AFTERGLOW_UI") return;

  if (event.data.type === "SET_GAIN") {
    chrome.runtime.sendMessage({
      type: "SET_CAPTURE_GAIN",
      gain: event.data.gain
    }).catch(() => {});
  }
});
