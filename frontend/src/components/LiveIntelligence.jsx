import { useEffect, useState } from "react";
import {
  getVibe,
  getRecommendation,
  getTrend,
  getWaitTime
} from "../services/api";

import CrowdChart from "./CrowdChart";

export default function LiveIntelligence({ placeId }) {
  const [vibe, setVibe] = useState(null);
  const [recommendation, setRecommendation] = useState(null);
  const [trendData, setTrendData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [waitData, setWaitData] = useState(null);

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

    const vibeData = await getVibe(placeId);
    const recData = await getRecommendation(placeId);
    const trend = await getTrend(placeId);
    const wait = await getWaitTime(placeId);
    setVibe(vibeData);
    setRecommendation(recData);
    setTrendData(trend);
    setWaitData(wait);

  } catch (err) {
    console.error("Error loading Live Intelligence:", err);
  } finally {
    setLoading(false);
  }
};

  return (
    <div className="space-y-6">
{loading && (
  <p className="text-gray-400">Updating live data...</p>
)}
      {/* 🔥 TOP CARDS */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">

        {/* Crowd Density */}
        <div className="bg-white rounded-xl shadow p-6">
          <p className="text-gray-500">Current Crowd Density</p>

          <h2 className="text-4xl font-bold mt-2">
            {vibe ? vibe.vibeScore * 10 : 0}%
          </h2>

          <p className="text-gray-500 mt-1">
            {vibe?.label || "Loading..."}
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

      {/* 🤖 AI PREDICTION */}
      {recommendation && (
        <div className="bg-orange-50 border-l-4 border-orange-400 rounded-xl p-5">
          <p className="font-semibold text-orange-700">
            🤖 AI Prediction
          </p>

          <p className="text-gray-700 mt-1">
            {recommendation.recommendation}
          </p>

          <p className="text-gray-500 mt-2">
            Trend: {recommendation.trend}
          </p>
        </div>
      )}

      {/* 📊 GRAPH */}
      <div className="bg-white rounded-xl shadow p-6">
        <p className="text-gray-500 mb-4">
          Today's Hourly Trend
        </p>

        <CrowdChart data={trendData} />
      </div>
    </div>
  );
}