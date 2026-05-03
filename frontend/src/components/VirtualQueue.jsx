import { useState, useEffect } from "react";
import { joinQueue, getQueueStatus } from "../services/api";
import { connectSocket, disconnectSocket } from "../services/socket";

export default function VirtualQueue({ placeId }) {

  // unique user (temporary)
  const [userId] = useState("user_" + Math.floor(Math.random() * 10000));

  const [joined, setJoined] = useState(false);
  const [status, setStatus] = useState(null);
  const [loading, setLoading] = useState(false);

  // 🔹 Join Queue
  const handleJoin = async () => {
    setLoading(true);

    try {
      await joinQueue(placeId, userId);
      setJoined(true);
      fetchStatus();
    } catch (err) {
      console.error("Join failed:", err);
    }

    setLoading(false);
  };

  // 🔹 Get Status
  const fetchStatus = async () => {
    try {
      const res = await getQueueStatus(placeId, userId);
      setStatus(res);
    } catch (err) {
      console.error("Status fetch failed:", err);
    }
  };

  // 🔁 AUTO REFRESH (IMPORTANT)
  useEffect(() => {
    if (joined) {
      connectSocket(placeId, fetchStatus);
      return () => clearInterval(interval);
    }
  }, [joined]);

  return (
    <div className="space-y-6">

      {/* JOIN BUTTON */}
      {!joined ? (
        <button
          onClick={handleJoin}
          disabled={loading}
          className="bg-orange-500 hover:bg-orange-600 text-white px-6 py-3 rounded-xl font-semibold"
        >
          {loading ? "Joining..." : "Join Queue"}
        </button>
      ) : (
        <div className="bg-white p-6 rounded-xl shadow space-y-4">

          <h2 className="text-xl font-bold">
            🎟️ Your Queue Status
          </h2>

          <p>
            Position: <strong>{status?.position}</strong>
          </p>

          <p>
            People Ahead: <strong>{status?.peopleAhead}</strong>
          </p>

          {/* 🔥 LIVE FEEDBACK */}
          <p className="text-green-600 text-sm">
            🔄 Updating automatically...
          </p>

        </div>
      )}
    </div>
  );
}