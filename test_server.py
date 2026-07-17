#!/usr/bin/env python3
"""
ESP32 RC Car — Android App Test Server

Simulates the ESP32 firmware's HTTP endpoints so you can test the Android app
without any hardware. Run this on your computer:

    ./venv/bin/python test_server.py

Then connect the Android app to this machine's IP (port 8080 by default).
The app will see a generated MJPEG video feed, motor commands will be logged
to the console, and /status will reply with live JSON.
"""

import argparse
import json
import os
import time
import math
import struct
import textwrap
import threading
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from io import BytesIO

from PIL import Image, ImageDraw, ImageFont


# ── generated video settings ────────────────────────────────────────────────
WIDTH = 176
HEIGHT = 144
FPS_TARGET = 15
FRAME_INTERVAL = 1.0 / FPS_TARGET

# shared state (protected by lock)
state_lock = threading.Lock()
motor_left = 0
motor_right = 0
last_seq = 0
start_time = time.time()
client_count = 0


def uptime_sec() -> float:
    return time.time() - start_time


def make_frame(seq: int) -> bytes:
    """Generate a synthetic JPEG frame showing motor values + debug info."""
    img = Image.new("RGB", (WIDTH, HEIGHT), color=(20, 24, 30))
    draw = ImageDraw.Draw(img)

    with state_lock:
        left = motor_left
        right = motor_right

    # --- background fill for each motor bar area ---
    bar_h = 24
    bar_w = WIDTH - 20

    # left motor bar
    l_intensity = int(abs(left) / 255 * 200)
    if left > 0:
        l_color = (0, l_intensity, 0)   # green forward
    elif left < 0:
        l_color = (l_intensity, 0, 0)   # red reverse
    else:
        l_color = (40, 40, 40)
    draw.rectangle([10, 10, 10 + bar_w, 10 + bar_h], fill=l_color)

    # right motor bar
    r_intensity = int(abs(right) / 255 * 200)
    if right > 0:
        r_color = (0, r_intensity, 0)
    elif right < 0:
        r_color = (r_intensity, 0, 0)
    else:
        r_color = (40, 40, 40)
    draw.rectangle([10, 40, 10 + bar_w, 40 + bar_h], fill=r_color)

    # --- text overlay ---
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf", 12)
    except OSError:
        font = ImageFont.load_default()

    draw.text((12, 10), f"L {left:+4d}", fill=(255, 255, 255), font=font)
    draw.text((12, 40), f"R {right:+4d}", fill=(255, 255, 255), font=font)

    # FPS / uptime
    elapsed = uptime_sec()
    fps = seq / max(elapsed, 0.001)
    draw.text((12, 72), f"FPS {fps:5.1f}", fill=(180, 180, 180), font=font)
    draw.text((12, 88), f"fr  {seq:5d}", fill=(180, 180, 180), font=font)

    # bouncing marker to confirm video is live
    bx = int(10 + (WIDTH - 30) * (0.5 + 0.5 * math.sin(elapsed * 2.0)))
    by = int(HEIGHT - 30 + 10 * math.cos(elapsed * 1.5))
    draw.ellipse([bx, by, bx + 14, by + 14], fill=(79, 195, 247))

    # encode to JPEG in memory
    out = BytesIO()
    img.save(out, format="JPEG", quality=70)
    return out.getvalue()


