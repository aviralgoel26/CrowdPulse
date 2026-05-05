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

        {/* Crowd Density */}
        <div className="bg-white rounded-xl shadow p-6">
          <p className="text-gray-500">Current Crowd Density</p>

          <h2 className="text-4xl font-bold mt-2">
         {waitData ? `${waitData.peopleAhead} people` : "--"}
          </h2>

          <p className="text-gray-500 mt-1">
            {"Loading..."}
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
      </div>

      
      {prediction && (
  <div className="bg-blue-50 border-l-4 border-blue-400 rounded-xl p-5 space-y-2">

    <p className="font-semibold text-blue-700">
      📊 Smart Intelligence
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
        {prediction?.bestTime || "--"}
      </span>
      {" "}({prediction.bestWait} min)
    </p>

    <p>
      📈 Peak Time:{" "}
      <span className="font-semibold">
        {prediction.peakTime}
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