# whisper_input.py
import sounddevice as sd
import whisper
import numpy as np
import scipy.io.wavfile as wav
import tempfile

model = whisper.load_model("base")  # Use base for speed

def listen_and_transcribe():
    print("🎙️ Listening...")
    duration = 5  # seconds
    fs = 16000

    print("Recording...")
    audio = sd.rec(int(duration * fs), samplerate=fs, channels=1, dtype='int16')
    sd.wait()

    with tempfile.NamedTemporaryFile(delete=False, suffix=".wav") as f:
        wav.write(f.name, fs, audio)
        print(f"Saved temporary audio to {f.name}")
        result = model.transcribe(f.name)

    return result["text"]
