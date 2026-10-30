# GTS Launcher

Launcher minimalista y oscuro para **pantallas Android de auto** (estéreos "doble DIN", TS10, TS18, Topway, FYT, etc.), inspirado en la sobriedad de los sistemas multimedia de fábrica. Botones grandes, pensado para usarse manejando, ligero (≈100 KB) y sin publicidad ni rastreo.

> Proyecto independiente. No está afiliado a Mitsubishi, Apple, Google ni a ningún fabricante de pantallas. "CarPlay", "Android Auto" y demás marcas pertenecen a sus dueños; el launcher solo abre las apps que ya tiene tu pantalla.

## Qué hace

- **Pantalla de inicio** con cuatro accesos grandes: Apple CarPlay, Android Auto, Radio y Bluetooth Audio.
- **CarPlay / Android Auto**: abre tu app de enlace (TLink5, ZLink, AutoKit, CarLinkit…). Con TLink5/ZLink detecta qué se conectó y **ese acceso se ensancha** sobre los dos lugares; al desconectar vuelven ambos.
- **Panel del vehículo**: velocidad (opcional, por GPS), hora y lo que está sonando, con botones grandes de anterior / play-pausa / siguiente.
- **Dock**: botón de luces (configurable: luces interiores, de chasis o ninguno), cajón de aplicaciones y ajustes.
- **Cajón de aplicaciones** con iconos del mismo estilo para todas las apps (o los originales, si lo prefieres).
- **Pantalla completa**: oculta la barra de notificaciones y las flechas mientras estás en el inicio.
- **Temas**: Grafito, Negro o Acero, con acento rojo, rojo oscuro o plata.
- **Actualización** desde la propia app (Ajustes › Buscar actualizaciones).

### Funciones extra en pantallas Topway (TS10 / TS18)

Si tu pantalla tiene los servicios Topway, el launcher los usa automáticamente:

| Función | Cómo |
|---|---|
| Voltaje de la batería del auto en la barra superior | Leído de la MCU, igual que la barra del sistema |
| Fuente activa (radio, Bluetooth, app) | Servicio XTService del estéreo |
| Botones de reproducción para radio, Bluetooth y apps | Mismo camino que los botones del volante |
| Canción y estado play/pausa del Bluetooth del estéreo | App Bluetooth del estéreo |

En otras pantallas esas funciones simplemente no aparecen; todo lo demás funciona igual.

## Requisitos

- Android **8.0 o superior** (la mayoría de pantallas usan 8.1, 9, 10 o más).
- Pantalla horizontal (diseñado sobre 1280×720; se adapta a 1024×600, 1920×720, etc.).

## Instalación (tutorial)

1. **Descarga** `GTS-Launcher-x.y.z.apk` desde [Releases](../../releases/latest).
   - En la propia pantalla: abre Chrome, entra a la página de Releases y descarga el APK.
   - O descárgalo en tu PC y cópialo a una memoria USB.
2. **Abre el APK** con el administrador de archivos de la pantalla (Archivos, ZArchiver, etc.).
3. Si Android lo pide, permite **"Instalar apps desconocidas"** para ese administrador de archivos y toca **Instalar**.
4. Abre **GTS Launcher** desde el menú de apps para probarlo sin cambiar nada todavía.

### Ponerlo como launcher predeterminado

- Pulsa el botón **Inicio** de la pantalla. Si Android pregunta qué app usar, elige **GTS Launcher** y **Siempre**.
- O en GTS Launcher: **Ajustes › Sistema › Launcher predeterminado** y elige GTS Launcher.

### Primeros ajustes recomendados

1. **Ajustes › Multimedia › Información de reproducción**: activa el acceso para GTS Launcher. Sin esto no puede mostrar la canción ni controlar la música de las apps.
2. **Ajustes › Accesos**: revisa qué app abre cada acceso. Se detectan solas por el nombre; si alguna sale "No encontrada" o no es la correcta, tócala y elige la app.
3. **Ajustes › Botón de luces**: elige la app de tus luces, el nombre del botón, u ocúltalo si no tienes.
4. **Ajustes › Velocidad** (opcional): "GPS (aproximada)" muestra la velocidad del receptor GPS. Es informativa y **no sustituye al velocímetro**.

