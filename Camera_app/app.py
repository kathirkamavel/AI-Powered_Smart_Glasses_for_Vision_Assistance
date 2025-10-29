from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse, FileResponse
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
import os
import base64
import json
import pandas as pd
import pickle
import numpy as np
import cv2
from scipy.stats import hmean
from fastapi import FastAPI
from pymongo import MongoClient
from bson import ObjectId
from datetime import datetime, timedelta
import random

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

UPLOAD_FOLDER = 'uploads'
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

# Mount the static files directory
app.mount("/static", StaticFiles(directory="static"), name="static")

client = MongoClient("mongodb+srv://kathirkamavel:<db_password>@cluster0.7yxl4.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0")
db = client['userData']
collection = db['Notes']

def convert_object_id_and_datetime(item):
    if isinstance(item, dict):
        for key, value in item.items():
            if isinstance(value, ObjectId):
                item[key] = str(value)
            elif isinstance(value, datetime):
                item[key] = value.isoformat()  # Convert datetime to ISO 8601 string
    return item

# Get data from MongoDB
@app.get("/getdata")
async def get_data():
    item = collection.find({"available": True})
    if item:
        d = {}
        d['m'] = []
        for i in item:
            i = convert_object_id_and_datetime(i)
            d['m'].append(i)
        return JSONResponse(content=d)
    return {"message": "Item not found"}

# Save data to MongoDB
@app.get("/adddata/{item_id}")
@app.post("/adddata/{item_id}")
async def save_data(item_id: str):

    data = {
        "date_time": datetime.now(),
        "note": item_id.replace("_"," "),
        "available": True
    }

    result = collection.insert_one(data)
    print("Inserted document IDs:", result.inserted_id)

    return {"message": "Success"}

@app.get("/remove/{item_id}")
async def remove_data(item_id: str):
    # Prepare the filter as a dictionary
    item_id = item_id.replace("_", " ")
    item_filter = {"note": {"$regex": item_id, "$options": "i"}}

    # Delete matching documents
    result = collection.delete_many(item_filter)
    
    # Return the count of deleted documents
    return {"message": "Success", "deleted_count": result.deleted_count}

@app.get("/", response_class=FileResponse)
@app.post("/", response_class=FileResponse)
async def render_index():
    return "index.html"

@app.get("/success.html", response_class=FileResponse)
async def success():
    return "success.html"

@app.get("/gesture.html", response_class=FileResponse)
async def gesture():
    return "gesture.html"

@app.get("/failure.html", response_class=FileResponse)
async def render_index():
    return "failure.html"

@app.post("/verify")
async def homepage(request: Request):
    data = await request.json()

    username = data.get('username')

    if username == "test":
        
        return JSONResponse(content={"status": "bot"}, status_code=200)
    
    df = pd.json_normalize(data)
    print(df.columns)
    df.drop(columns = ['screenResolution', 'aspectRatio', 'username'], inplace= True)

    df['mx'] = df['mouseMovements'].apply(lambda i: [int(j['x']) for j in i])
    df['my'] = df['mouseMovements'].apply(lambda i: [int(j['y']) for j in i])

    df.drop(columns=['mouseMovements','cpuLoadData','keystrokePatterns'], inplace= True)
    df['honeypotVerified'] = df['honeypotVerified'].apply(int)


    mx = df['mx'].values
    my = df['my'].values

    m = []

    for j in range(len(mx)):  # Loop through each list in mx

        l = []

        n = len(mx[j])  # Get the length of the current list mx[j]

        for i in range(n - 1):  # Iterate until the second last element

            # Calculate the Euclidean distance between consecutive points
            distance = np.sqrt((mx[j][i] - mx[j][i+1]) ** 2 + (my[j][i] - my[j][i+1]) ** 2)
            l.append(distance)

        m.append(l)  # Append the list of distances for this row

    df['m'] = m
    df['m'] = df['m'].apply(hmean)
    df['duration'] = df['mx'].apply(len)

    df.drop(columns = ['mx','my'], inplace=True)

    with open('models/best.pkl','rb') as f:
        dtree = pickle.load(f)

    z = dtree.predict(df)

    if z:

        return JSONResponse(content={"status": "human"}, status_code=200)
    
    else:

        return JSONResponse(content={"status": "bot"}, status_code=200)


@app.post("/gesture")
async def submit_drawing(request: Request):
    data = await request.json()
    drawing_data = data.get('drawing')
    expected_shape = data.get('expectedShape')
    print(expected_shape)

    if drawing_data and expected_shape:
        image_data = drawing_data.split(',')[1]
        image = base64.b64decode(image_data)
        image_filename = f"{expected_shape}.png"
        image_path = os.path.join(UPLOAD_FOLDER, image_filename)
        
        with open(image_path, 'wb') as image_file:
            image_file.write(image)

        image = cv2.imread(image_path)
        image = cv2.resize(image, (64, 64))

        j = np.array(image)
        j = j.flatten()
        j = j.reshape(-1,12288)

        with open(f'models/{expected_shape.lower()}.pkl','rb') as f:

            model = pickle.load(f)

        z = model.predict(j)

        if z > 0.5:
            return JSONResponse(content={'verified': True}, status_code=200)
        
        else:
            return JSONResponse(content={'verified': False}, status_code=400)
    else:
        return JSONResponse(content={'verified': False}, status_code=400)
    

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=80)
