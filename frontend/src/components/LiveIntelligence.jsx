import { useEffect, useState, useRef } from "react";
import {
  getWaitTime,
  getTimeSeries
} from "../services/api";

import CrowdChart from "./CrowdChart";
import { getPrediction } from "../services/api";
import { motion, AnimatePresence } from "framer-motion";
import {
  Users,
  Clock,
  Activity,
  TrendingUp,
  TrendingDown,
  Minus,
  Sparkles,
  AlertTriangle,
  Zap,
  ArrowDown,
  ArrowUp,
  Radio,
  BarChart3,
  Lightbulb,
  Timer,
  Signal,
  ShieldCheck,
  ShieldAlert,
  ShieldX,
} from "lucide-react";

// ─── Pulse Dot ────────────────────────────────────────────────
const PulseDot = ({ color = "#22c55e", size = 8 }) => (
  <span className="relative inline-flex items-center justify-center" style={{ width: size * 2, height: size * 2 }}>
    <span
      className="absolute animate-ping rounded-full opacity-50"
      style={{ width: size * 1.8, height: size * 1.8, backgroundColor: color }}
    />
    <span
      className="relative rounded-full"
      style={{ width: size, height: size, backgroundColor: color }}
    />
  </span>
);

// ─── Skeleton Block ───────────────────────────────────────────
const Skeleton = ({ className = "" }) => (
  <div className={`animate-pulse rounded-xl bg-slate-100 ${className}`} />
);

// ─── Skeleton Card ────────────────────────────────────────────
const SkeletonCard = () => (
  <div className="bg-white border border-slate-200 rounded-2xl p-6 space-y-4">
    <Skeleton className="h-4 w-28" />
    <Skeleton className="h-10 w-32" />
    <Skeleton className="h-3 w-40" />
  </div>
);

// ─── Alert Config ─────────────────────────────────────────────
const alertConfig = {
  LOW: {
    bg: "bg-emerald-50",
    border: "border-emerald-200",
    text: "text-emerald-600",
    icon: ShieldCheck,
  },
  MODERATE: {
    bg: "bg-amber-50",
    border: "border-amber-200",
    text: "text-amber-600",
    icon: ShieldAlert,
  },
  HIGH: {
    bg: "bg-orange-50",
    border: "border-orange-200",
    text: "text-orange-600",
    icon: AlertTriangle,
  },
  EXTREME: {
    bg: "bg-red-50",
    border: "border-red-200",
    text: "text-red-600",
    icon: ShieldX,
  },
};

// ─── Queue Status Config ──────────────────────────────────────
const queueStatusConfig = {
  ACTIVE: {
    color: "#22c55e",
    bg: "bg-emerald-50",
    border: "border-emerald-200",
    text: "text-emerald-600",
    label: "Active",
  },
  PAUSED: {
    color: "#eab308",
    bg: "bg-yellow-50",
    border: "border-yellow-200",
    text: "text-yellow-600",
    label: "Paused",
  },
  CLOSED: {
    color: "#ef4444",
    bg: "bg-red-50",
    border: "border-red-200",
    text: "text-red-600",
    label: "Closed",
  },
};

// ─── Trend Icon ───────────────────────────────────────────────
const TrendIcon = ({ trend }) => {
  if (trend === "RISING") return <TrendingUp className="w-4 h-4 text-red-500" />;
  if (trend === "FALLING") return <TrendingDown className="w-4 h-4 text-emerald-500" />;
  return <Minus className="w-4 h-4 text-slate-400" />;
};

// ─── Card Wrapper ─────────────────────────────────────────────
const IntelCard = ({ children, className = "", delay = 0 }) => (
  <motion.div
    initial={{ opacity: 0, y: 20 }}
    animate={{ opacity: 1, y: 0 }}
    transition={{ delay, duration: 0.4, ease: "easeOut" }}
    className={`group relative bg-white border border-slate-200 rounded-2xl p-6 hover:border-slate-300 hover:shadow-md transition-all duration-300 ${className}`}
  >
    {children}
  </motion.div>
);

