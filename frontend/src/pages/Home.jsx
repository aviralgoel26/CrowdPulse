import { useEffect, useState } from "react";
import { getPlaces } from "../services/api";
import Navbar from "../components/Navbar";
import Hero from "../components/Hero";
import PlaceCard from "../components/Placecard";

export default function Home() {
  const [places, setPlaces] = useState([]);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    const data = await getPlaces();
    console.log("DATA FROM API:", data); // 🔥 IMPORTANT
    setPlaces(data);
  };

  return (
    <>
      <Navbar />
      <Hero />

      <div className="mt-16 px-6 max-w-6xl mx-auto">

        <h2 className="text-2xl font-bold mb-6 text-left">
          Live around India
        </h2>

        {/* DEBUG VIEW */}
        {places.length === 0 ? (
          <p>No places found</p>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
    {places.map((p) => (
      <PlaceCard key={p.id} place={p} />
    ))}
  </div>
        )}
      </div>
      
    </>
  );
}