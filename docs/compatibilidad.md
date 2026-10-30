# Informe de compatibilidad

Probado a fondo en una **Topway TS10** (UIS7862, Android 10, 1280×720, firmware TS10.2.2) con TLINK5 5.3.36, más emulador Android 10 a 1280×720, 1024×600 y 1920×720. "Universal" = funciona en cualquier pantalla Android 8+; "Topway" = solo si la pantalla tiene esos servicios (se detectan solos).

| Función | App o servicio responsable | Método de integración | Estado | Resultado de pruebas | Limitaciones |
|---|---|---|---|---|---|
| Launcher de inicio | Android (HOME) | Actividad con categoría HOME | Universal | OK en TS10 y emulador | Algunos firmwares fijan su launcher; ver recuperación |
| Pantalla completa | Android | Modo inmersivo solo en el inicio | Universal | OK en TS10 | Las otras apps siguen mostrando la barra |
| Apple CarPlay / Android Auto | TLINK5, ZLink, AutoKit, CarLinkit… | Abre la app de enlace detectada por nombre (o la elegida) | Universal | OK en TS10 con TLINK5 | TLINK5 no permite forzar el modo; lo decide el teléfono |
| Detección CarPlay/Android Auto y panel ancho | TLINK5 / ZLink (`com.zjinnova.zlink`) | Difusión `com.zjinnova.zlink` (`status`, `phoneMode`) | Universal con TLINK5/ZLink | OK en TS10 (`carplay_wireless`) | Otras apps de enlace: sin detección, se muestran ambos accesos |
| Nombre de la canción en CarPlay | — | — | No disponible | Confirmado en TS10 | TLINK5 no publica metadatos ni notificaciones |
| Play/pausa de CarPlay | TLINK5 | `MAIN_AUDIO_START/STOP` + tecla multimedia | Universal con TLINK5 | OK en TS10 | — |
| Radio AM/FM | App de radio del estéreo (`com.tw.radio` en Topway) | Abre la app | Universal | OK en TS10 | Sin frecuencia ni cambio de banda desde fuera |
| Cambio de emisora desde el panel | XTService (Topway) | `mediaNext/mediaPre` | Topway | OK en TS10 | — |
| Bluetooth Audio | App Bluetooth del estéreo | Abre la app | Universal | OK en TS10 | El emparejamiento se hace en la app del estéreo |
| Canción del Bluetooth del estéreo | `com.tw.bt` (Topway) | Protocolo `GET_ID3_INFO` / `SEND_ID3_INFO` | Topway | OK en TS10 | — |
| Play/pausa del Bluetooth inmediato | `com.tw.bt` → XTService | Aviso `com.tw.bt.av.play` (`playState`) | Topway | OK en TS10 | — |
| Fuente de audio activa | XTService (`com.tw.service.xt`) | `Source_recieve` (1 radio, 8 Bluetooth, 0 app) | Topway | OK en TS10 | — |
| Música de apps (YouTube, Spotify…) | Sesiones multimedia de Android | `MediaSessionManager` + acceso a notificaciones | Universal | OK en emulador | Requiere activar el acceso a notificaciones |
| Voltaje de la batería | MCU vía `android.tw.john.TWUtil` (Topway) | Mensaje 1292, igual que la barra del sistema | Topway | OK en TS10 (≈11–14 V) | — |
| Velocidad km/h | Receptor GPS | `LocationManager` (opcional, "aproximada") | Universal | OK en TS10 | No es la velocidad del CANBUS; no sustituye al velocímetro |
| Velocidad por CANBUS | MCU (Topway) | — | Pendiente | — | Requiere identificar el mensaje de la MCU |
| Botón de luces | Cualquier app (Magic Lantern, etc.) | Abre la app elegida; nombre y visibilidad configurables | Universal | OK en TS10 | Control directo de las luces no implementado |
| Mandos al volante | App del estéreo (`com.tw.keypad` en Topway) | Accesible desde el cajón | Universal | OK en TS10 | El launcher no lee ni reasigna botones |
| Cajón de aplicaciones | Android | Lista de apps con icono + Ajustes de Android | Universal | OK en TS10 y emulador | — |
| Indicador Wi‑Fi | Android | `ConnectivityManager` + `WifiManager` | Universal | OK en TS10 | Se apaga durante CarPlay inalámbrico (el chip actúa de punto de acceso) |
| Indicador Bluetooth / teléfono | Android | `BluetoothAdapter` | Universal | Oculto en TS10 | En Topway el Bluetooth va por un módulo propio y Android lo reporta apagado |
| Actualizaciones | GitHub Releases | API pública + instalador de Android | Universal | OK (flujo probado con la PC) | Pide confirmar cada instalación |
| Diagnóstico | Android | Informe local, guardado en Descargas | Universal | OK en TS10 | No incluye IMEI, serie ni MAC |
