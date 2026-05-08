import { useState, useEffect, useCallback, useRef } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { 
  Users, 
  Clock, 
  AlertCircle, 
  CheckCircle, 
  Zap,
  TrendingDown,
  Activity,
  Radio,
  Shield,
  Timer,
  X,
  ChevronDown,
  Sparkles,
  Signal
} from "lucide-react";
import { joinQueue, getQueueStatus, leaveQueue } from "../services/api";
import { connectSocket, disconnectSocket } from "../services/socket";

// ─── Wait Time Formatter (min → h + m) ───────────────────────
const formatWaitTime = (minutes) => {
  if (minutes == null || minutes <= 0) return "0 min";
  if (minutes < 60) return `${minutes} min`;
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return m > 0 ? `${h}h ${m}m` : `${h}h`;
};

// ── Reusable Pulse Dot ──
const PulseDot = ({ color = "#22c55e", size = 8 }) => (
  <span className="relative inline-flex items-center justify-center" style={{ width: size * 2, height: size * 2 }}>
    <span className="absolute animate-ping rounded-full opacity-50" style={{ width: size * 1.8, height: size * 1.8, backgroundColor: color }} />
    <span className="relative rounded-full" style={{ width: size, height: size, backgroundColor: color }} />
  </span>
);

// ── Skeleton ──
const Skeleton = ({ className = "" }) => (
  <div className={`animate-pulse rounded-xl bg-slate-100 ${className}`} />
);

