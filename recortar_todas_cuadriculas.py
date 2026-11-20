import os
from PIL import Image, ImageFilter, ImageEnhance
import numpy as np

CARPETA_SALIDA = "app/src/main/res/drawable-nodpi"
os.makedirs(CARPETA_SALIDA, exist_ok=True)

# Limpiar recortes viejos en drawable normal para que Android no use versiones reescaladas borrosas
CARPETA_DRAWABLE_VIEJA = "app/src/main/res/drawable"
if os.path.exists(CARPETA_DRAWABLE_VIEJA):
    for archivo in os.listdir(CARPETA_DRAWABLE_VIEJA):
        if archivo.endswith(".png") and not archivo.startswith("ic_"):
            try:
                os.remove(os.path.join(CARPETA_DRAWABLE_VIEJA, archivo))
            except Exception:
                pass

HOJAS_MAPEO = {
    "hoja_1.jpg": [
        "reposo_feliz", "escuchando_mano_oido", "hablando_boca_abierta",
        "hablando_boca_cerrada", "pensando", "alerta"
    ],
    "hoja_2.jpg": [
        "saltando_azul", "corriendo_rapido", "recargando_bateria",
        "celebrando_confeti", "enamorado_corazones", "malicioso_cian"
    ],
    "hoja_3.jpg": [
        "asco_verde", "gafas_oscuras", "analizando",
        "furia_roja", "durmiendo_reposo", "triste_llorando"
    ],
    "hoja_4.jpg": [
        "angulo_perfil_1", "angulo_espalda_1", "curioso_purpura",
        "saltando_estrellas", "corriendo", "saludo_formal"
    ],
    "hoja_5.jpg": [
        "modo_escudo", "modo_combate", "modo_sigilo",
        "modo_diagnostico", "modo_energia", "modo_reposo_profundo"
    ]
}

def limpiar_fondo_inteligente(img_rgba):
    arr = np.array(img_rgba, dtype=np.int32)
    h, w, _ = arr.shape
    r, g, b = arr[:, :, 0], arr[:, :, 1], arr[:, :, 2]

    # Detectar color dominante del fondo en las 4 esquinas
    esquinas = np.concatenate([
        arr[0:10, 0:10, :3].reshape(-1, 3),
        arr[0:10, w-10:w, :3].reshape(-1, 3),
        arr[h-10:h, 0:10, :3].reshape(-1, 3),
        arr[h-10:h, w-10:w, :3].reshape(-1, 3),
    ], axis=0)
    bg_color = np.median(esquinas, axis=0)

    dist = np.sqrt(
        (r - bg_color[0]) ** 2 +
        (g - bg_color[1]) ** 2 +
        (b - bg_color[2]) ** 2
    )

    # Solo hacer transparente los pixeles conectados a los bordes (Flood Fill / BFS)
    # Esto protege el interior blanco del cuerpo y cabeza del robot
    visitado = np.zeros((h, w), dtype=bool)
    es_fondo = np.zeros((h, w), dtype=bool)
    umbral = 42.0

    from collections import deque
    cola = deque()

    for x in range(w):
        if dist[0, x] < umbral:
            cola.append((0, x))
            visitado[0, x] = True
        if dist[h - 1, x] < umbral:
            cola.append((h - 1, x))
            visitado[h - 1, x] = True
    for y in range(h):
        if dist[y, 0] < umbral:
            cola.append((y, 0))
            visitado[y, 0] = True
        if dist[y, w - 1] < umbral:
            cola.append((y, w - 1))
            visitado[y, w - 1] = True

    while cola:
        cy, cx = cola.popleft()
        es_fondo[cy, cx] = True
        for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            ny, nx = cy + dy, cx + dx
            if 0 <= ny < h and 0 <= nx < w and not visitado[ny, nx]:
                if dist[ny, nx] < umbral:
                    visitado[ny, nx] = True
                    cola.append((ny, nx))

    salida = arr.astype(np.uint8)
    salida[es_fondo, 3] = 0
    return Image.fromarray(salida, "RGBA")

def mejorar_nitidez_y_escalar(img_rgba, tam_final=1024):
    bbox = img_rgba.getbbox()
    if bbox:
        img_rgba = img_rgba.crop(bbox)

    w, h = img_rgba.size
    lado = max(w, h)
    lienzo = Image.new("RGBA", (lado, lado), (0, 0, 0, 0))
    lienzo.paste(img_rgba, ((lado - w) // 2, (lado - h) // 2))

    # Reescalado de maxima calidad LANCZOS y enfoque fino (UnsharpMask) para evitar borrosidad
    escalada = lienzo.resize((tam_final, tam_final), Image.Resampling.LANCZOS)
    nitida = escalada.filter(ImageFilter.UnsharpMask(radius=1.6, percent=145, threshold=2))
    nitida = ImageEnhance.Contrast(nitida).enhance(1.06)
    return nitida

def procesar_todas():
    for nombre_hoja, nombres_sprites in HOJAS_MAPEO.items():
        rutas_posibles = [
            nombre_hoja,
            os.path.join("sprites_originales", nombre_hoja),
            os.path.join("app/src/main/res/drawable", nombre_hoja),
            os.path.join("app/src/main/res/drawable-nodpi", nombre_hoja),
        ]
        ruta_encontrada = next((p for p in rutas_posibles if os.path.exists(p)), None)
        if not ruta_encontrada:
            continue

        hoja = Image.open(ruta_encontrada).convert("RGBA")
        ancho, alto = hoja.size
        cols, filas = 3, 2
        celda_w = ancho // cols
        celda_h = alto // filas

        idx = 0
        for f in range(filas):
            for c in range(cols):
                if idx >= len(nombres_sprites):
                    break
                margen_x = int(celda_w * 0.03)
                margen_y = int(celda_h * 0.03)
                caja = (
                    c * celda_w + margen_x,
                    f * celda_h + margen_y,
                    (c + 1) * celda_w - margen_x,
                    (f + 1) * celda_h - margen_y
                )
                recorte = hoja.crop(caja)
                sin_fondo = limpiar_fondo_inteligente(recorte)
                final_hd = mejorar_nitidez_y_escalar(sin_fondo, 1024)
                destino = os.path.join(CARPETA_SALIDA, f"{nombres_sprites[idx]}.png")
                final_hd.save(destino, "PNG", optimize=True)
                idx += 1

if __name__ == "__main__":
    procesar_todas()
