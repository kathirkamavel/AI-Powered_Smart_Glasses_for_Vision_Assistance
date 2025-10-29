{\rtf1\ansi\ansicpg1252\cocoartf2822
\cocoatextscaling0\cocoaplatform0{\fonttbl\f0\fswiss\fcharset0 Helvetica;}
{\colortbl;\red255\green255\blue255;}
{\*\expandedcolortbl;;}
\paperw11900\paperh16840\margl1440\margr1440\vieww11520\viewh8400\viewkind0
\pard\tx720\tx1440\tx2160\tx2880\tx3600\tx4320\tx5040\tx5760\tx6480\tx7200\tx7920\tx8640\pardirnatural\partightenfactor0

\f0\fs24 \cf0 from picamera2 import Picamera2\
from picamera2.encoders import JpegEncoder\
from picamera2.outputs import FileOutput\
from flask import Flask, Response\
import io\
import logging\
import threading\
import time\
\
# Set up logging\
logging.basicConfig(level=logging.INFO)\
logger = logging.getLogger(__name__)\
\
app = Flask(__name__)\
picam2 = None\
frame_buffer = io.BytesIO()\
frame_lock = threading.Lock()\
running = True\
\
def initialize_camera():\
    global picam2\
    try:\
        picam2 = Picamera2()\
        # Configure camera with better settings for continuous streaming\
        config = picam2.create_video_configuration(\
            main=\{"size": (640, 480)\},\
            controls=\{\
                "FrameDurationLimits": (33333, 33333),  # Set to ~30fps\
                "NoiseReductionMode": 0  # Minimal processing for better performance\
            \}\
        )\
        picam2.configure(config)\
        picam2.start()\
        time.sleep(2)  # Give camera time to warm up\
        return True\
    except Exception as e:\
        logger.error(f"Camera initialization failed: \{str(e)\}")\
        return False\
\
def capture_frames():\
    global running\
    frames_captured = 0\
    last_time = time.time()\
    \
    while running:\
        try:\
            with frame_lock:\
                frame_buffer.seek(0)\
                frame_buffer.truncate()\
                picam2.capture_file(frame_buffer, format='jpeg')\
                \
            frames_captured += 1\
            if frames_captured % 30 == 0:  # Log FPS every 30 frames\
                current_time = time.time()\
                fps = 30 / (current_time - last_time)\
                logger.info(f"Streaming at \{fps:.2f\} FPS")\
                last_time = current_time\
                \
            # Small sleep to prevent overwhelming the CPU\
            time.sleep(0.01)\
            \
        except Exception as e:\
            logger.error(f"Frame capture error: \{str(e)\}")\
            time.sleep(1)  # Wait before retrying\
            if not running:\
                break\
            \
            # Try to reinitialize camera if there's an error\
            logger.info("Attempting to reinitialize camera...")\
            if initialize_camera():\
                logger.info("Camera reinitialized successfully")\
            else:\
                logger.error("Camera reinitialization failed")\
                time.sleep(5)  # Wait longer before next retry\
\
def generate_frames():\
    while running:\
        try:\
            with frame_lock:\
                frame_data = frame_buffer.getvalue()\
            if frame_data:\
                yield (b'--frame\\r\\n'\
                       b'Content-Type: image/jpeg\\r\\n\\r\\n' + frame_data + b'\\r\\n')\
            time.sleep(0.01)  # Prevent tight loop\
        except Exception as e:\
            logger.error(f"Frame generation error: \{str(e)\}")\
            time.sleep(1)\
\
@app.route('/')\
def index():\
    return """\
    <html>\
        <head>\
            <title>Pi Zero Camera Stream</title>\
            <meta name="viewport" content="width=device-width, initial-scale=1">\
            <style>\
                body \{ font-family: Arial, sans-serif; margin: 20px; \}\
                img \{ max-width: 100%; height: auto; \}\
                .status \{ color: green; \}\
            </style>\
        </head>\
        <body>\
            <h1>Pi Zero Camera Stream</h1>\
            <p class="status">Stream Active</p>\
            <img src="/video_feed" width="640" height="480" />\
        </body>\
    </html>\
    """\
\
@app.route('/video_feed')\
def video_feed():\
    return Response(generate_frames(),\
                   mimetype='multipart/x-mixed-replace; boundary=frame')\
\
def cleanup():\
    global running\
    running = False\
    if picam2:\
        picam2.stop()\
        picam2.close()\
\
if __name__ == '__main__':\
    try:\
        if initialize_camera():\
            # Start frame capture in a separate thread\
            capture_thread = threading.Thread(target=capture_frames)\
            capture_thread.daemon = True\
            capture_thread.start()\
            \
            # Run the Flask app on all network interfaces\
            app.run(host='0.0.0.0', port=5000, threaded=True)\
    except KeyboardInterrupt:\
        logger.info("Shutting down gracefully...")\
    finally:\
        cleanup()}