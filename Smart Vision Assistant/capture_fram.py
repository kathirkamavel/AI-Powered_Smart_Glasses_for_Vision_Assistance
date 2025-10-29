# capture_frame.py
import cv2
import time
import os
import uuid

def capture_frame_from_stream(stream_url):
    cap = cv2.VideoCapture(stream_url)
    if not cap.isOpened():
        raise Exception("❌ Cannot open video stream. Check URL and connection.")

    print("📷 Capturing frame...")
    time.sleep(1)  # Give time for the stream to start
    ret, frame = cap.read()
    if not ret:
        raise Exception("❌ Failed to read frame from stream")

    filename = f"frame_{uuid.uuid4().hex}.jpg"
    cv2.imwrite(filename, frame)
    cap.release()
    print(f"✅ Frame saved as {filename}")
    return filename