// ═══════════════════════════════════════════════════════════════
// MAIN COMPONENT
// ═══════════════════════════════════════════════════════════════
export default function LiveIntelligence({ placeId }) {

  const [loading, setLoading] = useState(true);
  const [waitData, setWaitData] = useState(null);
  const [prediction, setPrediction] = useState(null);
  const [timeSeries, setTimeSeries] = useState([]);
  const [lastUpdated, setLastUpdated] = useState(null);
  const refreshCountRef = useRef(0);

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
      if (refreshCountRef.current === 0) setLoading(true);

      const [wait, predData, tsData] = await Promise.all([
        getWaitTime(placeId),
        getPrediction(placeId),
        getTimeSeries(placeId)
      ]);

      setWaitData(wait);
      setPrediction(predData);
      setTimeSeries(tsData || []);
      setLastUpdated(new Date());
      refreshCountRef.current += 1;
    } catch (err) {
      console.error("Error loading Live Intelligence:", err);
    } finally {
      setLoading(false);
    }
  };

  // ─── Queue status styling ────────────────────────────────
  const qStatus = queueStatusConfig[waitData?.queueStatus] || queueStatusConfig.CLOSED;
  const alertCfg = alertConfig[prediction?.alert] || alertConfig.LOW;
  const AlertIcon = alertCfg.icon;

  // ─── Loading Skeleton ────────────────────────────────────
  if (loading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-12 w-full rounded-2xl" />
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <SkeletonCard />
          <SkeletonCard />
          <SkeletonCard />
        </div>
        <div className="bg-white border border-slate-200 rounded-2xl p-6 space-y-4">
          <Skeleton className="h-5 w-40" />
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <Skeleton className="h-20" />
            <Skeleton className="h-20" />
            <Skeleton className="h-20" />
            <Skeleton className="h-20" />
          </div>
          <Skeleton className="h-12 w-full" />
        </div>
        <div className="bg-white border border-slate-200 rounded-2xl p-6 space-y-4">
          <Skeleton className="h-5 w-36" />
          <Skeleton className="h-[300px] w-full" />
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">

      {/* ═══ LIVE STATUS BAR ═══ */}
      <motion.div
        initial={{ opacity: 0, y: -10 }}
        animate={{ opacity: 1, y: 0 }}
        className={`flex flex-wrap items-center justify-between gap-4 px-5 py-3.5 rounded-2xl border ${qStatus.bg} ${qStatus.border}`}
      >
        <div className="flex items-center gap-3">
          <PulseDot color={qStatus.color} size={7} />
          <span className={`text-sm font-bold uppercase tracking-wider ${qStatus.text}`}>
            Queue {qStatus.label}
          </span>
          <span className="hidden sm:inline text-slate-300 text-xs">|</span>
          <span className="hidden sm:flex items-center gap-1.5 text-xs text-slate-500">
            <Radio className="w-3 h-3" />
            Live feed active
          </span>
        </div>

        <div className="flex items-center gap-3">
          {lastUpdated && (
            <span className="text-xs text-slate-400 font-medium">
              Updated {lastUpdated.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" })}
            </span>
          )}
          <motion.div
            animate={{ rotate: 360 }}
            transition={{ duration: 2, repeat: Infinity, ease: "linear" }}
            className="w-4 h-4"
          >
            <Signal className="w-4 h-4 text-slate-400" />
          </motion.div>
        </div>
      </motion.div>

      {/* ═══ METRIC CARDS ═══ */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">

        {/* ── Live Crowd Estimate ── */}
        <IntelCard delay={0.05}>
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-2.5">
              <div className="p-2.5 bg-[#FF9933]/10 border border-[#FF9933]/20 rounded-xl">
                <Users className="w-5 h-5 text-[#FF9933]" />
              </div>
              <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                Live Crowd
              </span>
            </div>
            <PulseDot color="#FF9933" size={5} />
          </div>

          <AnimatePresence mode="wait">
            <motion.h2
              key={waitData?.peopleAhead ?? "empty"}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              className="text-4xl lg:text-5xl font-bold text-slate-900 tracking-tight"
            >
              {waitData ? waitData.peopleAhead : "--"}
            </motion.h2>
          </AnimatePresence>

          <p className="text-sm text-slate-500 mt-2 font-medium">
            people currently ahead in queue
          </p>
        </IntelCard>

        {/* ── Estimated Wait ── */}
        <IntelCard delay={0.1}>
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-2.5">
              <div className="p-2.5 bg-blue-50 border border-blue-200 rounded-xl">
                <Clock className="w-5 h-5 text-blue-500" />
              </div>
              <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                Estimated Wait
              </span>
            </div>
            {prediction?.trend && <TrendIcon trend={prediction.trend} />}
          </div>

          <AnimatePresence mode="wait">
            <motion.h2
              key={waitData?.waitMinutes ?? "empty"}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              className="text-4xl lg:text-5xl font-bold text-slate-900 tracking-tight"
            >
              {waitData ? (
                <>
                  {waitData.waitMinutes}
                  <span className="text-xl text-slate-400 font-semibold ml-2">min</span>
                </>
              ) : (
                "--"
              )}
            </motion.h2>
          </AnimatePresence>

          <p className="text-sm text-slate-500 mt-2 font-medium">
            {waitData
              ? `${waitData.peopleAhead} ahead · ~${Math.round(waitData.waitMinutes / Math.max(waitData.peopleAhead, 1))} min/person`
              : "calculating..."}
          </p>
        </IntelCard>

        {/* ── Queue Status ── */}
        <IntelCard delay={0.15}>
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-2.5">
              <div className="p-2.5 bg-emerald-50 border border-emerald-200 rounded-xl">
                <Activity className="w-5 h-5 text-emerald-500" />
              </div>
              <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                Queue Status
              </span>
            </div>
          </div>

          <div className="flex items-center gap-3 mb-3">
            <PulseDot color={qStatus.color} size={6} />
            <h2 className={`text-3xl lg:text-4xl font-bold tracking-tight ${qStatus.text}`}>
              {waitData?.queueStatus || "UNKNOWN"}
            </h2>
          </div>

          <p className="text-sm text-slate-500 font-medium">
            live operational status
          </p>

          {waitData && (
            <div className="mt-4 pt-4 border-t border-slate-100 flex items-center gap-4">
              <div className="flex items-center gap-1.5">
                <Timer className="w-3.5 h-3.5 text-slate-400" />
                <span className="text-xs text-slate-500">
                  Avg {Math.round(waitData.waitMinutes / Math.max(waitData.peopleAhead, 1))} min/person
                </span>
              </div>
            </div>
          )}
        </IntelCard>
      </div>

      {/* ═══ AI INTELLIGENCE PANEL ═══ */}
      {prediction && (
        <IntelCard delay={0.2} className="relative overflow-hidden">
          <div className="absolute -top-24 -right-24 w-48 h-48 bg-[#FF9933]/5 rounded-full blur-3xl pointer-events-none" />
          <div className="absolute -bottom-16 -left-16 w-32 h-32 bg-blue-500/5 rounded-full blur-3xl pointer-events-none" />

          <div className="relative">
            {/* Header */}
            <div className="flex flex-wrap items-center justify-between gap-3 mb-6">
              <div className="flex items-center gap-3">
                <div className="p-2.5 bg-[#FF9933]/10 border border-[#FF9933]/20 rounded-xl">
                  <Sparkles className="w-5 h-5 text-[#FF9933]" />
                </div>
                <div>
                  <h3 className="text-lg font-bold text-slate-900">Smart Intelligence</h3>
                  <p className="text-xs text-slate-500 font-medium">AI-powered crowd predictions</p>
                </div>
              </div>

              <div className={`flex items-center gap-2 px-4 py-2 rounded-xl ${alertCfg.bg} border ${alertCfg.border}`}>
                <AlertIcon className={`w-4 h-4 ${alertCfg.text}`} />
                <span className={`text-sm font-bold ${alertCfg.text}`}>
                  {prediction.alert} Alert
                </span>
              </div>
            </div>

            {/* Metrics Grid */}
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
              <div className="bg-slate-50 border border-slate-200 rounded-xl p-4">
                <div className="flex items-center gap-2 mb-2">
                  <Clock className="w-3.5 h-3.5 text-[#FF9933]" />
                  <span className="text-xs text-slate-500 font-semibold uppercase tracking-wide">Now</span>
                </div>
                <p className="text-2xl font-bold text-slate-900">
                  {prediction.currentWait}
                  <span className="text-sm text-slate-400 font-medium ml-1">min</span>
                </p>
              </div>

              <div className="bg-emerald-50/50 border border-emerald-200 rounded-xl p-4">
                <div className="flex items-center gap-2 mb-2">
                  <ArrowDown className="w-3.5 h-3.5 text-emerald-500" />
                  <span className="text-xs text-slate-500 font-semibold uppercase tracking-wide">Best</span>
                </div>
                <p className="text-2xl font-bold text-emerald-600">
                  {formatTime(prediction?.bestTime) || "--"}
                </p>
                <p className="text-xs text-slate-500 mt-1">~{prediction.bestWait} min wait</p>
              </div>

              <div className="bg-red-50/50 border border-red-200 rounded-xl p-4">
                <div className="flex items-center gap-2 mb-2">
                  <ArrowUp className="w-3.5 h-3.5 text-red-500" />
                  <span className="text-xs text-slate-500 font-semibold uppercase tracking-wide">Peak</span>
                </div>
                <p className="text-2xl font-bold text-red-500">
                  {formatTime(prediction.peakTime)}
                </p>
                <p className="text-xs text-slate-500 mt-1">~{prediction.peakWait} min wait</p>
              </div>

              <div className="bg-slate-50 border border-slate-200 rounded-xl p-4">
                <div className="flex items-center gap-2 mb-2">
                  <BarChart3 className="w-3.5 h-3.5 text-blue-500" />
                  <span className="text-xs text-slate-500 font-semibold uppercase tracking-wide">Trend</span>
                </div>
                <div className="flex items-center gap-2">
                  <TrendIcon trend={prediction.trend} />
                  <p className={`text-2xl font-bold ${
                    prediction.trend === "RISING"
                      ? "text-red-500"
                      : prediction.trend === "FALLING"
                      ? "text-emerald-500"
                      : "text-slate-500"
                  }`}>
                    {prediction.trend}
                  </p>
                </div>
              </div>
            </div>

            {/* AI Recommendation */}
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.3 }}
              className="relative overflow-hidden bg-gradient-to-r from-[#FF9933]/5 via-[#FF9933]/10 to-[#FF9933]/5 border border-[#FF9933]/20 rounded-xl p-5"
            >
              <div className="absolute top-0 left-0 w-1 h-full bg-[#FF9933] rounded-full" />
              <div className="flex items-start gap-3 pl-3">
                <div className="flex-shrink-0 mt-0.5">
                  <Lightbulb className="w-5 h-5 text-[#FF9933]" />
                </div>
                <div>
                  <p className="text-xs font-bold text-[#FF9933] uppercase tracking-wider mb-1">
                    AI Recommendation
                  </p>
                  <p className="text-sm text-slate-600 leading-relaxed font-medium">
                    {prediction.recommendation}
                  </p>
                </div>
              </div>
            </motion.div>
          </div>
        </IntelCard>
      )}

      {/* ═══ HOURLY TREND CHART ═══ */}
      <IntelCard delay={0.25}>
        <div className="flex flex-wrap items-center justify-between gap-3 mb-6">
          <div className="flex items-center gap-3">
            <div className="p-2.5 bg-blue-50 border border-blue-200 rounded-xl">
              <BarChart3 className="w-5 h-5 text-blue-500" />
            </div>
            <div>
              <h3 className="text-lg font-bold text-slate-900">Today's Hourly Trend</h3>
              <p className="text-xs text-slate-500 font-medium">Wait time distribution across hours</p>
            </div>
          </div>

          <div className="flex items-center gap-2 px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-lg">
            <PulseDot color="#FF9933" size={4} />
            <span className="text-xs text-slate-500 font-medium">Live data</span>
          </div>
        </div>

        <CrowdChart
          data={prediction?.timeline || []}
          timeSeriesData={timeSeries}
        />
      </IntelCard>
    </div>
  );
}