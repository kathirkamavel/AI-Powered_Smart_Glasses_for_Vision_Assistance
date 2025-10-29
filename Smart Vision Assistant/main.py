# main.py
from whisper_input import listen_and_transcribe
from capture_frame import capture_frame_from_stream
from yolo_detect import detect_objects
from gemini_api import get_description_from_gemini
from tts import speak_text


if __name__ == "__main__":
    print(" Say something like: 'What is in front of me?'")
    user_query = listen_and_transcribe()
    print("You said:", user_query)

    print(" Capturing frame from Raspberry Pi stream...")
    frame_path = capture_frame_from_stream("http://192.168.54.228:8000/video_feed")

    print(" Running YOLO detection...")
    objects = detect_objects(frame_path)
    print("Objects detected:", objects)

    print(" Generating smart sentence using Gemini...")
    description = get_description_from_gemini(user_query, objects)
    print("Gemini response:", description)

    print(" Speaking out loud...")
    speak_text(description)
