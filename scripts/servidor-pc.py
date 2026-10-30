"""Enlace Wi-Fi entre la PC y GTS Launcher en la TS10 (la pantalla no tiene ADB sin root).

  GET  /gts.apk   entrega el APK más reciente de ../apks/ para «Actualizar desde la PC».
  GET  /version   versión de ese APK (para que Ajustes avise si hay una nueva).
  POST /reporte   guarda el diagnóstico enviado por la pantalla en ../reportes/.
  POST /apk       guarda APKs del fabricante para analizarlos en ../analisis/.

Uso:  python scripts/servidor-pc.py [puerto]     (por defecto 8765)
Ctrl+C para detenerlo. Solo atiende mientras está abierto.
"""
import datetime
import http.server
import pathlib
import re
import socket
import sys
import urllib.parse

ROOT = pathlib.Path(__file__).resolve().parent.parent
APKS = ROOT / "apks"
REPORTS = ROOT / "reportes"
ANALYSIS = ROOT / "analisis"
PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8765


def latest_apk():
    apks = sorted(APKS.glob("*.apk"), key=lambda p: p.stat().st_mtime, reverse=True)
    return apks[0] if apks else None


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/version":
            # Lets Ajustes show whether a newer build is waiting (version taken from the file name).
            apk = latest_apk()
            m = re.search(r"(\d+\.\d+\.\d+)", apk.name) if apk else None
            body = (m.group(1) if m else "").encode()
            self.send_response(200 if body else 404)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        if self.path != "/gts.apk":
            self.send_error(404)
            return
        apk = latest_apk()
        if apk is None:
            self.send_error(404, "No hay APK en apks/")
            return
        data = apk.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", "application/vnd.android.package-archive")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)
        self.finish_gracefully()
        print(f"-> APK enviado: {apk.name} ({len(data) // 1024} KB) a {self.client_address[0]}")

    def finish_gracefully(self):
        # Closing (or even half-closing) right after writing can lose the tail of the APK on the
        # way to the device. The client knows Content-Length, so let it hang up first.
        try:
            self.connection.settimeout(30)
            while self.connection.recv(4096):
                pass
        except OSError:
            pass

    def do_POST(self):
        if self.path.startswith("/apk?nombre="):
            self.receive_apk()
            return
        if self.path != "/reporte":
            self.send_error(404)
            return
        length = int(self.headers.get("Content-Length", 0))
        if length <= 0 or length > 20 * 1024 * 1024:
            self.send_error(400)
            return
        body = self.rfile.read(length)
        REPORTS.mkdir(exist_ok=True)
        name = REPORTS / f"diagnostico-{datetime.datetime.now():%Y%m%d-%H%M%S}.txt"
        name.write_bytes(body)
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"ok")
        print(f"<- Diagnóstico recibido de {self.client_address[0]}: {name}")

    def receive_apk(self):
        """Vendor APKs sent from Diagnóstico › «Apps a la PC», kept in ../analisis/ for study."""
        name = urllib.parse.unquote(self.path.split("=", 1)[1])
        if not re.fullmatch(r"[A-Za-z0-9._-]+\.apk", name):
            self.send_error(400)
            return
        length = int(self.headers.get("Content-Length", 0))
        if length <= 0 or length > 500 * 1024 * 1024:
            self.send_error(400)
            return
        ANALYSIS.mkdir(exist_ok=True)
        target = ANALYSIS / name
        remaining = length
        with open(target, "wb") as out:
            while remaining:
                chunk = self.rfile.read(min(remaining, 1 << 16))
                if not chunk:
                    break
                out.write(chunk)
                remaining -= len(chunk)
        if remaining:
            target.unlink(missing_ok=True)
            self.send_error(400, "incompleto")
            return
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"ok")
        print(f"<- APK para análisis: {target.name} ({length // 1024} KB)")

    def log_message(self, fmt, *args):
        pass


def local_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        # UDP connect sends nothing; it only makes the OS pick the LAN interface's address.
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except OSError:
        return "?"
    finally:
        s.close()


if __name__ == "__main__":
    apk = latest_apk()
    print(f"Servidor listo. En la pantalla escribe:  {local_ip()}:{PORT}")
    print(f"APK que se entregará: {apk.name if apk else '(ninguno)'}")
    http.server.ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
