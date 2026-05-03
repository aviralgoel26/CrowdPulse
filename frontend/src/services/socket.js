import SockJS from "sockjs-client";
import { Client } from "@stomp/stompjs";

let client = null;

export const connectSocket = (placeId, callback) => {
  client = new Client({
    webSocketFactory: () => new SockJS("http://localhost:8081/ws"),
    onConnect: () => {
      console.log("Connected");

      client.subscribe(`/topic/queue/${placeId}`, () => {
        callback();
      });
    },
  });

  client.activate();
};

export const disconnectSocket = () => {
  if (client) {
    client.deactivate();
  }
};