// https://console.hivemq.cloud/clusters/b6f0de39dbdb4fbc89413670aabed28a/getting-started/clients/mqtt-js
// https://console.hivemq.cloud/clusters/b6f0de39dbdb4fbc89413670aabed28a/web-client
// Publish Testing  username: admin, password: Admin123
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
    client.subscribe('+/+');
});

client.on('error', function (error) {
    console.log(error);
});

client.on('message', function (topic, message) {
    const topicPart = topic.split('/');
    const roomName = topicPart[0];
    const payloadName = topicPart[1];
    const payloadContent = message.toString();
    ioSocket.emit('mqtt_data', {roomName: roomName, payloadName: payloadName, payloadContent: payloadContent});
    // console.log({roomName: roomName, payloadName: payloadName, payloadContent: payloadContent});
    // console.log(topic)
    // console.log(payload_content)
});

server.listen(8080, () => {
    console.log('Server berjalan di http://localhost:8080');
});

// MQTT_HOST = 'b6f0de39dbdb4fbc89413670aabed28a.s1.eu.hivemq.cloud'
// MQTT_USER = 'raynercuanda'
// MQTT_PASS = 'Rayner123'
// MQTT_PORT = 8883