const status = document.getElementById("status");

document.getElementById("start").addEventListener("click", async () => {
  status.textContent = "Connecting to Spotify...";

  try {
    const response = await chrome.runtime.sendMessage({
      type: "START_CAPTURE_REQUEST"
    });

    if (response?.ok) {
      status.textContent = "Audio capture started!";
    } else {
      status.textContent = response?.error || "Capture failed.";
    }
  } catch (error) {
    status.textContent = error.message;
  }
});

document.getElementById("stop").addEventListener("click", async () => {
  await chrome.runtime.sendMessage({ type: "STOP_CAPTURE" });
  status.textContent = "Capture stopped.";
});
