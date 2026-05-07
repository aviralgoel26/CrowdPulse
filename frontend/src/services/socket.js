import SockJS from "sockjs-client";
import { Client } from "@stomp/stompjs";

let client = null;
let subscriptionId = null;

export const connectSocket = (placeId, callback) => {
  // Don't reconnect if already connected
  if (client && client.connected) {
    console.log("Socket already connected");
    return;
  }

  client = new Client({
    webSocketFactory: () => new SockJS("http://localhost:8081/ws"),
    
    onConnect: () => {
      console.log("✅ WebSocket connected to server");

      // Subscribe to queue updates for this place
      subscriptionId = client.subscribe(`/topic/queue/${placeId}`, (message) => {
        console.log("📩 Received queue update:", message.body);
        // Trigger callback to refresh status
        if (callback) {
          callback();
        }
      });

      console.log(`✅ Subscribed to /topic/queue/${placeId}`);
    },

    onDisconnect: () => {
      console.log("⚠️ WebSocket disconnected");
      subscriptionId = null;
    },

    onStompError: (frame) => {
      console.error("❌ WebSocket STOMP error:", frame.body);
    },

    onWebSocketError: (error) => {
      console.error("❌ WebSocket connection error:", error);
    },

    reconnectDelay: 5000, // Reconnect after 5 seconds on failure
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000,
  });

  try {
    client.activate();
  } catch (err) {
    console.error("❌ Failed to activate WebSocket:", err);
  }
};

export const disconnectSocket = () => {
  if (client && client.connected) {
    if (subscriptionId) {
      subscriptionId.unsubscribe();
      subscriptionId = null;
    }
    client.deactivate();
    console.log("🔌 WebSocket disconnected");
  }
};

export const isSocketConnected = () => {
  return client && client.connected;
};