# ── HTTP handler ────────────────────────────────────────────────────────────
class RCRequestHandler(BaseHTTPRequestHandler):

    server_version = "ESP32-Test-Server"

    def log_message(self, format, *args):
        _ = format, args
        pass

    def _send_json(self, data: dict) -> None:
        body = json.dumps(data).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def _send_text(self, status: int, text: str) -> None:
        body = text.encode()
        self.send_response(status)
        self.send_header("Content-Type", "text/plain")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = self.path.split("?", 1)[0]
        params = self._parse_params()

        if path == "/status":
            self._handle_status()
        elif path == "/control":
            self._handle_control(params)
        elif path == "/stream":
            self._handle_stream()
        else:
            self._send_text(404, "not found")

    def _parse_params(self) -> dict[str, str]:
        if "?" not in self.path:
            return {}
        qs = self.path.split("?", 1)[1]
        result = {}
        for pair in qs.split("&"):
            if "=" in pair:
                k, v = pair.split("=", 1)
                result[k] = v
        return result

    def _handle_status(self):
        with state_lock:
            data = {
                "fps": FPS_TARGET,
                "clients": client_count,
                "uptime": int(uptime_sec()),
                "left": motor_left,
                "right": motor_right,
                "resolution": f"{WIDTH}x{HEIGHT}",
            }
        self._send_json(data)

    def _handle_control(self, params: dict[str, str]):
        global motor_left, motor_right, last_seq
        try:
            left = int(params.get("l", 0))
            right = int(params.get("r", 0))
            seq = int(params.get("s", 0))
        except ValueError:
            self._send_text(400, "invalid params")
            return

        left = max(-255, min(255, left))
        right = max(-255, min(255, right))

        with state_lock:
            if seq and seq <= last_seq:
                self._send_text(200, "stale")
                print(f"  \033[90m(stale seq={seq} dropped)\033[0m")
                return
            if seq:
                last_seq = seq
            motor_left = left
            motor_right = right

        # colored console output
        l_str = f"L {left:+4d}"
        r_str = f"R {right:+4d}"
        l_color = _motor_color(left)
        r_color = _motor_color(right)
        print(f"  {l_color(l_str)}  {r_color(r_str)}")

        self._send_text(200, "ok")

    def _handle_stream(self):
        global client_count

        with state_lock:
            client_count += 1

        self.send_response(200)
        self.send_header("Content-Type", "multipart/x-mixed-replace; boundary=FRAME")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()

        seq = 0
        last_time = time.time()
        try:
            while True:
                frame = make_frame(seq)
                header = (
                    f"--FRAME\r\n"
                    f"Content-Type: image/jpeg\r\n"
                    f"Content-Length: {len(frame)}\r\n"
                    f"\r\n"
                )
                try:
                    self.wfile.write(header.encode())
                    self.wfile.write(frame)
                except (BrokenPipeError, ConnectionResetError):
                    break

                seq += 1

                # frame pacing
                elapsed = time.time() - last_time
                sleep_for = max(0, FRAME_INTERVAL - elapsed)
                time.sleep(sleep_for)
                last_time = time.time()
        finally:
            with state_lock:
                client_count -= 1

    def log_request(self, code="-", size="-"):
        # only log non-stream requests
        path = self.path.split("?", 1)[0]
        if path in ("/control", "/status"):
            # status and control already printed inline, just a quiet tick
            pass


# ── terminal colours ────────────────────────────────────────────────────────
USE_COLOR = os.isatty(1)


def _motor_color(val: int):
    """Return a function that colours a string based on motor value."""
    if not USE_COLOR:
        return lambda s: s
    if val > 0:
        return lambda s: f"\033[32m{s}\033[0m"   # green
    elif val < 0:
        return lambda s: f"\033[31m{s}\033[0m"   # red
    else:
        return lambda s: f"\033[90m{s}\033[0m"   # grey


# ── main ────────────────────────────────────────────────────────────────────
def main():
    parser = argparse.ArgumentParser(description="ESP32 RC Car Test Server")
    parser.add_argument("--host", default="0.0.0.0", help="bind address (default: 0.0.0.0)")
    parser.add_argument("--port", type=int, default=8080, help="bind port (default: 8080)")
    args = parser.parse_args()

    server = ThreadingHTTPServer((args.host, args.port), RCRequestHandler)
    server.daemon_threads = True
    local_ip = _guess_local_ip()

    print()
    print("╔══════════════════════════════════════════════╗")
    print("║   ESP32 RC Car — Android Test Server        ║")
    print("╠══════════════════════════════════════════════╣")
    print(f"║   Bind: {args.host}:{args.port:<5}                          ║")
    print(f"║   Your IP: {local_ip:<15}                   ║")
    print("╠══════════════════════════════════════════════╣")
    print("║   Endpoints:                                ║")
    print(f"║     GET /status                             ║")
    print(f"║     GET /control?l=N&r=N                    ║")
    print(f"║     GET /stream                             ║")
    print("╠══════════════════════════════════════════════╣")
    print(f"║   Connect the app to → {local_ip}:{args.port}    ║")
    print("╚══════════════════════════════════════════════╝")
    print()

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nshutting down.")
        server.shutdown()


def _guess_local_ip() -> str:
    import socket
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except OSError:
        return "127.0.0.1"


if __name__ == "__main__":
    main()
