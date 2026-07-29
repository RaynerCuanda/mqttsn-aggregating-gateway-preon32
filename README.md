# MQTT-SN Aggregating Gateway for Preon32 WSN

Final thesis (Tugas Akhir) project implementing an **MQTT-SN aggregating gateway** for a Preon32-based Wireless Sensor Network (WSN). The gateway sits between wireless sensor nodes and an MQTT broker, translating and aggregating lightweight MQTT-SN traffic into standard MQTT, with a real-time backend and web dashboard for monitoring incoming sensor data.

Full write-up: [`DokumenTA.pdf`](./DokumenTA.pdf).

## Overview

```
Preon32 Sensor Node --> Preon32 Forwarder --> Gateway --> MQTT Broker (HiveMQ Cloud) --> Backend (Subscriber) --> Frontend (Dashboard)
```

The system has four parts, each in its own directory:

| Directory | Role |
|---|---|
| [`Preon32Sandbox/`](./Preon32Sandbox) | Preon32 device apps: **Sensor node** (`Preon32Sensor.java`) reads its environment (temperature, humidity, air pressure, vibration) and publishes over MQTT-SN via IEEE 802.15.4; **Forwarder** (`ForwarderMQTTSN.java`) relays MQTT-SN traffic between sensor nodes and the gateway, since the gateway runs on a desktop and can't talk IEEE 802.15.4 directly. |
| [`AggregatingPreon32/`](./AggregatingPreon32) | Aggregating Gateway. Reads MQTT-SN packets from the Preon32 forwarder over a serial (USART) connection, and converts them to MQTT messages sent to the broker via a single aggregating connection using the Eclipse Paho MQTTv5 client. |
| [`Backend/`](./Backend) | Node.js/Express service that subscribes to the MQTT broker and re-broadcasts incoming sensor readings to connected clients in real time over Socket.IO. |
| [`Frontend/`](./Frontend) | React (Vite) dashboard that consumes the Socket.IO stream and visualizes live sensor readings (temperature, humidity, air pressure, vibration) per room. |

## 1. Sensor Nodes & Forwarder — `Preon32Sandbox/`

Sensor nodes built and deployed onto Preon32 hardware. It contains two device roles, both speaking MQTT-SN over IEEE 802.15.4:

* **`Preon32Sensor.java` (Sensor node):** Reads onboard/attached sensors (temperature, humidity, air pressure, vibration) and publishes readings as MQTT-SN messages toward the nearest forwarder/gateway.
* **`ForwarderMQTTSN.java` (Forwarder):** Relays MQTT-SN traffic between sensor nodes and the gateway. Needed because the gateway (`AggregatingPreon32/`) runs on a desktop machine and cannot receive IEEE 802.15.4 radio directly — the forwarder bridges radio traffic to the gateway's serial connection.
* Shared packet framing logic lives in `MQTTSNPacket.java` / `MQTTSNClient.java` / `PublishHelper.java`.

## 2. Gateway — `AggregatingPreon32/`

A Java application (Gradle, `application` plugin) responsible for:

* **Serial ingestion:** Listens on a serial port (`COM4` by default, see `GatewayMQTTSN.java`) using the Virtenio Commander toolset (`Preon32Helper`) to read raw packets from Preon32 WSN nodes.
* **MQTT-SN packet handling:** Parses MQTT-SN packet framing, message types, and bitwise flags (`MQTTSNPacket.java`), and tracks per-node session state (`ClientSession.java`).
* **Aggregating proxy:** Consolidates traffic from multiple sensor nodes/forwarders before bridging it upstream, reducing the number of individual broker connections.
* **QoS bridging:** Uses a bundled Eclipse Paho MQTTv5 client (vendored under `org.eclipse.paho.mqttv5`) so PUBACK/PUBREL/PUBCOMP handshakes on the MQTT side are driven by Paho's async token/promise API instead of manual state tracking.

Configuration (serial port and broker credentials) is read from a `.env` file in `AggregatingPreon32/` via `dotenv-java` (gitignored). Copy the template and fill it in:

```bash
cd AggregatingPreon32
cp .env.example .env   # then edit .env with your SERIAL_PORT / MQTT_HOST / MQTT_USER / MQTT_PASS
./gradlew run           # build and run the gateway
```

## 3. Backend — `Backend/`

An Express + Socket.IO server that bridges MQTT to the browser:

* Connects to the MQTT broker over TLS (`mqtts://…:8883`).
* Subscribes to all topics (`#`) and maps short MQTT-SN topic aliases (e.g. `7T`, `8H`) back to human-readable room/sensor names, alongside full-length `room/sensor` topics.
* Emits every message as a `mqtt_data` Socket.IO event (`{ roomName, payloadName, payloadContent, timeStamp }`) to all connected dashboard clients.

Configuration is read from a `.env` file in `Backend/` (gitignored). Copy the template and fill in your broker credentials:

```bash
cd Backend
cp .env.example .env   # then edit .env with your MQTT_HOST / MQTT_USER / MQTT_PASS
npm install
node server.js          # serves on http://localhost:8080
```

## 4. Frontend — `Frontend/`

A React 19 + Vite dashboard that connects to the backend over Socket.IO and renders live sensor data per room.

## Documentation

* [`DokumenTA.pdf`](./DokumenTA.pdf) — full thesis document (system design, MQTT-SN protocol handling, testing/evaluation results).
