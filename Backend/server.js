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
  client.subscribe('#');
});

client.on('error', function (error) {
  console.log(error);
});

// Untuk penerimaan Short topic Name
const topicMap = {
  '7T': { roomName: '9017', payloadName: 'Temperature' },
  '7H': { roomName: '9017', payloadName: 'Humidity' },
  '7P': { roomName: '9017', payloadName: 'AirPressure' },
  '7V': { roomName: '9017', payloadName: 'Vibration' },
  '8V': { roomName: '9018', payloadName: 'Vibration' },
  '8H': { roomName: '9018', payloadName: 'Humidity' },
  '8P': { roomName: '9018', payloadName: 'AirPressure' },
  '8T': { roomName: '9018', payloadName: 'Temperature' }
};

client.on('message', function (topic, message) {
  let roomName;
  let payloadName;

  if (topic.length <= 2){
    roomName = topicMap[topic].roomName;
    payloadName = topicMap[topic].payloadName;
  } else {
    const topicPart = topic.split('/');
    roomName = topicPart[0];
    payloadName = topicPart[1]; 
  }
  const payloadContent = message.toString();
  ioSocket.emit('mqtt_data', { roomName: roomName, payloadName: payloadName, payloadContent: payloadContent, timeStamp: Date.now()});
  console.log({ roomName: roomName, payloadName: payloadName, payloadContent: payloadContent });
});

server.listen(8080, () => {
  console.log('Server berjalan di http://localhost:8080');
});

// MQTT_HOST = 'b6f0de39dbdb4fbc89413670aabed28a.s1.eu.hivemq.cloud'
// MQTT_USER = 'raynercuanda'
// MQTT_PASS = 'Rayner123'
// MQTT_PORT = 8883