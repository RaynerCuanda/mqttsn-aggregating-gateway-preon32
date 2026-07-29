# MQTT-SN Aggregating Gateway for Preon32 WSN

Final thesis (Tugas Akhir) project implementing an **MQTT-SN aggregating gateway** for a Preon32-based Wireless Sensor Network (WSN). The gateway sits between wireless sensor nodes and an MQTT broker, translating and aggregating lightweight MQTT-SN traffic into standard MQTT, with a real-time backend and web dashboard for monitoring incoming sensor data.

Full write-up: [`DokumenTA.pdf`](./DokumenTA.pdf).

## Overview

```
Preon32 Sensor Nodes  --(MQTT-SN over serial/radio)-->  Gateway (Java)  --(MQTT/TLS)-->  MQTT Broker (HiveMQ Cloud)
                                                                                                |
                                                                                                v
                                                                          Backend (Node/Express + Socket.IO)
                                                                                                |
                                                                                                v
                                                                              Frontend (React dashboard)
```

The system has three parts, each in its own directory:

| Directory | Role |
|---|---|
| [`AggregatingPreon32/`](./AggregatingPreon32) | Java gateway. Reads MQTT-SN packets from Preon32 nodes over a serial (USART) connection, parses/aggregates them, and republishes them as standard MQTT messages to a broker via the Eclipse Paho MQTTv5 client. |
| [`Backend/`](./Backend) | Node.js/Express service that subscribes to the MQTT broker and re-broadcasts incoming sensor readings to connected clients in real time over Socket.IO. |
| [`Frontend/`](./Frontend) | React (Vite) dashboard that consumes the Socket.IO stream and visualizes live sensor readings (temperature, humidity, air pressure, vibration) per room. |

## 1. Gateway — `AggregatingPreon32/`

A Java application (Gradle, `application` plugin) responsible for:

* **Serial ingestion:** Listens on a serial port (`COM4` by default, see `GatewayMQTTSN.java`) using the Virtenio Commander toolset (`Preon32Helper`) to read raw packets from Preon32 WSN nodes.
* **MQTT-SN packet handling:** Parses MQTT-SN packet framing, message types, and bitwise flags (`MQTTSNPacket.java`), and tracks per-node session state (`ClientSession.java`).
* **Aggregating proxy:** Consolidates traffic from multiple sensor nodes/forwarders before bridging it upstream, reducing the number of individual broker connections.
* **QoS bridging:** Uses a bundled Eclipse Paho MQTTv5 client (vendored under `org.eclipse.paho.mqttv5`) so PUBACK/PUBREL/PUBCOMP handshakes on the MQTT side are driven by Paho's async token/promise API instead of manual state tracking.

**Entry point:** `aggregatingpreon32.GatewayMQTTSN` (configured as `mainClass` in `app/build.gradle.kts`).

### Prerequisites

* JDK 21 (see `java.toolchain` in `app/build.gradle.kts`)
* A Preon32 WSN device connected over serial (default port `COM4`, 115200 baud — edit `PORT_NUMBER` in `GatewayMQTTSN.java` to match your setup)
* Reachable MQTT broker credentials (see `GatewayMQTTSN.java` for host/port/auth configuration)

### Build & run

```bash
cd AggregatingPreon32
./gradlew run        # build and run the gateway
./gradlew build       # produce a runnable jar under app/build/libs
./gradlew test        # run unit tests
```

## 2. Backend — `Backend/`

An Express + Socket.IO server that bridges MQTT to the browser:

* Connects to the MQTT broker over TLS (`mqtts://…:8883`).
* Subscribes to all topics (`#`) and maps short MQTT-SN topic aliases (e.g. `7T`, `8H`) back to human-readable room/sensor names, alongside full-length `room/sensor` topics.
* Emits every message as a `mqtt_data` Socket.IO event (`{ roomName, payloadName, payloadContent, timeStamp }`) to all connected dashboard clients.

### Configuration

Create a `.env` file in `Backend/` (not committed) with:

```
MQTT_HOST=<your-broker-host>
MQTT_USER=<broker-username>
MQTT_PASS=<broker-password>
```

> The broker port (`8883`) and protocol (`mqtts`) are currently hardcoded in `server.js`.

### Run

```bash
cd Backend
npm install
node server.js   # serves on http://localhost:8080
```

## 3. Frontend — `Frontend/`

A React 19 + Vite dashboard that connects to the backend over Socket.IO and renders live sensor data per room.

### Run

```bash
cd Frontend
npm install
npm run dev       # dev server, default http://localhost:5173
npm run build      # production build
```

By default the backend's CORS/Socket.IO config expects the frontend at `http://localhost:5173`.

## Running the full stack locally

1. Start/point to an MQTT broker (e.g. HiveMQ Cloud, or a local Mosquitto instance) and note its credentials.
2. Configure and run the **gateway** (`AggregatingPreon32/`) so it can read from the Preon32 hardware and publish to the broker.
3. Configure `.env` and run the **backend** (`Backend/`) to bridge broker → browser.
4. Run the **frontend** (`Frontend/`) and open it in a browser to see live readings.

## Documentation

* [`DokumenTA.pdf`](./DokumenTA.pdf) — full thesis document (system design, MQTT-SN protocol handling, testing/evaluation results).
