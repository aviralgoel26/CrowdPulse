import { useParams } from "react-router-dom";
import { useEffect, useState } from "react";
import { getPlaceDetails } from "../services/api";
import { getImagesForPlace, handleImageError } from "../services/imageUtils";
import LiveIntelligence from "../components/LiveIntelligence";
import VirtualQueue from "../components/VirtualQueue";
import { motion, AnimatePresence } from "framer-motion";
import { 
  ArrowLeft, 
  MapPin, 
  Clock, 
  Calendar,
  Plane,
  Train,
  Car,
  Sunrise,
  Sunset,
  Sparkles,
  ExternalLink,
  ChevronRight,
  Activity
} from "lucide-react";

export default function PlaceDetail() {
  const { id } = useParams();

  const [tab, setTab] = useState("info");
  const [placeDetails, setPlaceDetails] = useState(null);
  const [imageIndex, setImageIndex] = useState(0);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    loadData();
  }, [id]);

  const loadData = async () => {
    setIsLoading(true);
    try {
      const details = await getPlaceDetails(id);
      setPlaceDetails(details);
    } catch (err) {
      console.error("Error loading data:", err);
    } finally {
      setIsLoading(false);
    }
  };

  // 🔥 SAFE PARSING HELPERS
  const parseJSON = (data, fallback) => {
  try {

    if (!data) return fallback;

    // already parsed array
    if (Array.isArray(data)) {
      return data;
    }

    // already parsed object
    if (typeof data === "object") {
      return data;
    }

    // parse stringified JSON
    return JSON.parse(data);

  } catch (err) {
    console.error("JSON Parse Error:", err);
    return fallback;
  }
};

  //const images = parseJSON(placeDetails?.images, []);
  const rituals = parseJSON(placeDetails?.rituals, []);
  const timings = parseJSON(placeDetails?.dailyTimings, {});
  const reach = parseJSON(placeDetails?.reachInfo, {});

  // Use centralized image utility for consistent image handling across components
  const displayImages = getImagesForPlace(placeDetails?.place?.name, placeDetails?.place?.id);

  // Auto-advance image carousel
  useEffect(() => {
    if (displayImages.length > 1) {
      const timer = setInterval(() => {
        setImageIndex((prev) => (prev + 1) % displayImages.length);
      }, 5000);
      return () => clearInterval(timer);
    }
  }, [displayImages.length]);

  // Loading State
  if (isLoading) {
    return (
      <div className="min-h-screen bg-[#F8FAFC] flex items-center justify-center">
        <motion.div 
          initial={{ opacity: 0, scale: 0.9 }}
          animate={{ opacity: 1, scale: 1 }}
          className="text-center space-y-6"
        >
          <div className="relative w-20 h-20 mx-auto">
            <motion.div
              animate={{ rotate: 360 }}
              transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
              className="absolute inset-0 border-4 border-slate-200 border-t-[#FF9933] rounded-full"
            />
            <motion.div
              animate={{ rotate: -360 }}
              transition={{ duration: 1.5, repeat: Infinity, ease: "linear" }}
              className="absolute inset-2 border-4 border-slate-200 border-t-[#FF9933]/50 rounded-full"
            />
          </div>
          <div className="space-y-2">
            <p className="text-lg text-slate-700 font-semibold">Initializing Intelligence</p>
            <p className="text-sm text-slate-400">Loading operational data...</p>
          </div>
        </motion.div>
      </div>
    );
  }

  if (!placeDetails) {
    return (
      <div className="min-h-screen bg-[#F8FAFC] flex items-center justify-center">
        <p className="text-slate-500">No data available</p>
      </div>
    );
  }

  const tabConfig = [
    { id: "info", label: "Info Hub", icon: Activity },
    { id: "live", label: "Live Intelligence", icon: Activity },
    { id: "virtual", label: "Virtual Tracker", icon: Clock }
  ];

  return (
    <div className="min-h-screen bg-[#F8FAFC]">
      
      {/* HERO SECTION - Keep dark for image overlays */}
      <div className="relative h-[70vh] overflow-hidden">
        {/* Background Image Carousel with Overlay */}
        <div className="absolute inset-0">
          <AnimatePresence mode="wait">
            <motion.div
              key={imageIndex}
              initial={{ opacity: 0, scale: 1.1 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              transition={{ duration: 1 }}
              className="absolute inset-0"
            >
              <img
                src={displayImages[imageIndex]}
                alt={placeDetails?.place?.name}
                className="w-full h-full object-cover"
                onError={(e) => handleImageError(e, placeDetails?.place?.name)}
              />
            </motion.div>
          </AnimatePresence>
          
          {/* Dark Gradient Overlays */}
          <div className="absolute inset-0 bg-gradient-to-t from-[#F8FAFC] via-slate-950/60 to-slate-950/40" />
          <div className="absolute inset-0 bg-gradient-to-r from-slate-950/50 via-transparent to-slate-950/30" />
          
          {/* Grid Pattern Overlay */}
          <div 
            className="absolute inset-0 opacity-5"
            style={{
              backgroundImage: `
                linear-gradient(to right, #FF9933 1px, transparent 1px),
                linear-gradient(to bottom, #FF9933 1px, transparent 1px)
              `,
              backgroundSize: '40px 40px'
            }}
          />
        </div>

        {/* Content Overlay */}
        <div className="relative h-full flex flex-col justify-end px-6 md:px-12 lg:px-16 pb-16 max-w-[1600px] mx-auto">
          
          {/* Top Bar */}
          <div className="absolute top-6 left-0 right-0 px-6 md:px-12 lg:px-16 flex items-center justify-between">
            {/* Back Button */}
            <motion.button
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              onClick={() => window.history.back()}
              className="group flex items-center gap-2 px-4 py-2.5 bg-white/20 backdrop-blur-xl border border-white/20 rounded-xl text-white hover:bg-white/30 transition-all duration-300"
            >
              <ArrowLeft className="w-4 h-4 transition-transform group-hover:-translate-x-1" />
              <span className="font-medium text-sm">Back</span>
            </motion.button>

            {/* Image Indicators */}
            {displayImages.length > 1 && (
              <motion.div 
                initial={{ opacity: 0, y: -20 }}
                animate={{ opacity: 1, y: 0 }}
                className="flex items-center gap-2 px-4 py-2 bg-white/20 backdrop-blur-xl border border-white/20 rounded-xl"
              >
                {displayImages.map((_, idx) => (
                  <button
                    key={idx}
                    onClick={() => setImageIndex(idx)}
                    className="group relative"
                  >
                    <div className={`h-1 rounded-full transition-all duration-300 ${
                      idx === imageIndex 
                        ? "w-8 bg-[#FF9933]" 
                        : "w-1.5 bg-white/50 group-hover:bg-white/70"
                    }`} />
                  </button>
                ))}
              </motion.div>
            )}
          </div>

          {/* Title Section */}
          <motion.div 
            initial={{ opacity: 0, y: 30 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.2 }}
            className="space-y-6"
          >
            {/* Live Badge */}
            <div className="flex items-center gap-4 flex-wrap">
              <div className="inline-flex items-center gap-2.5 px-4 py-2 bg-white/20 backdrop-blur-xl border border-white/20 rounded-full">
                <div className="relative flex items-center justify-center">
                  <span className="absolute w-3 h-3 bg-[#FF9933] rounded-full animate-ping opacity-75" />
                  <span className="relative w-2 h-2 bg-[#FF9933] rounded-full" />
                </div>
                <span className="text-xs font-bold text-white tracking-wide uppercase">Sacred Temple</span>
              </div>
              
              <div className="hidden md:inline-flex items-center gap-2 px-4 py-2 bg-white/15 backdrop-blur-xl border border-white/15 rounded-full">
                <Activity className="w-3.5 h-3.5 text-[#FF9933]" />
                <span className="text-xs font-semibold text-white/80">Operational Intelligence Active</span>
              </div>
            </div>
            
            {/* Main Title */}
            <div className="space-y-3">
              <h1 className="text-5xl md:text-6xl lg:text-7xl font-bold text-white tracking-tight leading-none">
                {placeDetails.place?.name}
              </h1>
              
              <div className="flex items-center gap-3 text-white/80">
                <MapPin className="w-5 h-5 text-[#FF9933]" />
                <span className="text-lg md:text-xl font-medium">
                  {placeDetails.place?.city}, {placeDetails.place?.state}
                </span>
              </div>
            </div>
          </motion.div>
        </div>

        {/* Scroll Indicator */}
        <motion.div 
          initial={{ opacity: 0 }}
          animate={{ opacity: 1, y: [0, 8, 0] }}
          transition={{ 
            opacity: { delay: 0.5 },
            y: { repeat: Infinity, duration: 2, ease: "easeInOut" }
          }}
          className="absolute bottom-8 left-1/2 -translate-x-1/2"
        >
          <div className="w-6 h-10 border-2 border-slate-400 rounded-full p-1.5">
            <div className="w-1.5 h-1.5 bg-[#FF9933] rounded-full mx-auto" />
          </div>
        </motion.div>
      </div>

      {/* MAIN CONTENT */}
      <div className="max-w-[1600px] mx-auto px-6 md:px-12 lg:px-16 -mt-24 relative z-10 pb-20">
        
        {/* Tab Navigation */}
        <motion.div 
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="mb-8"
        >
          <div className="inline-flex items-center gap-2 p-1.5 bg-white border border-slate-200 rounded-2xl shadow-sm">
            {tabConfig.map(({ id, label, icon: Icon }) => (
              <button
                key={id}
                onClick={() => setTab(id)}
                className="relative group"
              >
                {tab === id && (
                  <motion.div
                    layoutId="activeTab"
                    className="absolute inset-0 bg-[#FF9933] rounded-xl"
                    transition={{ type: "spring", bounce: 0.2, duration: 0.6 }}
                  />
                )}
                <div className={`relative flex items-center gap-2.5 px-6 py-3.5 rounded-xl font-semibold text-sm transition-colors duration-300 ${
                  tab === id
                    ? "text-white"
                    : "text-slate-500 hover:text-slate-800"
                }`}>
                  <Icon className="w-4 h-4" />
                  <span>{label}</span>
                </div>
              </button>
            ))}
          </div>
        </motion.div>

        {/* INFO TAB */}
        <AnimatePresence mode="wait">
          {tab === "info" && (
            <motion.div
              key="info"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -20 }}
              transition={{ duration: 0.3 }}
              className="grid grid-cols-1 lg:grid-cols-3 gap-6"
            >
              
              {/* LEFT COLUMN */}
              <div className="lg:col-span-2 space-y-6">
                
                {/* History & Significance Card */}
                <motion.div 
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.1 }}
                  className="group bg-white border border-slate-200 rounded-2xl p-8 hover:border-slate-300 hover:shadow-md transition-all duration-300"
                >
                  <div className="flex items-start gap-4 mb-6">
                    <div className="p-3 bg-[#FF9933]/10 border border-[#FF9933]/20 rounded-xl">
                      <Sparkles className="w-6 h-6 text-[#FF9933]" />
                    </div>
                    <div className="flex-1">
                      <h2 className="text-2xl font-bold text-slate-900 mb-1">History & Significance</h2>
                      <p className="text-sm text-slate-500">Ancient heritage and spiritual legacy</p>
                    </div>
                  </div>

                  <p className="text-slate-600 leading-relaxed text-base mb-6">
                    {placeDetails.description}
                  </p>

                  {/* Why it's Sacred */}
                  <div className="relative overflow-hidden bg-[#FF9933]/5 border border-[#FF9933]/20 rounded-xl p-6">
                    <div className="absolute top-0 right-0 w-32 h-32 bg-[#FF9933]/5 rounded-full -mr-16 -mt-16" />
                    
                    <div className="relative flex items-start gap-4">
                      <div className="flex-shrink-0 w-10 h-10 bg-[#FF9933] rounded-full flex items-center justify-center">
                        <Sparkles className="w-5 h-5 text-white" />
                      </div>
                      <div className="flex-1">
                        <p className="font-bold text-[#FF9933] mb-2 text-sm uppercase tracking-wide">Why it's sacred</p>
                        <p className="text-slate-600 leading-relaxed">
                          {placeDetails.significance}
                        </p>
                      </div>
                    </div>
                  </div>
                </motion.div>

                {/* How to Reach Card */}
                <motion.div 
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.2 }}
                  className="bg-white border border-slate-200 rounded-2xl p-8 hover:border-slate-300 hover:shadow-md transition-all duration-300"
                >
                  <div className="flex items-start gap-4 mb-6">
                    <div className="p-3 bg-blue-50 border border-blue-200 rounded-xl">
                      <MapPin className="w-6 h-6 text-blue-500" />
                    </div>
                    <div className="flex-1">
                      <h2 className="text-2xl font-bold text-slate-900 mb-1">How to Reach</h2>
                      <p className="text-sm text-slate-500">Multiple routes to your destination</p>
                    </div>
                  </div>

                  <div className="grid md:grid-cols-3 gap-4">
                    
                    {/* By Air */}
                    <div className="group relative overflow-hidden bg-slate-50 border border-slate-200 hover:border-blue-300 rounded-xl p-6 transition-all duration-300 hover:translate-y-[-2px] hover:shadow-sm">
                      <div className="absolute inset-0 bg-gradient-to-br from-blue-500/0 to-blue-500/0 group-hover:from-blue-50 group-hover:to-blue-100/50 transition-all duration-300" />
                      
                      <div className="relative">
                        <div className="w-12 h-12 bg-blue-50 border border-blue-200 rounded-xl flex items-center justify-center mb-4 group-hover:scale-110 transition-transform duration-300">
                          <Plane className="w-6 h-6 text-blue-500" />
                        </div>
                        <p className="font-bold text-slate-800 mb-2 text-sm uppercase tracking-wide">By Air</p>
                        <p className="text-slate-500 text-sm leading-relaxed">
                          {reach.byAir || "Not available"}
                        </p>
                      </div>
                    </div>

                    {/* By Rail */}
                    <div className="group relative overflow-hidden bg-slate-50 border border-slate-200 hover:border-emerald-300 rounded-xl p-6 transition-all duration-300 hover:translate-y-[-2px] hover:shadow-sm">
                      <div className="absolute inset-0 bg-gradient-to-br from-emerald-500/0 to-emerald-500/0 group-hover:from-emerald-50 group-hover:to-emerald-100/50 transition-all duration-300" />
                      
                      <div className="relative">
                        <div className="w-12 h-12 bg-emerald-50 border border-emerald-200 rounded-xl flex items-center justify-center mb-4 group-hover:scale-110 transition-transform duration-300">
                          <Train className="w-6 h-6 text-emerald-500" />
                        </div>
                        <p className="font-bold text-slate-800 mb-2 text-sm uppercase tracking-wide">By Rail</p>
                        <p className="text-slate-500 text-sm leading-relaxed">
                          {reach.byRail || "Not available"}
                        </p>
                      </div>
                    </div>

                    {/* By Road */}
                    <div className="group relative overflow-hidden bg-slate-50 border border-slate-200 hover:border-[#FF9933]/50 rounded-xl p-6 transition-all duration-300 hover:translate-y-[-2px] hover:shadow-sm">
                      <div className="absolute inset-0 bg-gradient-to-br from-[#FF9933]/0 to-[#FF9933]/0 group-hover:from-orange-50 group-hover:to-orange-100/50 transition-all duration-300" />
                      
                      <div className="relative">
                        <div className="w-12 h-12 bg-[#FF9933]/10 border border-[#FF9933]/20 rounded-xl flex items-center justify-center mb-4 group-hover:scale-110 transition-transform duration-300">
                          <Car className="w-6 h-6 text-[#FF9933]" />
                        </div>
                        <p className="font-bold text-slate-800 mb-2 text-sm uppercase tracking-wide">By Road</p>
                        <p className="text-slate-500 text-sm leading-relaxed">
                          {reach.byRoad || "Not available"}
                        </p>
                      </div>
                    </div>
                  </div>
                </motion.div>
              </div>

              {/* RIGHT COLUMN - Sticky Sidebar */}
              <div className="space-y-6">
                
                {/* Temple Timings Card */}
                <motion.div 
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.3 }}
                  className="bg-white border border-slate-200 rounded-2xl p-6 sticky top-8"
                >
                  <div className="flex items-center gap-3 mb-6">
                    <div className="p-2.5 bg-[#FF9933] rounded-xl">
                      <Clock className="w-5 h-5 text-white" />
                    </div>
                    <h2 className="text-xl font-bold text-slate-900">Temple Timings</h2>
                  </div>

                  {/* Opening & Closing Times */}
                  <div className="space-y-3 mb-6">
                    <div className="flex items-center justify-between p-4 bg-slate-50 border border-slate-200 rounded-xl">
                      <div className="flex items-center gap-3">
                        <div className="w-10 h-10 bg-green-50 border border-green-200 rounded-full flex items-center justify-center">
                          <Sunrise className="w-5 h-5 text-green-500" />
                        </div>
                        <div>
                          <p className="text-xs text-slate-500 font-medium uppercase tracking-wide">Opens</p>
                          <p className="text-lg font-bold text-slate-900">{timings.open || "N/A"}</p>
                        </div>
                      </div>
                    </div>

                    <div className="flex items-center justify-between p-4 bg-slate-50 border border-slate-200 rounded-xl">
                      <div className="flex items-center gap-3">
                        <div className="w-10 h-10 bg-indigo-50 border border-indigo-200 rounded-full flex items-center justify-center">
                          <Sunset className="w-5 h-5 text-indigo-500" />
                        </div>
                        <div>
                          <p className="text-xs text-slate-500 font-medium uppercase tracking-wide">Closes</p>
                          <p className="text-lg font-bold text-slate-900">{timings.close || "N/A"}</p>
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* Ritual Timings */}
                  <div className="border-t border-slate-200 pt-6">
                    <h3 className="text-base font-bold text-slate-900 mb-4 flex items-center gap-2">
                      <Sparkles className="w-4 h-4 text-[#FF9933]" />
                      Ritual Timings
                    </h3>

                    {rituals.length > 0 ? (
                      <div className="space-y-2 max-h-[400px] overflow-y-auto custom-scrollbar pr-2">
                        {rituals.map((r, i) => (
                          <motion.div
                            key={i}
                            initial={{ opacity: 0, x: -10 }}
                            animate={{ opacity: 1, x: 0 }}
                            transition={{ delay: i * 0.05 }}
                            className="group flex items-center justify-between p-3 bg-slate-50 hover:bg-slate-100 border border-slate-200 hover:border-[#FF9933]/30 rounded-xl transition-all duration-200"
                          >
                            <span className="text-slate-700 font-medium text-sm">{r.name}</span>
                            <span className="px-3 py-1 bg-[#FF9933] text-white text-xs font-bold rounded-full">
                              {r.time}
                            </span>
                          </motion.div>
                        ))}
                      </div>
                    ) : (
                      <p className="text-slate-400 text-sm text-center py-6">
                        No rituals scheduled
                      </p>
                    )}
                  </div>

                  {/* Book Tickets Button */}
                  <motion.button 
                    whileHover={{ scale: 1.02 }}
                    whileTap={{ scale: 0.98 }}
                    className="w-full mt-6 bg-[#FF9933] hover:bg-[#FF9933]/90 text-white py-4 rounded-xl font-bold transition-all duration-300 flex items-center justify-center gap-2 group"
                  >
                    <span>Book Official Tickets</span>
                    <ExternalLink className="w-4 h-4 group-hover:translate-x-1 group-hover:-translate-y-1 transition-transform" />
                  </motion.button>
                </motion.div>
              </div>
            </motion.div>
          )}

          {/* LIVE TAB */}
          {tab === "live" && (
            <motion.div
              key="live"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -20 }}
              transition={{ duration: 0.3 }}
            >
              <LiveIntelligence placeId={id} />
            </motion.div>
          )}

          {/* VIRTUAL TAB */}
          {tab === "virtual" && (
            <motion.div
              key="virtual"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -20 }}
              transition={{ duration: 0.3 }}
            >
              <VirtualQueue placeId={id} />
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {/* Custom Scrollbar Styles */}
      <style>{`
        .custom-scrollbar::-webkit-scrollbar {
          width: 6px;
        }
        .custom-scrollbar::-webkit-scrollbar-track {
          background: rgba(241, 245, 249, 0.5);
          border-radius: 10px;
        }
        .custom-scrollbar::-webkit-scrollbar-thumb {
          background: rgba(255, 153, 51, 0.3);
          border-radius: 10px;
        }
        .custom-scrollbar::-webkit-scrollbar-thumb:hover {
          background: rgba(255, 153, 51, 0.5);
        }
      `}</style>
    </div>
  );
}