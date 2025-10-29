# gemini_api.py
import google.generativeai as genai
import os

# Set your API key
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
if not GEMINI_API_KEY:
    raise ValueError("❌ Gemini API key not set in environment variable 'GEMINI_API_KEY'")

genai.configure(api_key=GEMINI_API_KEY)
model = genai.GenerativeModel("gemini-pro")

def generate_caption(detected_objects, voice_query=None):
    object_names = [obj['name'] for obj in detected_objects]
    prompt = f"Detected objects: {', '.join(object_names)}."

    if voice_query:
        prompt += f" User asked: '{voice_query}'. Generate a helpful natural sentence based on this."
    else:
        prompt += " Generate a descriptive sentence."

    response = model.generate_content(prompt)
    return response.text.strip()
