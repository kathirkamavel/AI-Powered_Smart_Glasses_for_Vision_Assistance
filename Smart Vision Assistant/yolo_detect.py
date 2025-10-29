# yolo_detect.py
from ultralytics import YOLO
import os

def detect_objects(image_path):
    model = YOLO("yolov8n.pt")  # lightweight version for speed
    results = model(image_path)[0]

    detected = []
    for result in results.boxes.data.tolist():
        class_id = int(result[5])
        confidence = result[4]
        name = model.names[class_id]
        detected.append({"name": name, "confidence": float(confidence)})

    return detected
