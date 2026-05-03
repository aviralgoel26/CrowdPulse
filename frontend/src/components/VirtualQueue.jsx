import { useState, useEffect } from "react";
import { joinQueue, getQueueStatus } from "../services/api";
import { connectSocket, disconnectSocket } from "../services/socket";

export default function VirtualQueue({ placeId }) {
  // 1. Generate ID once and keep it (avoiding reset on re-renders)
  const [userId] = useState(() => "user_" + Math.floor(Math.random() * 10000));

  const [joined, setJoined] = useState(false);
  const [status, setStatus] = useState(null);
  const [loading, setLoading] = useState(false);
  const [groupSize, setGroupSize] = useState(1);

  // 🔹 Get Status logic
  const fetchStatus = async () => {
    try {
      const res = await getQueueStatus(placeId, userId);
      setStatus(res);
    } catch (err) {
      console.error("Status fetch failed:", err);
    }
  };

  // 🔹 Join Queue logic
  const handleJoin = async () => {
    setLoading(true);
    try {
      await joinQueue(placeId, userId, groupSize);
      setJoined(true);
      await fetchStatus(); // Ensure first status is loaded
    } catch (err) {
      console.error("Join failed:", err);
    } finally {
      setLoading(false);
    }
  };

  // 🔁 Socket Integration
  useEffect(() => {
    if (joined) {
      // Connect to socket and pass fetchStatus as the callback
      connectSocket(placeId, fetchStatus);

      // Cleanup: Disconnect when component unmounts or user leaves
      return () => {
        disconnectSocket();
      };
    }
  }, [joined, placeId]); // Added placeId as dependency for safety

  return (
    <div className="space-y-6">
      {!joined ? (
        <div className="flex flex-col gap-4">
          <label className="flex flex-col gap-2">
            <span className="text-sm font-medium text-gray-700">Group Size:</span>
            <input
              type="number"
              min="1"
              max="10"
              value={groupSize}
              onChange={(e) => setGroupSize(parseInt(e.target.value) || 1)}
              className="border p-2 rounded-lg w-24"
            />
          </label>
          
          <button
            onClick={handleJoin}
            disabled={loading}
            className="bg-orange-500 hover:bg-orange-600 disabled:bg-gray-400 text-white px-6 py-3 rounded-xl font-semibold transition-colors"
          >
            {loading ? "Joining..." : "Join Queue"}
          </button>
        </div>
      ) : (
        <div className="bg-white p-6 rounded-xl shadow-lg border border-orange-100 space-y-4">
          <h2 className="text-xl font-bold text-gray-800">
            🎟️ Your Queue Status
          </h2>

          <div className="grid grid-cols-2 gap-4">
            <div className="bg-orange-50 p-3 rounded-lg">
              <p className="text-sm text-gray-500">Position</p>
              <p className="text-2xl font-bold text-orange-600">{status?.position || "-"}</p>
            </div>
            <div className="bg-gray-50 p-3 rounded-lg">
              <p className="text-sm text-gray-500">People Ahead</p>
              <p className="text-2xl font-bold text-gray-700">{status?.peopleAhead ?? "-"}</p>
            </div>
          </div>

          <p className="text-green-600 text-sm flex items-center gap-2">
            <span className="relative flex h-2 w-2">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75"></span>
              <span className="relative inline-flex rounded-full h-2 w-2 bg-green-500"></span>
            </span>
            Updating automatically...
          </p>
        </div>
      )}
    </div>
  );
}