import { useEffect, useState } from "react";
import { getPlaces } from "../services/api";
import Navbar from "../components/Navbar";
import PlaceCard from "../components/PlaceCard";
import { motion } from "framer-motion";
import { Sparkles, Activity, Clock, ShieldCheck, Search } from "lucide-react";

const features = [
  { icon: Activity, label: "Live density" },
  { icon: Clock, label: "AI wait time" },
  { icon: ShieldCheck, label: "Verified data" },
];

export default function Home() {
  const [places, setPlaces] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState("");

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    setLoading(true);
    const data = await getPlaces();
    console.log("DATA FROM API:", data); // 🔥 IMPORTANT
    setPlaces(data);
    setLoading(false);
  };

  const filtered = places.filter(
    (p) =>
      p.name?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      p.city?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      p.state?.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <div className="min-h-screen bg-[#F8FAFC]">
      <Navbar />

      {/* ═══ HERO SECTION ═══ */}
      <section className="relative overflow-hidden">
        {/* Subtle grid bg */}
        <div
          className="absolute inset-0 opacity-[0.03]"
          style={{
            backgroundImage:
              "linear-gradient(to right, #FF9933 1px, transparent 1px), linear-gradient(to bottom, #FF9933 1px, transparent 1px)",
            backgroundSize: "48px 48px",
          }}
        />
        {/* Radial glow */}
        <div className="absolute top-0 left-1/2 -translate-x-1/2 w-[800px] h-[500px] bg-[#FF9933]/5 rounded-full blur-3xl pointer-events-none" />

        <div className="relative max-w-[1600px] mx-auto px-6 md:px-12 lg:px-16 pt-20 pb-16 md:pt-28 md:pb-24">
          {/* Badge */}
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            className="flex justify-start mb-8"
          >
            <div className="inline-flex items-center gap-2 px-4 py-2 bg-white border border-slate-200 rounded-full shadow-sm">
              <Sparkles className="w-4 h-4 text-[#FF9933]" />
              <span className="text-xs font-semibold text-slate-600">
                AI-powered pilgrimage intelligence
              </span>
            </div>
          </motion.div>

          {/* Heading */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.1 }}
            className="max-w-3xl"
          >
            <h1 className="text-5xl md:text-6xl lg:text-7xl font-bold text-slate-900 tracking-tight leading-[1.1]">
              Know the crowd,
              <br />
              <span className="relative inline-block mt-2">
                <span className="relative z-10">before you go.</span>
                <span className="absolute bottom-1 left-0 right-0 h-[0.35em] bg-[#FF9933]/30 rounded-sm -z-0" />
              </span>
            </h1>
          </motion.div>

          {/* Subtitle */}
          <motion.p
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.2 }}
            className="mt-6 text-base md:text-lg text-slate-500 max-w-xl leading-relaxed"
          >
            India's first universal density-awareness platform. From Himalayan
            shrines to Mumbai promenades — skip the wait, time it right.
          </motion.p>

          {/* Feature chips */}
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.3 }}
            className="flex flex-wrap gap-3 mt-8"
          >
            {features.map(({ icon: Icon, label }) => (
              <div
                key={label}
                className="flex items-center gap-2 px-4 py-2.5 bg-white border border-slate-200 rounded-xl text-sm text-slate-600 font-medium shadow-sm"
              >
                <Icon className="w-4 h-4 text-[#FF9933]" />
                {label}
              </div>
            ))}
          </motion.div>

          {/* Search */}
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.4 }}
            className="mt-10 max-w-2xl"
          >
            <div className="relative">
              <Search className="absolute left-5 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search monuments, beaches, temples, or urban hubs across India..."
                className="w-full pl-14 pr-6 py-4 bg-white border border-slate-200 rounded-2xl text-slate-800 placeholder-slate-400 text-sm font-medium shadow-sm focus:outline-none focus:border-[#FF9933]/50 focus:ring-1 focus:ring-[#FF9933]/20 transition-all"
              />
            </div>
          </motion.div>
        </div>
      </section>

      {/* ═══ PLACES SECTION ═══ */}
      <section className="max-w-[1600px] mx-auto px-6 md:px-12 lg:px-16 pb-20">
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.5 }}
          className="flex items-end justify-between mb-8"
        >
          <div>
            <h2 className="text-2xl md:text-3xl font-bold text-slate-900">
              Live around India
            </h2>
            <p className="text-sm text-slate-500 mt-1 font-medium">
              {filtered.length} places matching your search
            </p>
          </div>
        </motion.div>

        {loading ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {[...Array(6)].map((_, i) => (
              <div key={i} className="animate-pulse rounded-2xl bg-white border border-slate-200 h-72" />
            ))}
          </div>
        ) : filtered.length === 0 ? (
          <div className="text-center py-20">
            <p className="text-slate-500 text-lg font-medium">No places found</p>
            <p className="text-slate-400 text-sm mt-1">Try a different search term</p>
          </div>
        ) : (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.6 }}
            className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6"
          >
            {filtered.map((p, i) => (
              <motion.div
                key={p.id}
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.1 * i, duration: 0.3 }}
              >
                <PlaceCard place={p} />
              </motion.div>
            ))}
          </motion.div>
        )}
      </section>
    </div>
  );
}