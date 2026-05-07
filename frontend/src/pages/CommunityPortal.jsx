import { useEffect, useState} from "react";
import {
  submitCommunityUpdate,
  getLatestCommunityUpdate,
  getCommunityHistory
} from "../services/api";
export default function CommunityPortal() {
  const [placeId, setPlaceId] = useState(1);
  const [queueLength, setQueueLength] = useState("");
  const [throughput, setThroughput] = useState("");
  const [status, setStatus] = useState("ACTIVE");
  const [note, setNote] = useState("");
  const [latest, setLatest] = useState(null);
  const [history, setHistory] = useState([]);

useEffect(() => {

  const loadLatest = async () => {

    try {

      const data =
        await getLatestCommunityUpdate(placeId);

      setLatest(data);
      const historyData =
  await getCommunityHistory(placeId);

setHistory(historyData);

    } catch (err) {
      console.error(err);
    }
  };

  loadLatest();

}, [placeId]);

  const handleSubmit = async (e) => {
    e.preventDefault();

    const payload = {
      placeId: Number(placeId),
      reportedQueueLength: Number(queueLength),
      throughputPerMin: Number(throughput),
      queueStatus: status,
      note: note,
    };

    try {
      const data = await submitCommunityUpdate(payload);

      console.log("Submitted:", data);

      alert("✅ Update submitted successfully");
      const latestData =
  await getLatestCommunityUpdate(placeId);

setLatest(latestData);

      setQueueLength("");
      setThroughput("");
      setNote("");
    } catch (err) {
      console.error(err);
      alert("❌ Failed to submit");
    }
  };

  return (
    <div className="max-w-xl mx-auto p-6">
      <h1 className="text-2xl font-bold mb-6">
        Community Update Portal
      </h1>
{latest && (

  <div className="bg-white shadow rounded-xl p-5 mb-6">

    <h2 className="text-lg font-semibold mb-4">
      Current Operational State
    </h2>
    <div className="grid grid-cols-2 gap-4">

      <div>
        <p className="text-gray-500 text-sm">
          Queue Length
        </p>

        <p className="text-2xl font-bold">
          {latest.reportedQueueLength}
        </p>
      </div>

      <div>
        <p className="text-gray-500 text-sm">
          Throughput
        </p>

        <p className="text-2xl font-bold">
          {latest.throughputPerMin}/min
        </p>
      </div>

      <div>
        <p className="text-gray-500 text-sm">
          Queue Status
        </p>

        <p className="text-xl font-semibold">
          {latest.queueStatus}
        </p>
      </div>

      <div>
        <p className="text-gray-500 text-sm">
          Last Note
        </p>

        <p>
          {latest.note || "No notes"}
        </p>
      </div>

    </div>
  </div>
)}
<div className="bg-white shadow rounded-xl p-5 mb-6">

  <h2 className="text-lg font-semibold mb-4">
    Recent Community Updates
  </h2>

  <div className="space-y-3">

    {history.map((item) => (

      <div
        key={item.id}
        className="border rounded-lg p-3 flex justify-between items-center"
      >

        <div>
          <p className="font-semibold">
            Queue: {item.reportedQueueLength}
          </p>

          <p className="text-sm text-gray-500">
            Throughput: {item.throughputPerMin}/min
          </p>

          <p className="text-sm text-gray-500">
            {item.note || "No notes"}
          </p>
        </div>

        <div className="text-right">

          <p className="font-semibold">
            {item.queueStatus}
          </p>

          <p className="text-sm text-gray-400">
            {new Date(item.createdAt)
              .toLocaleTimeString()}
          </p>

        </div>

      </div>
    ))}

  </div>
</div>
      <form onSubmit={handleSubmit} className="space-y-4">

        {/* Place ID */}
        <div>
          <label className="block text-sm mb-1">Place ID</label>
          <input
            type="number"
            value={placeId}
            onChange={(e) => setPlaceId(e.target.value)}
            className="w-full border p-2 rounded"
          />
        </div>

        {/* Queue Length */}
        <div>
          <label className="block text-sm mb-1">
            Estimated Queue Length
          </label>
          <input
            type="number"
            value={queueLength}
            onChange={(e) => setQueueLength(e.target.value)}
            className="w-full border p-2 rounded"
            required
          />
        </div>

        {/* Throughput */}
        <div>
          <label className="block text-sm mb-1">
            People Served per Minute
          </label>
          <input
            type="number"
            value={throughput}
            onChange={(e) => setThroughput(e.target.value)}
            className="w-full border p-2 rounded"
            required
          />
        </div>

        {/* Status */}
        <div>
          <label className="block text-sm mb-1">
            Queue Status
          </label>
          <select
            value={status}
            onChange={(e) => setStatus(e.target.value)}
            className="w-full border p-2 rounded"
          >
            <option value="ACTIVE">Active</option>
            <option value="PAUSED">Paused</option>
          </select>
        </div>

        {/* Note */}
        <div>
          <label className="block text-sm mb-1">
            Notes (optional)
          </label>
          <textarea
            value={note}
            onChange={(e) => setNote(e.target.value)}
            className="w-full border p-2 rounded"
            rows={3}
          />
        </div>

        {/* Submit */}
        <button
          type="submit"
          className="w-full bg-orange-500 hover:bg-orange-600 text-white py-3 rounded"
        >
          Submit Update
        </button>
      </form>
    </div>
  );
}