import os
from collections import deque
import numpy as np
from PIL import Image, ImageFilter

CARPETA_DRAWABLE = "app/src/main/res/drawable"
os.makedirs(CARPETA_DRAWABLE, exist_ok=True)

HOJAS = [
    ("hoja_1.jpg", 2, 4, [
        "angulo_frente_1", "angulo_perfil_1", "angulo_tres_cuartos_1", "angulo_espalda_1",
        "angulo_frente_2", "angulo_perfil_2", "angulo_tres_cuartos_2", "angulo_espalda_2"
    ]),
    ("hoja_2.jpg", 4, 4, [
        "saltando_estrellas", "corriendo", "recargando_bateria_rosa", "vibrando_feliz",
        "llorando", "sorprendido", "enojado_vapor", "pensando",
        "guino_paz", "celebracion_arcoiris", "asustado", "gafas_cool",
        "mareado", "reposo_feliz", "saludo_firme", "durmiendo_brazos_arriba"
    ]),
    ("hoja_3.jpg", 3, 3, [
        "estrellas_cian", "hablando_boca_abierta", "dudoso_ambar",
        "asombro_amarillo", "molesto_rojo", "escuchando_mano_oido",
        "enamorado_manos_pecho", "escaneando_holograma", "durmiendo_reposo"
    ]),
    ("hoja_4.jpg", 2, 3, [
        "hablando_boca_cerrada", "alegre_saludando", "procesando_guino",
        "alerta", "curioso_purpura", "reposo_saludando"
    ]),
    ("hoja_5.jpg", 4, 4, [
        "saltando_azul", "corriendo_rapido", "recargando_bateria", "energia_verde",
        "triste_llorando", "sorpresa_amarilla", "furia_roja", "analizando",
        "guino_victoria", "celebrando_confeti", "miedo_purpura", "gafas_oscuras",
        "confusion_espiral", "asco_verde", "enamorado_corazones", "malicioso_cian"
    ])
]

def eliminar_fondo_sin_halo(img_pil, tolerancia=58, erosion_px=2):
    img = img_pil.convert("RGBA")
    arr = np.array(img, dtype=np.int16)
    h, w, _ = arr.shape
    visitado = np.zeros((h, w), dtype=bool)
    cola = deque()

    def es_fondo(y, x):
        r, g, b, _ = arr[y, x]
        brillo = (int(r) + int(g) + int(b)) // 3
        saturacion = max(r, g, b) - min(r, g, b)
        return brillo >= 255 - tolerancia and saturacion <= 38

    for x in range(w):
        for y in (0, 1, h - 2, h - 1):
            if 0 <= y < h and not visitado[y, x] and es_fondo(y, x):
                visitado[y, x] = True
                cola.append((y, x))
    for y in range(h):
        for x in (0, 1, w - 2, w - 1):
            if 0 <= x < w and not visitado[y, x] and es_fondo(y, x):
                visitado[y, x] = True
                cola.append((y, x))

    while cola:
        cy, cx = cola.popleft()
        for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1), (-1, -1), (-1, 1), (1, -1), (1, 1)):
            ny, nx = cy + dy, cx + dx
            if 0 <= ny < h and 0 <= nx < w and not visitado[ny, nx]:
                if es_fondo(ny, nx):
                    visitado[ny, nx] = True
                    cola.append((ny, nx))

    alpha = np.where(visitado, 0, 255).astype(np.uint8)
    alpha_img = Image.fromarray(alpha, mode="L")
    for _ in range(erosion_px):
        alpha_img = alpha_img.filter(ImageFilter.MinFilter(3))
    alpha_img = alpha_img.filter(ImageFilter.GaussianBlur(radius=0.8))

    salida = arr.astype(np.uint8)
    salida[:, :, 3] = np.array(alpha_img, dtype=np.uint8)
    return Image.fromarray(salida)

def centrar_nitido(img_rgba, tamano=768, margen=20):
    bbox = img_rgba.getbbox()
    if not bbox:
        return img_rgba.resize((tamano, tamano), Image.Resampling.LANCZOS)
    recorte = img_rgba.crop(bbox)
    max_lado = tamano - (margen * 2)
    ratio = min(max_lado / recorte.width, max_lado / recorte.height)
    nuevo_w = max(1, int(recorte.width * ratio))
    nuevo_h = max(1, int(recorte.height * ratio))
    recorte = recorte.resize((nuevo_w, nuevo_h), Image.Resampling.LANCZOS)
    recorte = recorte.filter(ImageFilter.UnsharpMask(radius=1.2, percent=135, threshold=2))

    lienzo = Image.new("RGBA", (tamano, tamano), (0, 0, 0, 0))
    pos_x = (tamano - nuevo_w) // 2
    pos_y = (tamano - nuevo_h) // 2
    lienzo.paste(recorte, (pos_x, pos_y), recorte)
    return lienzo

total = 0
for archivo, filas, cols, nombres in HOJAS:
    ruta_origen = os.path.join(CARPETA_DRAWABLE, archivo)
    if not os.path.exists(ruta_origen):
        continue
    img = Image.open(ruta_origen)
    ancho_celda = img.width // cols
    alto_celda = img.height // filas
    idx = 0
    for f in range(filas):
        for c in range(cols):
            if idx >= len(nombres):
                break
            caja = (c * ancho_celda, f * alto_celda, (c + 1) * ancho_celda, (f + 1) * alto_celda)
            celda = img.crop(caja)
            limpia = eliminar_fondo_sin_halo(celda)
            final = centrar_nitido(limpia)
            ruta_guardado = os.path.join(CARPETA_DRAWABLE, f"{nombres[idx]}.png")
            final.save(ruta_guardado, "PNG", optimize=True)
            total += 1
            idx += 1
    os.remove(ruta_origen)
    print(f"[OK] {archivo} recortada sin halo blanco y eliminada.")

print(f"Total sprites HD generados: {total}")
