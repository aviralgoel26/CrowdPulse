import { useState } from "react";

export default function CommunityPortal() {
  const [placeId, setPlaceId] = useState(1);
  const [queueLength, setQueueLength] = useState("");
  const [throughput, setThroughput] = useState("");
  const [status, setStatus] = useState("ACTIVE");
  const [note, setNote] = useState("");

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
      const res = await fetch(
        "http://localhost:8081/api/v1/crowdpulse/community/update",
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify(payload),
        }
      );

      const data = await res.json();
      console.log("Submitted:", data);

      alert("✅ Update submitted successfully");

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