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

def encontrar_intervalos_activos(perfil_bool, min_tam=25):
    intervalos = []
    en_bloque = False
    inicio = 0
    for i, val in enumerate(perfil_bool):
        if val and not en_bloque:
            en_bloque = True
            inicio = i
        elif not val and en_bloque:
            en_bloque = False
            if i - inicio >= min_tam:
                intervalos.append((inicio, i))
    if en_bloque and len(perfil_bool) - inicio >= min_tam:
        intervalos.append((inicio, len(perfil_bool)))
    return intervalos

def fusionar_intervalos_cercanos(intervalos, gap_max=14):
    if not intervalos:
        return []
    fusionados = [intervalos[0]]
    for ini, fin in intervalos[1:]:
        prev_ini, prev_fin = fusionados[-1]
        if ini - prev_fin <= gap_max:
            fusionados[-1] = (prev_ini, fin)
        else:
            fusionados.append((ini, fin))
    return fusionados

def limpiar_fondo_con_proteccion_borde(img_rgba):
    arr = np.array(img_rgba, dtype=np.float32)
    h, w, _ = arr.shape
    r, g, b = arr[:, :, 0], arr[:, :, 1], arr[:, :, 2]

    esquinas = np.concatenate([
        arr[0:5, 0:5, :3].reshape(-1, 3),
        arr[0:5, w-5:w, :3].reshape(-1, 3),
        arr[h-5:h, 0:5, :3].reshape(-1, 3),
        arr[h-5:h, w-5:w, :3].reshape(-1, 3),
    ], axis=0)
    bg_color = np.median(esquinas, axis=0)

    dist_bg = np.sqrt(
        (r - bg_color[0]) ** 2 +
        (g - bg_color[1]) ** 2 +
        (b - bg_color[2]) ** 2
    )

    lum = 0.299 * r + 0.587 * g + 0.114 * b
    gx = np.zeros_like(lum)
    gy = np.zeros_like(lum)
    gx[:, 1:-1] = np.abs(lum[:, 2:] - lum[:, :-2]) * 0.5
    gy[1:-1, :] = np.abs(lum[2:, :] - lum[:-2, :]) * 0.5
    grad = np.sqrt(gx ** 2 + gy ** 2)

    es_candidato_fondo = (dist_bg < 24.0) & (grad < 8.5)

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
    nitida = escalada.filter(ImageFilter.UnsharpMask(radius=1.4, percent=135, threshold=2))
    nitida = ImageEnhance.Contrast(nitida).enhance(1.04)
    return nitida

def detectar_cajas_personajes(hoja_rgba, max_esperados=8):
    arr = np.array(hoja_rgba, dtype=np.float32)
    h, w, _ = arr.shape
    r, g, b = arr[:, :, 0], arr[:, :, 1], arr[:, :, 2]

    # Muestrear fondo en bordes exteriores de la hoja
    bordes = np.concatenate([
        arr[0:8, :, :3].reshape(-1, 3),
        arr[h-8:h, :, :3].reshape(-1, 3),
        arr[:, 0:8, :3].reshape(-1, 3),
        arr[:, w-8:w, :3].reshape(-1, 3),
    ], axis=0)
    bg = np.median(bordes, axis=0)

    dist = np.sqrt((r - bg[0])**2 + (g - bg[1])**2 + (b - bg[2])**2)
    # Un pixel pertenece a un personaje si difiere del fondo blanco/gris claro
    no_fondo = dist > 16.0

    # Detectar bandas horizontales (filas de personajes)
    perfil_y = np.sum(no_fondo, axis=1) > (w * 0.035)
    filas = fusionar_intervalos_cercanos(encontrar_intervalos_activos(perfil_y, min_tam=int(h * 0.07)), gap_max=int(h * 0.015))

    cajas = []
    pad = 10
    for y1, y2 in filas:
        sub_y1 = max(0, y1 - pad)
        sub_y2 = min(h, y2 + pad)
        franja = no_fondo[sub_y1:sub_y2, :]
        perfil_x = np.sum(franja, axis=0) > ((sub_y2 - sub_y1) * 0.04)
        cols = fusionar_intervalos_cercanos(encontrar_intervalos_activos(perfil_x, min_tam=int(w * 0.08)), gap_max=int(w * 0.015))

        for x1, x2 in cols:
            sub_x1 = max(0, x1 - pad)
            sub_x2 = min(w, x2 + pad)
            cajas.append((sub_x1, sub_y1, sub_x2, sub_y2))

    # Respaldo inteligente si la deteccion automatica no encontro suficientes bloques
    if len(cajas) < 4:
        cajas = []
        # Detectar automaticamente si la hoja es horizontal (4 cols x 2 filas) o vertical (2 cols x 4 filas)
        if w > h * 1.15:
            cols, filas_n = 4, 2
        else:
            cols, filas_n = 2, 4
        cw, ch = w // cols, h // filas_n
        for f in range(filas_n):
            for c in range(cols):
                cajas.append((c * cw, f * ch, (c + 1) * cw, (f + 1) * ch))

    return cajas[:max_esperados]

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
        cajas = detectar_cajas_personajes(hoja, max_esperados=len(nombres_sprites))

        for idx, caja in enumerate(cajas):
            if idx >= len(nombres_sprites):
                break
            recorte = hoja.crop(caja)
            sin_fondo = limpiar_fondo_con_proteccion_borde(recorte)
            final_hd = mejorar_nitidez_y_escalar(sin_fondo, 1024)
            destino = os.path.join(CARPETA_SALIDA, f"{nombres_sprites[idx]}.png")
            final_hd.save(destino, "PNG", optimize=True)

if __name__ == "__main__":
    procesar_todas()
