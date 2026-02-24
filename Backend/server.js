// https://console.hivemq.cloud/clusters/b6f0de39dbdb4fbc89413670aabed28a/getting-started/clients/mqtt-js

import express from 'express';
import cors from 'cors';
import { Server } from "socket.io";
import http from "node:http";
import mqtt from "mqtt";
import 'dotenv/config'; 

const app = express();
app.use(cors());

const server = http.createServer(app);
const ioSocket = new Server(server, {
  cors: {
    origin: "http://localhost:5173",
    methods: ["GET", "POST"]
  }
});

const mqttOptions = {
  host: process.env.MQTT_HOST,
  port: 8883,
  protocol: 'mqtts',
  username: process.env.MQTT_USER,
  password: process.env.MQTT_PASS,
}

var client = mqtt.connect(mqttOptions);

client.on('connect', function () {
    console.log('Connection to Broker Established');
    client.subscribe('my/test/topic')
});

client.on('error', function (error) {
    console.log(error);
});

client.on('message', function (topic, message) {
    console.log('Received message:', topic, message.toString());
    ioSocket.emit('mqtt_data', message.toString());
});

server.listen(8080, () => {
    console.log('Server berjalan di http://localhost:8080');
});
