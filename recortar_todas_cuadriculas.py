import os
from collections import deque
from PIL import Image, ImageFilter, ImageEnhance
import numpy as np

CARPETA_ORIGEN = "app/src/main/res/drawable"
CARPETA_SALIDA = "app/src/main/res/drawable-nodpi"
os.makedirs(CARPETA_SALIDA, exist_ok=True)

# Limpiar recortes viejos dentro de res/drawable (dejando intactas hoja_1.jpg .. hoja_5.jpg y nucleo_icono.png)
if os.path.exists(CARPETA_ORIGEN):
    for archivo in os.listdir(CARPETA_ORIGEN):
        if archivo.endswith(".png") and archivo != "nucleo_icono.png" and not archivo.startswith("ic_"):
            try:
                os.remove(os.path.join(CARPETA_ORIGEN, archivo))
            except Exception:
                pass

# Cada hoja tiene 4 filas x 2 columnas (8 avatares por hoja)
HOJAS_MAPEO = {
    "hoja_1.jpg": [
        "reposo_feliz", "escuchando_mano_oido",
        "hablando_boca_abierta", "hablando_boca_cerrada",
        "pensando", "alerta",
        "saltando_azul", "corriendo_rapido"
    ],
    "hoja_2.jpg": [
        "enamorado_corazones", "recargando_bateria",
        "furia_roja", "curioso_purpura",
        "triste_llorando", "gafas_oscuras",
        "malicioso_cian", "durmiendo_reposo"
    ],
    "hoja_3.jpg": [
        "asco_verde", "analizando",
        "celebrando_confeti", "saludo_formal",
        "saltando_estrellas", "corriendo",
        "angulo_perfil_1", "angulo_espalda_1"
    ],
    "hoja_4.jpg": [
        "modo_escudo", "modo_combate",
        "modo_sigilo", "modo_diagnostico",
        "modo_energia", "modo_reposo_profundo",
        "extra_1", "extra_2"
    ],
    "hoja_5.jpg": [
        "extra_3", "extra_4",
        "extra_5", "extra_6",
        "extra_7", "extra_8",
        "extra_9", "extra_10"
    ]
}

def limpiar_fondo_con_proteccion_borde(img_rgba):
    arr = np.array(img_rgba, dtype=np.float32)
    h, w, _ = arr.shape
    r, g, b = arr[:, :, 0], arr[:, :, 1], arr[:, :, 2]

    # Detectar fondo blanco/gris muy claro de las esquinas de la celda
    esquinas = np.concatenate([
        arr[0:6, 0:6, :3].reshape(-1, 3),
        arr[0:6, w-6:w, :3].reshape(-1, 3),
        arr[h-6:h, 0:6, :3].reshape(-1, 3),
        arr[h-6:h, w-6:w, :3].reshape(-1, 3),
    ], axis=0)
    bg_color = np.median(esquinas, axis=0)

    # Diferencia respecto al fondo puro + deteccion de contorno (gradiente) para frenar antes de entrar al cuerpo blanco del robot
    dist_bg = np.sqrt(
        (r - bg_color[0]) ** 2 +
        (g - bg_color[1]) ** 2 +
        (b - bg_color[2]) ** 2
    )

    # Magnitud de gradiente (borde del personaje)
    lum = 0.299 * r + 0.587 * g + 0.114 * b
    gx = np.zeros_like(lum)
    gy = np.zeros_like(lum)
    gx[:, 1:-1] = np.abs(lum[:, 2:] - lum[:, :-2]) * 0.5
    gy[1:-1, :] = np.abs(lum[2:, :] - lum[:-2, :]) * 0.5
    grad = np.sqrt(gx ** 2 + gy ** 2)

    # Umbral estricto: solo elimina el fondo uniforme exterior (dist_bg < 22) y se detiene al tocar el contorno gris suave del robot
    es_candidato_fondo = (dist_bg < 22.0) & (grad < 8.5)

    visitado = np.zeros((h, w), dtype=bool)
    es_fondo = np.zeros((h, w), dtype=bool)
    cola = deque()

    for x in range(w):
        if es_candidato_fondo[0, x]:
            cola.append((0, x))
            visitado[0, x] = True
        if es_candidato_fondo[h - 1, x]:
            cola.append((h - 1, x))
            visitado[h - 1, x] = True
    for y in range(h):
        if es_candidato_fondo[y, 0]:
            cola.append((y, 0))
            visitado[y, 0] = True
        if es_candidato_fondo[y, w - 1]:
            cola.append((y, w - 1))
            visitado[y, w - 1] = True

    while cola:
        cy, cx = cola.popleft()
        es_fondo[cy, cx] = True
        for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            ny, nx = cy + dy, cx + dx
            if 0 <= ny < h and 0 <= nx < w and not visitado[ny, nx]:
                if es_candidato_fondo[ny, nx]:
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

    escalada = lienzo.resize((tam_final, tam_final), Image.Resampling.LANCZOS)
    nitida = escalada.filter(ImageFilter.UnsharpMask(radius=1.5, percent=140, threshold=2))
    nitida = ImageEnhance.Contrast(nitida).enhance(1.05)
    return nitida

def procesar_todas():
    for nombre_hoja, nombres_sprites in HOJAS_MAPEO.items():
        rutas_posibles = [
            os.path.join(CARPETA_ORIGEN, nombre_hoja),
            nombre_hoja,
            os.path.join("sprites_originales", nombre_hoja),
        ]
        ruta_encontrada = next((p for p in rutas_posibles if os.path.exists(p)), None)
        if not ruta_encontrada:
            continue

        hoja = Image.open(ruta_encontrada).convert("RGBA")
        ancho, alto = hoja.size
        # Cuadricula real de 4 filas x 2 columnas
        cols, filas = 2, 4
        celda_w = ancho // cols
        celda_h = alto // filas

        idx = 0
        for f in range(filas):
            for c in range(cols):
                if idx >= len(nombres_sprites):
                    break
                margen_x = int(celda_w * 0.02)
                margen_y = int(celda_h * 0.02)
                caja = (
                    c * celda_w + margen_x,
                    f * celda_h + margen_y,
                    (c + 1) * celda_w - margen_x,
                    (f + 1) * celda_h - margen_y
                )
                recorte = hoja.crop(caja)
                sin_fondo = limpiar_fondo_con_proteccion_borde(recorte)
                final_hd = mejorar_nitidez_y_escalar(sin_fondo, 1024)
                destino = os.path.join(CARPETA_SALIDA, f"{nombres_sprites[idx]}.png")
                final_hd.save(destino, "PNG", optimize=True)
                idx += 1

if __name__ == "__main__":
    procesar_todas()
