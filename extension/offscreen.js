let audioContext;
let analyser;
let audioStream;
let source;
let gainNode;
let sendInterval;

async function startCapture(streamId) {
  try {
    await stopCapture();

    audioStream = await navigator.mediaDevices.getUserMedia({
      audio: {
        mandatory: {
          chromeMediaSource: "tab",
          chromeMediaSourceId: streamId
        }
      },
      video: false
    });

    audioContext = new AudioContext();
    await audioContext.resume();

    source = audioContext.createMediaStreamSource(audioStream);
    analyser = audioContext.createAnalyser();

    analyser.fftSize = 2048;
    analyser.smoothingTimeConstant = 0.8;

    source.connect(analyser);

    // Adjustable output gain, after analysis.
    gainNode = audioContext.createGain();
    gainNode.gain.value = 1;
    analyser.connect(gainNode);
    gainNode.connect(audioContext.destination);

    const rawData = new Uint8Array(analyser.frequencyBinCount);

    sendInterval = setInterval(() => {
      if (!analyser) return;

      analyser.getByteFrequencyData(rawData);

      // Reduce 1024 frequency bins to 128 lightweight values.
      const frequencies = [];

      for (let i = 0; i < 128; i++) {
        let total = 0;

        for (let j = 0; j < 8; j++) {
          total += rawData[i * 8 + j];
        }

        frequencies.push(total / 8);
      }

      const average = (start, end) => {
        let total = 0;

        for (let i = start; i < end; i++) {
          total += frequencies[i];
        }

        return total / (end - start);
      };

      chrome.runtime.sendMessage({
        type: "AUDIO_DATA",
        frequencies,
        bass: average(0, 10),
        mids: average(10, 60),
        treble: average(60, 128),
        rawPeak: Math.max(...rawData),
        contextState: audioContext.state,
        trackState: audioStream.getAudioTracks().map(t => t.readyState)
      }).catch(() => {});
    }, 33);

    chrome.runtime.sendMessage({
      type: "CAPTURE_STARTED"
    }).catch(() => {});

  } catch (error) {
    console.error("Afterglow audio error:", error);

    chrome.runtime.sendMessage({
      type: "CAPTURE_ERROR",
      error: error.message
    }).catch(() => {});
  }
}

async function stopCapture() {
  if (sendInterval) {
    clearInterval(sendInterval);
    sendInterval = null;
  }

  if (audioStream) {
    audioStream.getTracks().forEach(track => track.stop());
    audioStream = null;
  }

  if (audioContext) {
    await audioContext.close();
    audioContext = null;
  }

  analyser = null;
  source = null;
  gainNode = null;
}

chrome.runtime.onMessage.addListener((message) => {
  if (message.type === "SET_CAPTURE_GAIN_OFFSCREEN" && gainNode && audioContext) {
    const value = Math.max(0, Math.min(1.5, Number(message.gain) || 0));
    gainNode.gain.setTargetAtTime(value, audioContext.currentTime, 0.025);
  }

  if (message.type === "START_CAPTURE") {
    startCapture(message.streamId);
  }

  if (message.type === "STOP_CAPTURE") {
    stopCapture();
  }
});
