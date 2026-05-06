import { useEffect, useState } from "react";
import {
  getWaitTime
} from "../services/api";

import CrowdChart from "./CrowdChart";
import { getPrediction } from "../services/api";

export default function LiveIntelligence({ placeId }) {
 
 
  const [loading, setLoading] = useState(true);
  const [waitData, setWaitData] = useState(null);
  const [prediction, setPrediction] = useState(null);

  useEffect(() => {
  loadData();

  // ⏱️ Auto refresh every 5 sec
  const interval = setInterval(() => {
    loadData();
  }, 5000);

  // 🧹 cleanup
  return () => clearInterval(interval);

}, [placeId]);

const formatTime = (time) => {
  if (!time) return "--";
  return time.slice(0, 5); // HH:mm
};

  const loadData = async () => {
  try {
    setLoading(true);

    const wait = await getWaitTime(placeId);
    const predData = await getPrediction(placeId);
    
  
    setWaitData(wait);
    setPrediction(predData);
  } catch (err) {
    console.error("Error loading Live Intelligence:", err);
  } finally {
    setLoading(false);
  }
};

  return (
    <div className="space-y-6">
{loading && (
  <p className="text-gray-400 animate-pulse">
    Updating live data...
  </p>
)}
      {/* 🔥 TOP CARDS */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">

        {/* Live Crowd */}
<div className="bg-white rounded-xl shadow p-6">

  <p className="text-gray-500">
    Live Crowd Estimate
  </p>

  <h2 className="text-4xl font-bold mt-2 text-orange-500">
    {waitData ? waitData.peopleAhead : "--"}
  </h2>

  <p className="text-gray-500 mt-1">
    people currently ahead in queue
  </p>

</div>

        {/* Estimated Wait */}
        <div className="bg-white rounded-xl shadow p-6">
          <p className="text-gray-500">Estimated Wait</p>

          <h2 className="text-4xl font-bold mt-2">
  {waitData ? `${waitData.waitMinutes} min` : "--"}
</h2>

<p className="text-gray-500 mt-1">
  {waitData
    ? `${waitData.peopleAhead} people ahead`
    : "calculating..."}
</p>

          <p className="text-gray-500 mt-1">
            based on live data
          </p>
        </div>

        <div className="bg-white rounded-xl shadow p-6">

  <p className="text-gray-500">
    Queue Status
  </p>

  <h2 className={`text-3xl font-bold mt-2 ${
    waitData?.queueStatus === "ACTIVE"
      ? "text-green-600"
      : waitData?.queueStatus === "PAUSED"
      ? "text-yellow-500"
      : "text-red-600"
  }`}>

    {waitData?.queueStatus || "UNKNOWN"}

  </h2>

  <p className="text-gray-500 mt-1">
    live operational status
  </p>

</div>
      </div>

      
      {prediction && (
  <div className="bg-blue-50 border-l-4 border-blue-400 rounded-xl p-5 space-y-2">

    <p className="font-semibold text-blue-700">
      📊 Smart Intelligence
    </p>

    <p className={`font-bold ${
  prediction.alert === "EXTREME"
    ? "text-red-600"
    : prediction.alert === "HIGH"
    ? "text-orange-500"
    : prediction.alert === "MODERATE"
    ? "text-yellow-500"
    : "text-green-600"
}`}>
  🚦 Crowd Alert: {prediction.alert}
</p>

    <p>
      🟢 Current Wait:{" "}
      <span className="font-semibold">
        {prediction.currentWait} min
      </span>
    </p>

    <p>
      📉 Best Time:{" "}
      <span className="font-semibold">
        {formatTime(prediction?.bestTime) || "--"}
      </span>
      {" "}({prediction.bestWait} min)
    </p>

    <p>
      📈 Peak Time:{" "}
      <span className="font-semibold">
        {formatTime(prediction.peakTime)}
      </span>
      {" "}({prediction.peakWait} min)
    </p>

    <p className={`font-semibold ${
      prediction.trend === "RISING"
        ? "text-red-500"
        : prediction.trend === "FALLING"
        ? "text-green-500"
        : "text-gray-500"
    }`}>
      📊 Trend: {prediction.trend}
    </p>

    <p className="text-blue-600 font-medium">
      💡 {prediction.recommendation}
    </p>

  </div>
)}

      {/* 📊 GRAPH */}
      <div className="bg-white rounded-xl shadow p-6">
        <p className="text-gray-500 mb-4">
          Today's Hourly Trend
        </p>

        <CrowdChart data={prediction?.timeline || []} />
      </div>
    </div>
  );
}