## Actualizar

**Ajustes › Actualización › Buscar actualizaciones**. Si hay una versión nueva en GitHub, la descarga y Android te pide confirmar la instalación. La primera vez Android te pedirá permitir "Instalar apps desconocidas" para GTS Launcher. Tus ajustes se conservan.

## Volver al launcher original / desinstalar

- **Volver al original**: Ajustes de Android › Apps › Apps predeterminadas › App de inicio, y elige el launcher de tu pantalla. Si tu pantalla no tiene ese menú: Ajustes › Apps › GTS Launcher › **Borrar valores predeterminados**, y al pulsar Inicio elige el original.
- **Desinstalar**: Ajustes de Android › Apps › GTS Launcher › Desinstalar. Primero vuelve al launcher original.

Más detalle en [docs/recuperacion.md](docs/recuperacion.md). El launcher no modifica el sistema, no necesita root y no toca el firmware.

## Permisos y privacidad

| Permiso | Para qué | ¿Obligatorio? |
|---|---|---|
| Acceso a notificaciones | Leer qué suena y controlar la reproducción | No (sin él no hay info de música) |
| Ubicación | Solo si eliges velocidad por GPS | No |
| Instalar apps desconocidas | Instalar actualizaciones desde la app | No |
| Bluetooth, red, Wi‑Fi | Mostrar los indicadores de estado | Automático |

No envía datos a ningún servidor. Solo se conecta a internet cuando tocas "Buscar actualizaciones" (consulta la página pública de GitHub). No lee IMEI, números de serie ni direcciones MAC.

## Limitaciones conocidas

- **Nombre de la canción en CarPlay/Android Auto**: TLink5 no lo comparte con el sistema, así que se muestra "Apple CarPlay · Sonando en TLINK5" (los botones sí funcionan).
- **Frecuencia de la radio**: la app de radio de los Topway no la publica; el acceso abre la radio y se cambia de banda dentro de ella.
- **Velocidad real del CANBUS**: aún no implementada; solo GPS (aproximada).
- Android 12+ puede pedir permiso de "Dispositivos cercanos" para mostrar el icono de Bluetooth.

Tabla completa en [docs/compatibilidad.md](docs/compatibilidad.md) y pruebas realizadas en [docs/pruebas.md](docs/pruebas.md).

## Compilar desde el código

Requiere Android Studio (JDK 17+) o solo el SDK de Android.

```
cd launcher
./gradlew assembleRelease        # Windows: gradlew.bat assembleRelease
```

El APK queda en `launcher/app/build/outputs/apk/release/`. Sin `llave-firma/keystore.properties` se firma con la llave de depuración (no podrá instalarse encima de las versiones publicadas).

Estructura:

```
launcher/app/src/main/java/com/jajua/gts/
  HomeActivity      pantalla de inicio      SettingsActivity  ajustes
  Tile, InfoPanel, TopBar, Dock, DrawerView  vistas
  AppCatalog, Targets, Role, AppGlyphs       apps, accesos e iconos
  MediaMonitor, TwBluetooth, TwCommand       reproducción (Android y Topway)
  TLinkState, TwVoltage, StatusMonitor, SpeedMonitor
  Audit/AuditActivity                        diagnóstico del sistema
scripts/servidor-pc.py   herramienta de desarrollo (modo desarrollador)
```

### Modo desarrollador

Toca 7 veces la versión en Ajustes. Aparecen el enlace Wi‑Fi con la PC (`python scripts/servidor-pc.py`) para enviar diagnósticos e instalar versiones de prueba sin ADB ni root.

## Licencia

[MIT](LICENSE).
