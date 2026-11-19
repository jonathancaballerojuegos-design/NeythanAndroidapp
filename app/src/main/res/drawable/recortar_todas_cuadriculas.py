import os
from collections import deque
import numpy as np
from PIL import Image

CARPETA_SALIDA = "drawable_neythan_listos"
os.makedirs(CARPETA_SALIDA, exist_ok=True)

HOJAS = [
    ("hoja_1.png", 2, 4, [
        "angulo_frente_1", "angulo_perfil_1", "angulo_tres_cuartos_1", "angulo_espalda_1",
        "angulo_frente_2", "angulo_perfil_2", "angulo_tres_cuartos_2", "angulo_espalda_2"
    ]),
    ("hoja_2.png", 4, 4, [
        "saltando_estrellas", "corriendo", "recargando_bateria_rosa", "vibrando_feliz",
        "llorando", "sorprendido", "enojado_vapor", "pensando",
        "guino_paz", "celebracion_arcoiris", "asustado", "gafas_cool",
        "mareado", "reposo_feliz", "saludo_firme", "durmiendo_brazos_arriba"
    ]),
    ("hoja_3.png", 3, 3, [
        "estrellas_cian", "hablando_boca_abierta", "dudoso_ambar",
        "asombro_amarillo", "molesto_rojo", "escuchando_mano_oido",
        "enamorado_manos_pecho", "escaneando_holograma", "durmiendo_reposo"
    ]),
    ("hoja_4.png", 2, 3, [
        "hablando_boca_cerrada", "alegre_saludando", "procesando_guino",
        "alerta", "curioso_purpura", "reposo_saludando"
    ]),
    ("hoja_5.png", 4, 4, [
        "saltando_azul", "corriendo_rapido", "recargando_bateria", "energia_verde",
        "triste_llorando", "sorpresa_amarilla", "furia_roja", "analizando",
        "guino_victoria", "celebrando_confeti", "miedo_purpura", "gafas_oscuras",
        "confusion_espiral", "asco_verde", "enamorado_corazones", "malicioso_cian"
    ])
]

def eliminar_fondo_blanco(img_pil, tolerancia=32):
    img = img_pil.convert("RGBA")
    arr = np.array(img, dtype=np.int16)
    h, w, _ = arr.shape
    visitado = np.zeros((h, w), dtype=bool)
    cola = deque()

    def es_blanco(y, x):
        r, g, b, _ = arr[y, x]
        return r >= 255 - tolerancia and g >= 255 - tolerancia and b >= 255 - tolerancia

    for x in range(w):
        if es_blanco(0, x):
            visitado[0, x] = True
            cola.append((0, x))
        if es_blanco(h - 1, x):
            visitado[h - 1, x] = True
            cola.append((h - 1, x))
    for y in range(h):
        if es_blanco(y, 0):
            visitado[y, 0] = True
            cola.append((y, 0))
        if es_blanco(y, w - 1):
            visitado[y, w - 1] = True
            cola.append((y, w - 1))

    while cola:
        cy, cx = cola.popleft()
        for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            ny, nx = cy + dy, cx + dx
            if 0 <= ny < h and 0 <= nx < w and not visitado[ny, nx]:
                if es_blanco(ny, nx):
                    visitado[ny, nx] = True
                    cola.append((ny, nx))

    salida = arr.astype(np.uint8)
    salida[visitado, 3] = 0
    return Image.fromarray(salida)

def centrar_y_redimensionar(img_rgba, tamano=512, margen=24):
    bbox = img_rgba.getbbox()
    if not bbox:
        return img_rgba.resize((tamano, tamano), Image.Resampling.LANCZOS)
    recorte = img_rgba.crop(bbox)
    max_lado = tamano - (margen * 2)
    ratio = min(max_lado / recorte.width, max_lado / recorte.height)
    nuevo_w = max(1, int(recorte.width * ratio))
    nuevo_h = max(1, int(recorte.height * ratio))
    recorte = recorte.resize((nuevo_w, nuevo_h), Image.Resampling.LANCZOS)
    lienzo = Image.new("RGBA", (tamano, tamano), (0, 0, 0, 0))
    pos_x = (tamano - nuevo_w) // 2
    pos_y = (tamano - nuevo_h) // 2
    lienzo.paste(recorte, (pos_x, pos_y), recorte)
    return lienzo

total = 0
for archivo, filas, cols, nombres in HOJAS:
    if not os.path.exists(archivo):
        print(f"[OMITIDO] No se encontró {archivo}")
        continue
    img = Image.open(archivo)
    ancho_celda = img.width // cols
    alto_celda = img.height // filas
    idx = 0
    for f in range(filas):
        for c in range(cols):
            if idx >= len(nombres):
                break
            caja = (c * ancho_celda, f * alto_celda, (c + 1) * ancho_celda, (f + 1) * alto_celda)
            celda = img.crop(caja)
            limpia = eliminar_fondo_blanco(celda)
            final = centrar_y_redimensionar(limpia)
            ruta_guardado = os.path.join(CARPETA_SALIDA, f"{nombres[idx]}.png")
            final.save(ruta_guardado, "PNG")
            total += 1
            idx += 1
    print(f"[OK] {archivo} procesada correctamente.")

print(f"\nListo, señor. Se generaron {total} sprites transparentes en '{CARPETA_SALIDA}'.")
