# Registro de pruebas

**E** = emulador Android 10 (API 29). **T** = pantalla física Topway TS10 (Android 10, 1280×720) con TLINK5 5.3.36 y un iPhone.

## Visuales

| Prueba | Dónde | Resultado |
|---|---|---|
| 1280×720 horizontal | E, T | OK |
| 1024×600 y 1920×720 | E | OK, se adapta |
| Tamaño de fuente del sistema ×1.15 | T | OK (el texto se limita entre ×0.9 y ×1.2) |
| Pantalla completa sin barras | E, T | OK |
| Panel ancho CarPlay / Android Auto | E (simulado), T | OK |
| Cajón con iconos uniformes | E, T | OK |
| Temas y acentos | E | OK |

## Funcionales

| Prueba | Dónde | Resultado |
|---|---|---|
| Abrir el launcher y volver con Inicio | E, T | OK |
| CarPlay y Android Auto abren TLINK5 | T | OK |
| Detección de CarPlay (`carplay_wireless`) | T | OK |
| Radio: abrir, panel "Radio", cambiar emisora | T | OK |
| Bluetooth: canción, play/pausa inmediato, anterior/siguiente | T | OK |
| CarPlay: play/pausa, anterior/siguiente desde el panel | T | OK |
| Botón de luces abre Magic Lantern | T | OK |
| Cajón: abrir apps de terceros | E, T | OK |
| Voltaje de la MCU | T | OK (11,13 V con el motor apagado) |
| Velocidad GPS | T | OK, marcada como aproximada |
| Diagnóstico y guardado | E, T | OK, sin datos sensibles |
| Actualización por Wi‑Fi (modo desarrollador) | E, T | OK |
| Buscar actualizaciones en GitHub | E | Pendiente de la primera publicación |
| APK de lanzamiento (R8) en todas las pantallas | E | OK, sin cierres |

## Errores encontrados y corregidos

- La app Bluetooth del estéreo repite la última canción aunque ya no suene → solo se muestra si avanza o si el estéreo informa "reproduciendo".
- Pausa con retraso → se usa el aviso directo del estéreo (`com.tw.bt.av.play`).
- Descarga de APK cortada al final por el cierre de conexión en Windows → el servidor espera a que el cliente cierre.
- El modo (CarPlay/Android Auto) seguía al botón pulsado → se toma de TLINK5.
- XTService informa 0 con apps → se interpreta como "fuente: app".

## Pendiente

- Velocidad por CANBUS en Topway.
- Pruebas en pantallas FYT y otras marcas (ayúdanos abriendo un issue con tu diagnóstico).
