import { useParams } from "react-router-dom";
import { useEffect, useState } from "react";

import { getVibe, getRecommendation } from "../services/api";
import LiveIntelligence from "../components/LiveIntelligence";

export default function PlaceDetail() {
  const { id } = useParams();

  // ✅ FIX: hooks inside component
  const [tab, setTab] = useState("info");
  const [place, setPlace] = useState(null);
  const [vibe, setVibe] = useState(null);
  const [recommendation, setRecommendation] = useState(null);

  useEffect(() => {
    loadData();
  }, [id]);

  const loadData = async () => {
    const placeData = await getPlaceDetails(id);
    const vibeData = await getVibe(id);
    const recData = await getRecommendation(id);
    
    setPlace(placeData);
    setVibe(vibeData);
    setRecommendation(recData);
    
  };

  return (
    <div className="p-6 max-w-6xl mx-auto">

      {/* 🔙 HEADER */}
      <h1 className="text-3xl font-bold mb-6">
        Place Details
      </h1>

      {/* 🔥 TABS */}
      <div className="flex gap-6 border-b mb-6">

        <button
          onClick={() => setTab("info")}
          className={`pb-2 ${
            tab === "info"
              ? "border-b-2 border-orange-500 font-semibold"
              : "text-gray-500"
          }`}
        >
          Info Hub
        </button>

        <button
          onClick={() => setTab("live")}
          className={`pb-2 ${
            tab === "live"
              ? "border-b-2 border-orange-500 font-semibold"
              : "text-gray-500"
          }`}
        >
          Live Intelligence
        </button>

        <button
          onClick={() => setTab("virtual")}
          className={`pb-2 ${
            tab === "virtual"
              ? "border-b-2 border-orange-500 font-semibold"
              : "text-gray-500"
          }`}
        >
          Virtual Tracker
        </button>

      </div>

      {/* 📦 CONTENT */}
      <div>

        {/* INFO TAB */}
        {tab === "info" && place && (
  <div className="space-y-6">

    {/* TITLE */}
    <div>
      <h2 className="text-2xl font-bold">
        {place.name}
      </h2>
      <p className="text-gray-500">
        {place.city}, {place.state}
      </p>
    </div>

    {/* IMAGE */}
    <img
      src={place.images?.[0]}
      alt={place.name}
      className="w-full h-64 object-cover rounded-xl"
    />

    {/* HISTORY */}
    <div>
      <h3 className="font-semibold text-lg">
        History & Significance
      </h3>
      <p className="text-gray-600 mt-2">
        {place.description}
      </p>
    </div>

    {/* BEST TIME */}
    <div>
      <h3 className="font-semibold text-lg">
        Best Time to Visit
      </h3>
      <p className="text-gray-600 mt-2">
        {place.bestTime}
      </p>
    </div>

  </div>
)}
        {/* LIVE INTELLIGENCE TAB */}
        {tab === "live" && (
          <LiveIntelligence placeId={id} />
        )}

        {/* VIRTUAL TRACKER TAB */}
        {tab === "virtual" && (
          <div>
            🚧 Virtual queue coming soon...
          </div>
        )}

      </div>
    </div>
  );
}