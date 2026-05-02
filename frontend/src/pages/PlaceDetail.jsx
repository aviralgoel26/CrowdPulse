import { useParams } from "react-router-dom";
import { useEffect, useState } from "react";
import { getPlaceDetails, getVibe, getRecommendation } from "../services/api";
import LiveIntelligence from "../components/LiveIntelligence";

export default function PlaceDetail() {
  const { id } = useParams();

  const [tab, setTab] = useState("info");
  const [placeDetails, setPlaceDetails] = useState(null);
  const [vibe, setVibe] = useState(null);
  const [recommendation, setRecommendation] = useState(null);
  const [imageIndex, setImageIndex] = useState(0);

  useEffect(() => {
    loadData();
  }, [id]);

  const loadData = async () => {
    try {
      const details = await getPlaceDetails(id);
      const vibeData = await getVibe(id);
      const recData = await getRecommendation(id);

      setPlaceDetails(details);
      setVibe(vibeData);
      setRecommendation(recData);
    } catch (err) {
      console.error("Error loading data:", err);
    }
  };

  // 🔥 SAFE PARSING HELPERS
  const parseJSON = (data, fallback) => {
    try {
      return JSON.parse(data);
    } catch {
      return fallback;
    }
  };

  const images = parseJSON(placeDetails?.images, []);
  const rituals = parseJSON(placeDetails?.rituals, []);
  const timings = parseJSON(placeDetails?.dailyTimings, {});
  const reach = parseJSON(placeDetails?.reachInfo, {});

  // Auto-advance image carousel
  useEffect(() => {
    if (images.length > 1) {
      const timer = setInterval(() => {
        setImageIndex((prev) => (prev + 1) % images.length);
      }, 5000);
      return () => clearInterval(timer);
    }
  }, [images.length]);

  if (!placeDetails) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-orange-50 via-white to-amber-50">
        <div className="text-center space-y-4">
          <div className="w-16 h-16 mx-auto border-4 border-orange-500 border-t-transparent rounded-full animate-spin"></div>
          <p className="text-lg text-gray-600 font-medium">Loading details...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-orange-50/30 via-white to-amber-50/30">
      
      {/* HERO SECTION */}
      <div className="relative h-[75vh] overflow-hidden">
        {/* Background Image Carousel */}
        <div className="absolute inset-0">
          {(images.length > 0 ? images : [""]).map((img, idx) => (
            <div
              key={idx}
              className={`absolute inset-0 transition-opacity duration-1000 ${
                idx === imageIndex ? "opacity-100" : "opacity-0"
              }`}
            >
              <img
  src={
    img?.startsWith("http")
      ? img
      : `http://localhost:8081${img}`
  }
  onError={(e) => {
    e.target.src = "https://images.unsplash.com/photo-1582510003544-4d00b7f74220";
  }}
                alt={placeDetails.name}
                className="w-full h-full object-cover"
              />
            </div>
          ))}
          
          {/* Gradient Overlays */}
          <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/40 to-transparent"></div>
          <div className="absolute inset-0 bg-gradient-to-r from-black/50 via-transparent to-transparent"></div>
          
          {/* Decorative Pattern Overlay */}
          <div className="absolute inset-0 opacity-10"
            style={{
              backgroundImage: `url("data:image/svg+xml,%3Csvg width='60' height='60' viewBox='0 0 60 60' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath d='M30 0l30 30-30 30L0 30z' fill='%23ff9933' fill-opacity='0.4'/%3E%3C/svg%3E")`,
              backgroundSize: '30px 30px'
            }}
          ></div>
        </div>

        {/* Content Overlay */}
        <div className="relative h-full flex flex-col justify-end px-6 md:px-12 pb-12 max-w-7xl mx-auto">
          
          {/* Back Button */}
          <button
            onClick={() => window.history.back()}
            className="absolute top-8 left-6 md:left-12 bg-white/20 backdrop-blur-md hover:bg-white/30 text-white px-5 py-2.5 rounded-full font-medium transition-all duration-300 flex items-center gap-2 border border-white/30 group"
          >
            <svg className="w-5 h-5 transition-transform group-hover:-translate-x-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
            Back
          </button>

          {/* Image Indicators */}
          {images.length > 1 && (
            <div className="absolute top-8 right-6 md:right-12 flex gap-2">
              {images.map((_, idx) => (
                <button
                  key={idx}
                  onClick={() => setImageIndex(idx)}
                  className={`h-1.5 rounded-full transition-all duration-300 ${
                    idx === imageIndex 
                      ? "w-12 bg-white" 
                      : "w-6 bg-white/40 hover:bg-white/60"
                  }`}
                />
              ))}
            </div>
          )}

          {/* Title Section */}
          <div className="space-y-4 animate-fadeInUp">
            <div className="inline-flex items-center gap-2 bg-orange-500/90 backdrop-blur-sm px-4 py-2 rounded-full text-white text-sm font-semibold">
              <span className="w-2 h-2 bg-white rounded-full animate-pulse"></span>
              Sacred Temple
            </div>
            
            <h1 className="text-5xl md:text-7xl font-bold text-white drop-shadow-2xl leading-tight">
              {placeDetails.name}
            </h1>
            
            <div className="flex items-center gap-2 text-white/90 text-lg">
              <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
                <path fillRule="evenodd" d="M5.05 4.05a7 7 0 119.9 9.9L10 18.9l-4.95-4.95a7 7 0 010-9.9zM10 11a2 2 0 100-4 2 2 0 000 4z" clipRule="evenodd" />
              </svg>
              {placeDetails.city}, {placeDetails.state}
            </div>
          </div>
        </div>

        {/* Scroll Indicator */}
        <div className="absolute bottom-8 left-1/2 -translate-x-1/2 animate-bounce">
          <svg className="w-6 h-6 text-white/70" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 14l-7 7m0 0l-7-7m7 7V3" />
          </svg>
        </div>
      </div>

      {/* MAIN CONTENT */}
      <div className="max-w-7xl mx-auto px-6 md:px-12 -mt-20 relative z-10">
        
        {/* Tabs */}
        <div className="bg-white rounded-2xl shadow-2xl shadow-orange-500/10 p-2 mb-8 border border-gray-100">
          <div className="flex gap-2">
            <button
              onClick={() => setTab("info")}
              className={`flex-1 py-4 px-6 rounded-xl font-semibold transition-all duration-300 ${
                tab === "info"
                  ? "bg-gradient-to-r from-orange-500 to-amber-500 text-white shadow-lg shadow-orange-500/30"
                  : "text-gray-600 hover:bg-gray-50"
              }`}
            >
              <div className="flex items-center justify-center gap-2">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                Info Hub
              </div>
            </button>

            <button
              onClick={() => setTab("live")}
              className={`flex-1 py-4 px-6 rounded-xl font-semibold transition-all duration-300 ${
                tab === "live"
                  ? "bg-gradient-to-r from-orange-500 to-amber-500 text-white shadow-lg shadow-orange-500/30"
                  : "text-gray-600 hover:bg-gray-50"
              }`}
            >
              <div className="flex items-center justify-center gap-2">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                </svg>
                Live Intelligence
              </div>
            </button>

            <button
              onClick={() => setTab("virtual")}
              className={`flex-1 py-4 px-6 rounded-xl font-semibold transition-all duration-300 ${
                tab === "virtual"
                  ? "bg-gradient-to-r from-orange-500 to-amber-500 text-white shadow-lg shadow-orange-500/30"
                  : "text-gray-600 hover:bg-gray-50"
              }`}
            >
              <div className="flex items-center justify-center gap-2">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                Virtual Tracker
              </div>
            </button>
          </div>
        </div>

        {/* INFO TAB */}
        {tab === "info" && (
          <div className="space-y-8 pb-20">
            
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
              
              {/* LEFT COLUMN */}
              <div className="lg:col-span-2 space-y-8">
                
                {/* History & Significance */}
                <div className="group bg-white rounded-2xl p-8 shadow-xl shadow-gray-200/50 border border-gray-100 hover:shadow-2xl hover:shadow-orange-500/10 transition-all duration-500">
                  <div className="flex items-start gap-4 mb-6">
                    <div className="p-3 bg-gradient-to-br from-orange-100 to-amber-100 rounded-xl">
                      <svg className="w-6 h-6 text-orange-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
                      </svg>
                    </div>
                    <div>
                      <h2 className="text-2xl font-bold text-gray-900 mb-1">History & Significance</h2>
                      <p className="text-sm text-gray-500">Ancient heritage and spiritual legacy</p>
                    </div>
                  </div>

                  <p className="text-gray-700 leading-relaxed text-lg mb-6">
                    {placeDetails.description}
                  </p>

                  <div className="relative overflow-hidden bg-gradient-to-br from-orange-50 via-amber-50 to-orange-50 border-2 border-orange-200 rounded-2xl p-6">
                    {/* Decorative Elements */}
                    <div className="absolute top-0 right-0 w-32 h-32 bg-orange-200/20 rounded-full -mr-16 -mt-16"></div>
                    <div className="absolute bottom-0 left-0 w-24 h-24 bg-amber-200/20 rounded-full -ml-12 -mb-12"></div>
                    
                    <div className="relative flex items-start gap-3">
                      <div className="flex-shrink-0 w-10 h-10 bg-gradient-to-br from-orange-500 to-amber-500 rounded-full flex items-center justify-center shadow-lg">
                        <svg className="w-5 h-5 text-white" fill="currentColor" viewBox="0 0 20 20">
                          <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                        </svg>
                      </div>
                      <div className="flex-1">
                        <p className="font-bold text-orange-700 mb-2 text-lg">Why it's sacred</p>
                        <p className="text-gray-700 leading-relaxed">
                          {placeDetails.significance}
                        </p>
                      </div>
                    </div>
                  </div>
                </div>

                {/* How to Reach */}
                <div className="bg-white rounded-2xl p-8 shadow-xl shadow-gray-200/50 border border-gray-100 hover:shadow-2xl hover:shadow-orange-500/10 transition-all duration-500">
                  <div className="flex items-start gap-4 mb-6">
                    <div className="p-3 bg-gradient-to-br from-blue-100 to-indigo-100 rounded-xl">
                      <svg className="w-6 h-6 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 20l-5.447-2.724A1 1 0 013 16.382V5.618a1 1 0 011.447-.894L9 7m0 13l6-3m-6 3V7m6 10l4.553 2.276A1 1 0 0021 18.382V7.618a1 1 0 00-.553-.894L15 4m0 13V4m0 0L9 7" />
                      </svg>
                    </div>
                    <div>
                      <h2 className="text-2xl font-bold text-gray-900 mb-1">How to Reach</h2>
                      <p className="text-sm text-gray-500">Multiple routes to your spiritual destination</p>
                    </div>
                  </div>

                  <div className="grid md:grid-cols-3 gap-6">
                    
                    {/* By Air */}
                    <div className="group relative overflow-hidden bg-gradient-to-br from-sky-50 to-blue-50 rounded-2xl p-6 border-2 border-sky-100 hover:border-sky-300 transition-all duration-300 hover:shadow-lg hover:-translate-y-1">
                      <div className="absolute top-0 right-0 w-20 h-20 bg-sky-200/20 rounded-full -mr-10 -mt-10"></div>
                      <div className="relative">
                        <div className="w-12 h-12 bg-gradient-to-br from-sky-400 to-blue-500 rounded-xl flex items-center justify-center mb-4 shadow-lg group-hover:scale-110 transition-transform">
                          <svg className="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
                          </svg>
                        </div>
                        <p className="font-bold text-gray-900 mb-2">By Air</p>
                        <p className="text-gray-600 text-sm leading-relaxed">
                          {reach.byAir || "Not available"}
                        </p>
                      </div>
                    </div>

                    {/* By Rail */}
                    <div className="group relative overflow-hidden bg-gradient-to-br from-emerald-50 to-green-50 rounded-2xl p-6 border-2 border-emerald-100 hover:border-emerald-300 transition-all duration-300 hover:shadow-lg hover:-translate-y-1">
                      <div className="absolute top-0 right-0 w-20 h-20 bg-emerald-200/20 rounded-full -mr-10 -mt-10"></div>
                      <div className="relative">
                        <div className="w-12 h-12 bg-gradient-to-br from-emerald-400 to-green-500 rounded-xl flex items-center justify-center mb-4 shadow-lg group-hover:scale-110 transition-transform">
                          <svg className="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 3v4M3 5h4M6 17v4m-2-2h4m5-16l2.286 6.857L21 12l-5.714 2.143L13 21l-2.286-6.857L5 12l5.714-2.143L13 3z" />
                          </svg>
                        </div>
                        <p className="font-bold text-gray-900 mb-2">By Rail</p>
                        <p className="text-gray-600 text-sm leading-relaxed">
                          {reach.byRail || "Not available"}
                        </p>
                      </div>
                    </div>

                    {/* By Road */}
                    <div className="group relative overflow-hidden bg-gradient-to-br from-amber-50 to-orange-50 rounded-2xl p-6 border-2 border-amber-100 hover:border-amber-300 transition-all duration-300 hover:shadow-lg hover:-translate-y-1">
                      <div className="absolute top-0 right-0 w-20 h-20 bg-amber-200/20 rounded-full -mr-10 -mt-10"></div>
                      <div className="relative">
                        <div className="w-12 h-12 bg-gradient-to-br from-amber-400 to-orange-500 rounded-xl flex items-center justify-center mb-4 shadow-lg group-hover:scale-110 transition-transform">
                          <svg className="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
                          </svg>
                        </div>
                        <p className="font-bold text-gray-900 mb-2">By Road</p>
                        <p className="text-gray-600 text-sm leading-relaxed">
                          {reach.byRoad || "Not available"}
                        </p>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              {/* RIGHT COLUMN */}
              <div className="space-y-8">
                
                {/* Temple Timings */}
                <div className="bg-gradient-to-br from-white to-orange-50/30 rounded-2xl p-6 shadow-xl shadow-gray-200/50 border-2 border-orange-100 sticky top-8">
                  <div className="flex items-center gap-3 mb-6">
                    <div className="p-2.5 bg-gradient-to-br from-orange-500 to-amber-500 rounded-xl shadow-lg">
                      <svg className="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                      </svg>
                    </div>
                    <h2 className="text-xl font-bold text-gray-900">Temple Timings</h2>
                  </div>

                  <div className="space-y-3 mb-6">
                    <div className="flex items-center justify-between p-4 bg-white rounded-xl border border-orange-100">
                      <div className="flex items-center gap-3">
                        <div className="w-10 h-10 bg-green-100 rounded-full flex items-center justify-center">
                          <svg className="w-5 h-5 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z" />
                          </svg>
                        </div>
                        <div>
                          <p className="text-xs text-gray-500 font-medium">Opens</p>
                          <p className="text-lg font-bold text-gray-900">{timings.open || "N/A"}</p>
                        </div>
                      </div>
                    </div>

                    <div className="flex items-center justify-between p-4 bg-white rounded-xl border border-orange-100">
                      <div className="flex items-center gap-3">
                        <div className="w-10 h-10 bg-indigo-100 rounded-full flex items-center justify-center">
                          <svg className="w-5 h-5 text-indigo-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z" />
                          </svg>
                        </div>
                        <div>
                          <p className="text-xs text-gray-500 font-medium">Closes</p>
                          <p className="text-lg font-bold text-gray-900">{timings.close || "N/A"}</p>
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* Ritual Timings */}
                  <div className="border-t-2 border-orange-100 pt-6">
                    <h3 className="text-lg font-bold text-gray-900 mb-4 flex items-center gap-2">
                      <svg className="w-5 h-5 text-orange-500" fill="currentColor" viewBox="0 0 20 20">
                        <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                      </svg>
                      Ritual Timings
                    </h3>

                    {rituals.length > 0 ? (
                      <div className="space-y-2 max-h-80 overflow-y-auto pr-2 custom-scrollbar">
                        {rituals.map((r, i) => (
                          <div
                            key={i}
                            className="group flex items-center justify-between p-3 bg-white hover:bg-orange-50 rounded-xl border border-gray-100 hover:border-orange-200 transition-all duration-200"
                          >
                            <span className="text-gray-700 font-medium text-sm">{r.name}</span>
                            <span className="px-3 py-1 bg-gradient-to-r from-orange-500 to-amber-500 text-white text-xs font-bold rounded-full shadow-sm">
                              {r.time}
                            </span>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <p className="text-gray-500 text-sm text-center py-4">
                        No rituals scheduled
                      </p>
                    )}
                  </div>

                  {/* Book Tickets Button */}
                  <button className="w-full mt-6 bg-gradient-to-r from-orange-500 to-amber-500 hover:from-orange-600 hover:to-amber-600 text-white py-4 rounded-xl font-bold shadow-xl shadow-orange-500/30 hover:shadow-2xl hover:shadow-orange-500/40 transition-all duration-300 flex items-center justify-center gap-2 group">
                    <span>Book Official Tickets</span>
                    <svg className="w-5 h-5 group-hover:translate-x-1 transition-transform" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 7l5 5m0 0l-5 5m5-5H6" />
                    </svg>
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* LIVE TAB */}
        {tab === "live" && (
          <div className="pb-20">
            <LiveIntelligence placeId={id} />
          </div>
        )}

        {/* VIRTUAL TAB */}
        {tab === "virtual" && (
          <div className="pb-20">
            <div className="bg-white rounded-2xl p-12 shadow-xl text-center border border-gray-100">
              <div className="w-24 h-24 bg-gradient-to-br from-orange-100 to-amber-100 rounded-full flex items-center justify-center mx-auto mb-6">
                <svg className="w-12 h-12 text-orange-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
              </div>
              <h3 className="text-2xl font-bold text-gray-900 mb-3">Virtual Queue Coming Soon</h3>
              <p className="text-gray-600 max-w-md mx-auto">
                We're working on an intelligent virtual queue system to enhance your temple visit experience.
              </p>
            </div>
          </div>
        )}
      </div>

      
    </div>
  );
}