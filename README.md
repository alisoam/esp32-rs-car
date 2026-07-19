# ESP32 RC Car

WiFi-controlled RC car with live camera feed. Android app for driving, ESP32 firmware for the car.

## Components

| Part | Status |
|------|--------|
| Android app (Kotlin) | Done |
| Python test server | Done |
| ESP32 firmware (ESP-IDF v5.x) | TODO |

## Quick start

### Test server (no hardware needed)

```bash
python -m venv .venv && .venv/bin/pip install -r test_server/requirements.txt
source .envrc
python test_server/test_server.py
```

### Android app

```bash
cd android
./gradlew assembleDebug
./gradlew installDebug
```

Connect to the test server or ESP32 at `192.168.4.1:80`.

## Endpoints

| Path | Description |
|------|-------------|
| `GET /stream` | MJPEG video feed (multipart/x-mixed-replace) |
| `GET /control?l=<N>&r=<N>&s=<seq>` | Motor speeds (-255..255) with sequence dedup |
| `GET /status` | JSON: fps, clients, uptime, motor values, resolution |

## Hardware

- ESP32 Dev Board
- OV7670 camera (176×144 QCIF)
- MX1508 dual H-bridge motor driver
- 2× DC motors (differential drive)

## License

MIT