export default function VirtualQueue({ placeId }) {
  const [userId] = useState(() => {
    let id = localStorage.getItem("userId");
    if (!id) {
      id = "user_" + Math.floor(Math.random() * 1000000);
      localStorage.setItem("userId", id);
    }
    return id;
  });

  const [joined, setJoined] = useState(false);
  const [status, setStatus] = useState(null);
  const [loading, setLoading] = useState(false);
  const [groupSize, setGroupSize] = useState(1);
  const [checkingQueue, setCheckingQueue] = useState(true);
  const [previousPeopleAhead, setPreviousPeopleAhead] = useState(0);
  const [isMoving, setIsMoving] = useState(false);
  const [isJoiningNow, setIsJoiningNow] = useState(false);
  const [showGroupPicker, setShowGroupPicker] = useState(false);

  useEffect(() => {
    const checkExistingQueue = async () => {
      try {
        setCheckingQueue(true);
        const res = await getQueueStatus(placeId, userId);
        if (res && res.isUserInQueue === true) {
          setJoined(true);
          setStatus(res);
          setPreviousPeopleAhead(res.peopleAhead || 0);
        } else {
          setJoined(false);
          setStatus(null);
        }
      } catch (err) {
        console.error("Error checking queue:", err);
        setJoined(false);
      } finally {
        setCheckingQueue(false);
      }
    };
    checkExistingQueue();
  }, [placeId, userId]);

  const fetchStatus = useCallback(async () => {
    if (isJoiningNow) {
      console.debug("Skipping status fetch - join in progress");
      return;
    }
    try {
      const res = await getQueueStatus(placeId, userId);
      if (res && typeof res === "object") {
        if (res.isUserInQueue === true) {
          setPreviousPeopleAhead((prev) => {
            if (res.peopleAhead < prev) {
              setIsMoving(true);
              setTimeout(() => setIsMoving(false), 1000);
            }
            return res.peopleAhead || 0;
          });
          setJoined(true);
          setStatus(res);
        } else if (res.isUserInQueue === false) {
          setJoined(false);
          setStatus(null);
        } else {
          console.warn("Unexpected response format:", res);
        }
      } else {
        console.error("Invalid response type:", typeof res);
      }
    } catch (err) {
      console.error("Status fetch failed:", err);
    }
  }, [placeId, userId, isJoiningNow]);

  const handleJoin = async () => {
    setLoading(true);
    setIsJoiningNow(true);
    try {
      const res = await joinQueue(placeId, userId, groupSize);
      if (res.success !== false) {
        setJoined(true);
        setTimeout(async () => {
          setIsJoiningNow(false);
          await fetchStatus();
        }, 1500);
      } else {
        console.error("Join failed:", res.message);
        setIsJoiningNow(false);
      }
    } catch (err) {
      console.error("Join request failed:", err);
      setIsJoiningNow(false);
    } finally {
      setLoading(false);
    }
  };

  const handleLeave = async () => {
    setLoading(true);
    try {
      const res = await leaveQueue(placeId, userId);
      if (res.success === true) {
        console.log("✅ Successfully left queue");
        setJoined(false);
        setStatus(null);
        setPreviousPeopleAhead(0);
      } else {
        console.error("Leave failed:", res.message);
        alert("Failed to leave queue: " + res.message);
      }
    } catch (err) {
      console.error("Leave request failed:", err);
      alert("Error leaving queue. Please refresh and try again.");
    } finally {
      setLoading(false);
    }
  };

  const webSocketCallbackRef = useRef(null);
  const stateRef = useRef({ placeId, userId, isJoiningNow, fetchStatus });

  useEffect(() => {
    stateRef.current = { placeId, userId, isJoiningNow, fetchStatus };
  }, [placeId, userId, isJoiningNow, fetchStatus]);

  useEffect(() => {
    webSocketCallbackRef.current = async () => {
      console.debug("📩 WebSocket update received, fetching status...");
      const current = stateRef.current;
      if (!current.isJoiningNow && current.fetchStatus) {
        setTimeout(() => {
          if (stateRef.current.fetchStatus && !stateRef.current.isJoiningNow) {
            stateRef.current.fetchStatus();
          }
        }, 100);
      }
    };
  }, []);

  useEffect(() => {
    if (joined && placeId && userId) {
      const handleWebSocketMessage = () => {
        if (webSocketCallbackRef.current) {
          webSocketCallbackRef.current();
        }
      };
      connectSocket(placeId, handleWebSocketMessage);
      return () => { disconnectSocket(); };
    }
  }, [joined, placeId, userId]);

  useEffect(() => {
    if (!placeId || !userId) return;
    const interval = setInterval(() => {
      fetch(
        `http://localhost:8081/api/v1/crowdpulse/queue/heartbeat?placeId=${placeId}&userId=${userId}`,
        { method: "POST" }
      )
        .then(res => { if (!res.ok) console.warn("Heartbeat error:", res.status); })
        .catch((err) => { console.debug("Heartbeat network error:", err.message); });

      if (joined && !isJoiningNow) {
        fetchStatus();
      }
    }, 10000);
    return () => clearInterval(interval);
  }, [placeId, userId, joined, isJoiningNow, fetchStatus]);

  const getQueueStatusDisplay = () => {
    if (!status) return { label: "Connecting...", color: "#6b7280", icon: Signal };
    switch (status.queueState) {
      case "ACTIVE":
        if (status.peopleAhead === 0) return { label: "NEXT UP", color: "#22c55e", icon: Zap };
        if (status.peopleAhead < 10) return { label: "APPROACHING", color: "#3b82f6", icon: TrendingDown };
        if (status.peopleAhead > 50) return { label: "HEAVY FLOW", color: "#eab308", icon: Activity };
        return { label: "LIVE — Queue Active", color: "#22c55e", icon: Activity };
      case "SHADOW":
        return { label: "Standby Mode", color: "#f97316", icon: Shield };
      default:
        return { label: "Queue Paused", color: "#ef4444", icon: AlertCircle };
    }
  };

  const getMovementIndicator = () => {
    if (!status) return { text: "Initializing...", color: "text-slate-400" };
    if (isMoving) return { text: "Queue moving — position updated", color: "text-emerald-500" };
    if (status.peopleAhead === 0) return { text: "You're next in line!", color: "text-emerald-500" };
    if (status.estimatedWaitMinutes <= 5) return { text: "Queue accelerating", color: "text-blue-500" };
    if (status.estimatedWaitMinutes > 20) return { text: "Heavy congestion detected", color: "text-amber-500" };
    return { text: "Queue moving normally", color: "text-slate-500" };
  };

  const queueDisplay = getQueueStatusDisplay();
  const QueueIcon = queueDisplay.icon;
  const movement = getMovementIndicator();
  const progressPercent = status ? Math.max(0, (100 - (status.peopleAhead / (status.peopleAhead + 1)) * 100)) : 0;

  if (checkingQueue) {
    return (
      <motion.div className="flex justify-center items-center py-20" initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
        <div className="flex flex-col items-center gap-5">
          <div className="relative w-16 h-16">
            <motion.div animate={{ rotate: 360 }} transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
              className="absolute inset-0 border-4 border-slate-200 border-t-[#FF9933] rounded-full" />
            <motion.div animate={{ rotate: -360 }} transition={{ duration: 1.5, repeat: Infinity, ease: "linear" }}
              className="absolute inset-2 border-4 border-slate-200 border-t-[#FF9933]/50 rounded-full" />
          </div>
          <div className="text-center space-y-1">
            <p className="text-slate-700 font-semibold">Checking queue status</p>
            <p className="text-xs text-slate-400">Syncing with live system...</p>
          </div>
        </div>
      </motion.div>
    );
  }

  return (
    <div className="w-full">
      <AnimatePresence mode="wait">
        {!joined ? (
          <motion.div key="join-form" initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -20 }}>
            {!showGroupPicker ? (
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                className="bg-white border border-slate-200 rounded-2xl p-10 md:p-14 text-center"
              >
                <motion.div
                  initial={{ scale: 0.8, opacity: 0 }}
                  animate={{ scale: 1, opacity: 1 }}
                  transition={{ delay: 0.1, type: "spring" }}
                  className="w-16 h-16 mx-auto mb-6 bg-[#FF9933]/10 border border-[#FF9933]/20 rounded-2xl flex items-center justify-center"
                >
                  <Users className="w-8 h-8 text-[#FF9933]" />
                </motion.div>

                <motion.h2
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.15 }}
                  className="text-2xl md:text-3xl font-bold text-slate-900 mb-3"
                >
                  Join the Virtual Queue
                </motion.h2>

                <motion.p
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  transition={{ delay: 0.2 }}
                  className="text-slate-500 mb-8 max-w-md mx-auto leading-relaxed"
                >
                  Skip the physical line. Get a live sequence number and track your darshan time in real-time.
                </motion.p>

                <motion.button
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.25 }}
                  onClick={() => setShowGroupPicker(true)}
                  whileHover={{ scale: 1.02, boxShadow: "0 8px 30px rgba(255,153,51,0.2)" }}
                  whileTap={{ scale: 0.98 }}
                  className="inline-flex items-center gap-2.5 px-8 py-4 bg-[#FF9933] hover:bg-[#FF9933]/90 text-white rounded-xl font-bold text-base transition-all duration-300"
                >
                  <Sparkles className="w-5 h-5" />
                  Start Your Queue Journey
                </motion.button>
              </motion.div>
            ) : (
              <motion.div
                initial={{ opacity: 0, scale: 0.95 }}
                animate={{ opacity: 1, scale: 1 }}
                className="bg-white border border-slate-200 rounded-2xl p-8 md:p-10"
              >
                <div className="text-center mb-8">
                  <div className="w-14 h-14 mx-auto mb-5 bg-[#FF9933]/10 border border-[#FF9933]/20 rounded-2xl flex items-center justify-center">
                    <Users className="w-7 h-7 text-[#FF9933]" />
                  </div>
                  <h3 className="text-xl md:text-2xl font-bold text-slate-900 mb-2">How many in your group?</h3>
                  <p className="text-sm text-slate-500">Including yourself (max 10)</p>
                </div>

                <div className="flex flex-wrap justify-center gap-3 mb-8 max-w-sm mx-auto">
                  {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map(n => (
                    <motion.button
                      key={n}
                      whileHover={{ scale: 1.1 }}
                      whileTap={{ scale: 0.95 }}
                      onClick={() => setGroupSize(n)}
                      className={`w-12 h-12 rounded-xl font-bold text-base transition-all duration-200 ${
                        groupSize === n
                          ? "bg-[#FF9933] text-white shadow-lg shadow-[#FF9933]/20"
                          : "bg-slate-50 border border-slate-200 text-slate-600 hover:border-slate-300"
                      }`}
                    >
                      {n}
                    </motion.button>
                  ))}
                </div>

                <div className="flex items-center justify-center gap-4">
                  <motion.button
                    whileHover={{ scale: 1.02 }}
                    whileTap={{ scale: 0.98 }}
                    onClick={() => { setShowGroupPicker(false); setGroupSize(1); }}
                    className="px-6 py-3 text-slate-500 hover:text-slate-800 font-semibold text-sm transition-colors"
                  >
                    Cancel
                  </motion.button>

                  <motion.button
                    onClick={handleJoin}
                    disabled={loading}
                    whileHover={{ scale: 1.02, boxShadow: "0 8px 30px rgba(255,153,51,0.2)" }}
                    whileTap={{ scale: 0.98 }}
                    className="px-8 py-3 bg-[#FF9933] hover:bg-[#FF9933]/90 disabled:bg-slate-200 disabled:text-slate-400 text-white rounded-xl font-bold text-sm transition-all duration-300 flex items-center gap-2"
                  >
                    {loading ? (
                      <>
                        <motion.div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full" animate={{ rotate: 360 }} transition={{ duration: 0.8, repeat: Infinity, ease: "linear" }} />
                        Joining...
                      </>
                    ) : (
                      <>Confirm & Get Badge</>
                    )}
                  </motion.button>
                </div>
              </motion.div>
            )}
          </motion.div>
        ) : (
          <motion.div key="queue-dashboard" initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -20 }} className="space-y-5">

            {/* Status Banner */}
            <motion.div
              initial={{ opacity: 0, y: -10 }}
              animate={{ opacity: 1, y: 0 }}
              className="flex flex-wrap items-center justify-between gap-3 px-5 py-3.5 rounded-2xl border"
              style={{
                backgroundColor: `${queueDisplay.color}08`,
                borderColor: `${queueDisplay.color}30`,
              }}
            >
              <div className="flex items-center gap-3">
                <PulseDot color={queueDisplay.color} size={6} />
                <span className="text-sm font-bold uppercase tracking-wider" style={{ color: queueDisplay.color }}>
                  {queueDisplay.label}
                </span>
              </div>
              <div className="flex items-center gap-2">
                <Radio className="w-3.5 h-3.5 text-slate-400" />
                <span className="text-xs text-slate-400 font-medium">WebSocket connected</span>
              </div>
            </motion.div>

            {/* Sequence Number Card */}
            <motion.div
              initial={{ scale: 0.95, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              transition={{ delay: 0.1, type: "spring" }}
              className="relative overflow-hidden bg-white border border-[#FF9933]/30 rounded-2xl p-8"
            >
              <div className="absolute -top-20 -right-20 w-40 h-40 bg-[#FF9933]/5 rounded-full blur-2xl pointer-events-none" />

              <div className="relative flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
                <div>
                  <div className="flex items-center gap-2 mb-2">
                    <CheckCircle className="w-4 h-4 text-emerald-500" />
                    <span className="text-sm font-semibold text-emerald-600">You're in the queue</span>
                  </div>
                  <p className="text-xs text-slate-400 uppercase tracking-wider font-semibold mb-1">Sequence number</p>
                  <AnimatePresence mode="wait">
                    <motion.div
                      key={status?.position}
                      initial={{ y: -10, opacity: 0 }}
                      animate={{ y: 0, opacity: 1 }}
                      className="text-5xl md:text-6xl font-black text-slate-900 tracking-tight"
                    >
                      #{status?.position ?? "—"}
                    </motion.div>
                  </AnimatePresence>
                  <p className="text-sm text-slate-500 mt-2 font-medium">
                    Group of {status?.groupSize || groupSize}
                  </p>
                </div>

                <div className="px-4 py-2 bg-slate-50 border border-slate-200 rounded-xl">
                  <p className="text-xs text-slate-400 font-medium">Token is static</p>
                  <p className="text-xs text-[#FF9933] font-bold">Position fixed</p>
                </div>
              </div>
            </motion.div>

            {/* Progress Bar */}
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.2 }}
              className="bg-white border border-slate-200 rounded-2xl p-6"
            >
              <div className="flex justify-between items-center mb-3">
                <p className="text-sm font-semibold text-slate-700">Queue progress</p>
                <motion.span
                  className="text-sm font-bold text-[#FF9933]"
                  key={progressPercent}
                  initial={{ scale: 1.3 }}
                  animate={{ scale: 1 }}
                >
                  {Math.round(progressPercent)}%
                </motion.span>
              </div>
              <div className="w-full bg-slate-100 rounded-full h-2.5 overflow-hidden">
                <motion.div
                  className="h-full rounded-full bg-gradient-to-r from-[#FF9933] to-[#FF9933]/70"
                  initial={{ width: 0 }}
                  animate={{ width: `${Math.max(5, progressPercent)}%` }}
                  transition={{ duration: 0.8, ease: "easeOut" }}
                />
              </div>
            </motion.div>

            {/* People Ahead & ETA */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <motion.div
                initial={{ opacity: 0, x: -20 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: 0.25 }}
                className="bg-white border border-slate-200 rounded-2xl p-6 hover:border-slate-300 hover:shadow-sm transition-all"
              >
                <div className="flex items-center justify-between mb-3">
                  <div className="flex items-center gap-2">
                    <div className="p-2 bg-[#FF9933]/10 border border-[#FF9933]/20 rounded-lg">
                      <Users className="w-4 h-4 text-[#FF9933]" />
                    </div>
                    <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">People ahead</span>
                  </div>
                  {isMoving && <PulseDot color="#22c55e" size={5} />}
                </div>
                <AnimatePresence mode="wait">
                  <motion.div
                    key={status?.peopleAhead}
                    initial={{ y: -8, opacity: 0 }}
                    animate={{ y: 0, opacity: 1 }}
                    className="text-4xl font-black text-slate-900 tracking-tight"
                  >
                    {status?.peopleAhead ?? "—"}
                  </motion.div>
                </AnimatePresence>
                <p className="text-xs text-slate-400 mt-2 font-medium">dynamically updating</p>
              </motion.div>

              <motion.div
                initial={{ opacity: 0, x: 20 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: 0.25 }}
                className="bg-white border border-slate-200 rounded-2xl p-6 hover:border-slate-300 hover:shadow-sm transition-all"
              >
                <div className="flex items-center justify-between mb-3">
                  <div className="flex items-center gap-2">
                    <div className="p-2 bg-blue-50 border border-blue-200 rounded-lg">
                      <Clock className="w-4 h-4 text-blue-500" />
                    </div>
                    <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Est. action time</span>
                  </div>
                </div>
                <AnimatePresence mode="wait">
                  <motion.div
                    key={status?.estimatedDarshanTime}
                    initial={{ y: -8, opacity: 0 }}
                    animate={{ y: 0, opacity: 1 }}
                    className="text-4xl font-black text-slate-900 tracking-tight"
                  >
                    {status?.estimatedDarshanTime?.substring(0, 5) ?? "—"}
                  </motion.div>
                </AnimatePresence>
                <p className="text-xs text-slate-400 mt-2 font-medium">
                  {status?.estimatedWaitMinutes ? `~${formatWaitTime(status.estimatedWaitMinutes)} remaining` : "calculating..."}
                </p>
              </motion.div>
            </div>

            {/* Movement Indicator */}
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.3 }}
              className="flex items-center gap-3 px-5 py-3.5 bg-white border border-slate-200 rounded-2xl"
            >
              <motion.div animate={{ scale: [1, 1.2, 1] }} transition={{ duration: 2, repeat: Infinity }}>
                <Activity className="w-4 h-4 text-[#FF9933]" />
              </motion.div>
              <span className={`text-sm font-semibold ${movement.color}`}>{movement.text}</span>
            </motion.div>

            {/* Leave Queue */}
            <motion.button
              onClick={handleLeave}
              disabled={loading}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.35 }}
              whileHover={{ scale: 1.01 }}
              whileTap={{ scale: 0.99 }}
              className="w-full flex items-center justify-center gap-2 px-6 py-4 bg-white border border-red-200 hover:border-red-300 hover:bg-red-50 rounded-2xl text-red-500 hover:text-red-600 font-semibold text-sm transition-all duration-300 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <X className="w-4 h-4" />
              {loading ? "Leaving..." : "Leave Queue"}
            </motion.button>

            {/* Real-time Footer */}
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.4 }}
              className="flex items-center justify-center gap-2 text-xs text-slate-400"
            >
              <PulseDot color="#22c55e" size={4} />
              <span>Real-time tracking active · WebSocket connected</span>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}