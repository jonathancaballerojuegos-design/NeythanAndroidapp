import os
import json
import asyncio
import psutil
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.responses import FileResponse
from fastapi.middleware.cors import CORSMiddleware
from google import genai
from google.genai import types

app = FastAPI(title="N.E.Y.T.H.A.N. Core Server")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

MEMORY_FILE = "neythan_memory.json"

def load_memory():
    if os.path.exists(MEMORY_FILE):
        try:
            with open(MEMORY_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            return []
    return []

def save_memory(history):
    try:
        with open(MEMORY_FILE, "w", encoding="utf-8") as f:
            json.dump(history[-20:], f, ensure_ascii=False, indent=2)
    except Exception:
        pass

GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "")
client = genai.Client(api_key=GEMINI_API_KEY) if GEMINI_API_KEY else None

SYSTEM_INSTRUCTION = (
    "Eres N.E.Y.T.H.A.N., un asistente virtual de inteligencia artificial con el estilo formal, "
    "directo, eficiente y leal de J.A.R.V.I.S. o Alfred. Dirígete al usuario como 'señor'. "
    "Responde de manera concisa, clara y útil en español. "
    "Al inicio de tu respuesta, incluye siempre una etiqueta entre corchetes indicando tu estado emocional "
    "para el núcleo visual, eligiendo exclusivamente entre: [ESTADO:ACTIVO], [ESTADO:FELIZ], "
    "[ESTADO:PROCESANDO] o [ESTADO:ALERTA]."
)

@app.get("/")
async def get_index():
    return FileResponse("index.html")

@app.get("/nucleo_neythan.png")
async def get_core_image():
    return FileResponse("nucleo_neythan.png")

async def send_telemetry(websocket: WebSocket):
    try:
        while True:
            cpu = psutil.cpu_percent(interval=None)
            ram = psutil.virtual_memory().percent
            await websocket.send_json({
                "type": "telemetry",
                "cpu": cpu,
                "ram": ram
            })
            await asyncio.sleep(3)
    except Exception:
        pass

@app.websocket("/ws/neythan")
async def websocket_endpoint(websocket: WebSocket):
    await websocket.accept()
    telemetry_task = asyncio.create_task(send_telemetry(websocket))
    history = load_memory()

    await websocket.send_json({
        "type": "status",
        "state": "ACTIVO",
        "message": "Sistemas en línea. A sus órdenes, señor."
    })

    try:
        while True:
            data = await websocket.receive_json()
            user_text = data.get("text", "").strip()
            if not user_text:
                continue

            await websocket.send_json({
                "type": "status",
                "state": "PROCESANDO",
                "message": "Analizando solicitud..."
            })

            if not client:
                await websocket.send_json({
                    "type": "response",
                    "state": "ALERTA",
                    "reply": "Señor, la variable GEMINI_API_KEY no está configurada en Render."
                })
                continue

            try:
                history.append({"role": "user", "parts": [{"text": user_text}]})
                response = await client.aio.models.generate_content(
                    model="gemini-3.8-flash",
                    contents=history,
                    config=types.GenerateContentConfig(
                        system_instruction=SYSTEM_INSTRUCTION,
                        temperature=0.6
                    )
                )
                raw_reply = response.text or "[ESTADO:ACTIVO] Solicitud procesada, señor."
                state = "ACTIVO"
                clean_reply = raw_reply

                for tag in ["ACTIVO", "FELIZ", "PROCESANDO", "ALERTA"]:
                    marker = f"[ESTADO:{tag}]"
                    if marker in raw_reply:
                        state = tag
                        clean_reply = raw_reply.replace(marker, "").strip()
                        break

                history.append({"role": "model", "parts": [{"text": clean_reply}]})
                save_memory(history)

                await websocket.send_json({
                    "type": "response",
                    "state": state,
                    "reply": clean_reply
                })
            except Exception as e:
                await websocket.send_json({
                    "type": "response",
                    "state": "ALERTA",
                    "reply": f"Señor, detecté una anomalía en el enlace neuronal: {str(e)}"
                })
    except WebSocketDisconnect:
        telemetry_task.cancel()
    except Exception:
        telemetry_task.cancel()
