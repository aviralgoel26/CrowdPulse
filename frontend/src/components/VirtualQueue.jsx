import { useState, useEffect, useCallback, useRef } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { 
  Users, 
  Clock, 
  AlertCircle, 
  CheckCircle, 
  Zap,
  TrendingDown,
  Activity
} from "lucide-react";
import { joinQueue, getQueueStatus, leaveQueue } from "../services/api";
import { connectSocket, disconnectSocket } from "../services/socket";

export default function VirtualQueue({ placeId }) {
  // User ID persistence
  const [userId] = useState(() => {
    let id = localStorage.getItem("userId");
    if (!id) {
      id = "user_" + Math.floor(Math.random() * 1000000);
      localStorage.setItem("userId", id);
    }
    return id;
  });

  // State management
  const [joined, setJoined] = useState(false);
  const [status, setStatus] = useState(null);
  const [loading, setLoading] = useState(false);
  const [groupSize, setGroupSize] = useState(1);
  const [checkingQueue, setCheckingQueue] = useState(true);
  const [previousPeopleAhead, setPreviousPeopleAhead] = useState(0);
  const [isMoving, setIsMoving] = useState(false);
  const [isJoiningNow, setIsJoiningNow] = useState(false);  // 🆕 Prevent premature fetchStatus during join

  // Check existing queue on mount
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

  // Fetch status
  const fetchStatus = useCallback(async () => {
    // 🆕 Skip fetchStatus during join process to prevent race conditions
    if (isJoiningNow) {
      console.debug("Skipping status fetch - join in progress");
      return;
    }

    try {
      const res = await getQueueStatus(placeId, userId);
      
      // Validate response properly
      if (res && typeof res === "object") {
        // Explicitly check for isUserInQueue flag (new format)
        if (res.isUserInQueue === true) {
          // Detect movement
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
          // Explicit "not in queue" response
          setJoined(false);
          setStatus(null);
        } else {
          // Unknown response format - don't change joined state
          console.warn("Unexpected response format:", res);
        }
      } else {
        console.error("Invalid response type:", typeof res);
      }
    } catch (err) {
      console.error("Status fetch failed:", err);
      // Don't change joined state on error - keep showing queue UI
    }
  }, [placeId, userId, isJoiningNow]);

  // Join queue
  const handleJoin = async () => {
    setLoading(true);
    setIsJoiningNow(true);  // 🆕 Mark join process in progress
    
    try {
      const res = await joinQueue(placeId, userId, groupSize);
      
      if (res.success !== false) {
        setJoined(true);
        
        // 🆕 Increased delay: Allow backend to:
        // 1. Persist queue entry to Redis
        // 2. Create heartbeat key
        // 3. Ensure scheduler won't mark as SHADOW
        setTimeout(async () => {
          setIsJoiningNow(false);  // Mark join as complete
          await fetchStatus();
        }, 1500);  // Increased from 500ms to 1500ms
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

  // ✅ LEAVE QUEUE (NEW - CRITICAL FIX)
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

  // Stable callback ref for WebSocket to avoid re-triggering useEffect
  const webSocketCallbackRef = useRef(null);
  const stateRef = useRef({ placeId, userId, isJoiningNow, fetchStatus });

  // Update the ref whenever state changes (using ref to avoid effect dependencies)
  useEffect(() => {
    stateRef.current = { placeId, userId, isJoiningNow, fetchStatus };
  }, [placeId, userId, isJoiningNow, fetchStatus]);

  // Create stable callback that reads from ref
  useEffect(() => {
    webSocketCallbackRef.current = async () => {
      console.debug("📩 WebSocket update received, fetching status...");
      const current = stateRef.current;
      
      if (!current.isJoiningNow && current.fetchStatus) {
        // Add small delay to allow backend to process
        setTimeout(() => {
          if (stateRef.current.fetchStatus && !stateRef.current.isJoiningNow) {
            stateRef.current.fetchStatus();
          }
        }, 100);
      }
    };
  }, []);

  // WebSocket integration
  useEffect(() => {
    if (joined && placeId && userId) {
      const handleWebSocketMessage = () => {
        if (webSocketCallbackRef.current) {
          webSocketCallbackRef.current();
        }
      };

      connectSocket(placeId, handleWebSocketMessage);
      return () => {
        disconnectSocket();
      };
    }
  }, [joined, placeId, userId]);

  // Heartbeat & Polling
  useEffect(() => {
    if (!placeId || !userId) return;  // Need these to send heartbeat

    // ✅ CRITICAL FIX: Continue heartbeat even during join process
    // This ensures the scheduler won't mark user as SHADOW
    const interval = setInterval(() => {
      // 1. Heartbeat (always send to keep user active)
      fetch(
        `http://localhost:8081/api/v1/crowdpulse/queue/heartbeat?placeId=${placeId}&userId=${userId}`,
        { method: "POST" }
      )
        .then(res => {
          if (!res.ok) console.warn("Heartbeat error:", res.status);
        })
        .catch((err) => {
          console.debug("Heartbeat network error:", err.message);
        });

      // 2. Poll Status ONLY if joined and not in join process
      if (joined && !isJoiningNow) {
        fetchStatus();
      }
    }, 10000);

    return () => clearInterval(interval);
  }, [placeId, userId, joined, isJoiningNow, fetchStatus]);

  // Determine queue status
  const getQueueStatusDisplay = () => {
    if (!status) return { label: "Connecting...", color: "bg-gray-400", icon: "⚪" };
    
    switch (status.queueState) {
      case "ACTIVE":
        if (status.peopleAhead === 0) return { label: "NEXT UP", color: "bg-green-500", icon: "🟢" };
        if (status.peopleAhead < 10) return { label: "APPROACHING", color: "bg-blue-500", icon: "🔵" };
        if (status.peopleAhead > 50) return { label: "HEAVY FLOW", color: "bg-yellow-500", icon: "🟡" };
        return { label: "LIVE — Queue Active", color: "bg-green-500", icon: "🟢" };
      case "SHADOW":
        return { label: "Standby Mode", color: "bg-orange-500", icon: "🟠" };
      default:
        return { label: "Queue Paused", color: "bg-red-500", icon: "🔴" };
    }
  };

  // Movement indicator
  const getMovementIndicator = () => {
    if (!status) return { text: "Initializing...", color: "text-gray-500" };
    
    if (isMoving) {
      return { text: "✨ Queue moving...", color: "text-green-500" };
    }
    
    if (status.peopleAhead === 0) {
      return { text: "🎉 You're next!", color: "text-green-500" };
    }
    
    if (status.estimatedWaitMinutes <= 5) {
      return { text: "🚀 Queue accelerating", color: "text-blue-500" };
    }
    
    if (status.estimatedWaitMinutes > 20) {
      return { text: "⏳ Heavy congestion", color: "text-orange-500" };
    }
    
    return { text: "📊 Queue moving normally", color: "text-slate-600" };
  };

  const queueDisplay = getQueueStatusDisplay();
  const movement = getMovementIndicator();
  const progressPercent = status ? Math.max(0, (100 - (status.peopleAhead / (status.peopleAhead + 1)) * 100)) : 0;

  if (checkingQueue) {
    return (
      <motion.div 
        className="flex justify-center items-center py-16"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
      >
        <div className="flex flex-col items-center gap-4">
          <motion.div 
            className="w-12 h-12 border-4 border-orange-200 border-t-orange-500 rounded-full"
            animate={{ rotate: 360 }}
            transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
          />
          <p className="text-slate-600 font-medium">Checking your queue status...</p>
        </div>
      </motion.div>
    );
  }

  return (
    <div className="w-full">
      <AnimatePresence mode="wait">
        {!joined ? (
          // JOIN QUEUE FORM
          <motion.div 
            key="join-form"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -20 }}
            className="space-y-6 bg-gradient-to-br from-slate-50 to-slate-100 p-8 rounded-2xl"
          >
            <div>
              <h2 className="text-3xl font-bold text-slate-900 mb-2">Join the Queue</h2>
              <p className="text-slate-600">Experience live darshan tracking</p>
            </div>

            <div className="space-y-4">
              <label className="block">
                <span className="text-sm font-semibold text-slate-700 mb-2 block">
                  <Users className="inline w-4 h-4 mr-2" />
                  Number of Pilgrims in Your Group
                </span>
                <motion.select
                  value={groupSize}
                  onChange={(e) => setGroupSize(parseInt(e.target.value) || 1)}
                  className="w-full px-4 py-3 border-2 border-slate-300 rounded-xl focus:border-orange-500 focus:ring-2 focus:ring-orange-200 outline-none transition-all"
                  whileHover={{ borderColor: "#FF9933" }}
                  disabled={loading}
                >
                  {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map(n => (
                    <option key={n} value={n}>{n} {n === 1 ? "pilgrim" : "pilgrims"}</option>
                  ))}
                </motion.select>
              </label>
            </div>

            <motion.button
              onClick={handleJoin}
              disabled={loading}
              whileHover={{ scale: 1.02, boxShadow: "0 20px 40px rgba(255, 153, 51, 0.3)" }}
              whileTap={{ scale: 0.98 }}
              className="w-full bg-gradient-to-r from-orange-500 to-orange-600 hover:from-orange-600 hover:to-orange-700 disabled:from-gray-400 disabled:to-gray-400 text-white px-6 py-4 rounded-xl font-bold transition-all duration-300 text-lg shadow-lg"
            >
              {loading ? (
                <motion.span 
                  className="flex items-center justify-center gap-2"
                  animate={{ opacity: [1, 0.5, 1] }}
                  transition={{ duration: 1.5, repeat: Infinity }}
                >
                  <motion.div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                  Joining Queue...
                </motion.span>
              ) : (
                "Enter Virtual Queue"
              )}
            </motion.button>

            <p className="text-center text-xs text-slate-500">
              You'll receive a static sequence number and real-time tracking
            </p>
          </motion.div>
        ) : (
          // QUEUE STATUS DASHBOARD
          <motion.div 
            key="queue-dashboard"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -20 }}
            className="space-y-6"
          >
            {/* STATUS HEADER */}
            <motion.div 
              className={`${queueDisplay.color} text-white px-6 py-4 rounded-xl font-bold flex items-center gap-3 shadow-lg`}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.2 }}
            >
              <motion.span 
                className="text-2xl"
                animate={{ scale: [1, 1.2, 1] }}
                transition={{ duration: 2, repeat: Infinity }}
              >
                {queueDisplay.icon}
              </motion.span>
              <span className="text-lg tracking-wide">{queueDisplay.label}</span>
            </motion.div>

            {/* MAIN SEQUENCE NUMBER */}
            <motion.div 
              className="bg-gradient-to-br from-orange-50 to-orange-100 border-3 border-orange-500 p-8 rounded-2xl text-center shadow-lg"
              initial={{ scale: 0.9, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              transition={{ delay: 0.3, type: "spring" }}
            >
              <p className="text-sm font-semibold text-orange-700 mb-2 tracking-widest">YOUR SEQUENCE NUMBER</p>
              <motion.div 
                className="text-6xl font-black text-orange-600 tracking-tight"
                initial={{ y: -20 }}
                animate={{ y: 0 }}
              >
                #{status?.position}
              </motion.div>
              <p className="text-orange-600 font-medium mt-2">Your token remains static</p>
            </motion.div>

            {/* PROGRESS VISUALIZATION */}
            <motion.div 
              className="bg-white border-2 border-slate-200 p-6 rounded-2xl"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.4 }}
            >
              <div className="mb-4">
                <div className="flex justify-between items-center mb-3">
                  <p className="text-sm font-semibold text-slate-700">Queue Progress</p>
                  <motion.span 
                    className="text-xs font-bold text-orange-600"
                    animate={{ opacity: [1, 0.5, 1] }}
                    transition={{ duration: 2, repeat: Infinity }}
                  >
                    {Math.round(progressPercent)}%
                  </motion.span>
                </div>
                <div className="w-full bg-slate-200 rounded-full h-3 overflow-hidden">
                  <motion.div 
                    className="bg-gradient-to-r from-orange-400 to-orange-600 h-full rounded-full"
                    initial={{ width: 0 }}
                    animate={{ width: `${Math.max(5, progressPercent)}%` }}
                    transition={{ duration: 0.8, ease: "easeOut" }}
                  />
                </div>
              </div>
            </motion.div>

            {/* PEOPLE AHEAD & ETA */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {/* People Ahead Card */}
              <motion.div 
                className="bg-gradient-to-br from-slate-50 to-slate-100 border-2 border-slate-200 p-6 rounded-xl"
                initial={{ opacity: 0, x: -20 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: 0.5 }}
              >
                <div className="flex items-center justify-between mb-3">
                  <p className="text-xs font-semibold text-slate-600 uppercase tracking-wider">People Ahead</p>
                  <Users className="w-4 h-4 text-slate-500" />
                </div>
                <motion.div 
                  className="text-4xl font-black text-slate-900"
                  key={status?.peopleAhead}
                  initial={{ y: -10, opacity: 0 }}
                  animate={{ y: 0, opacity: 1 }}
                  transition={{ type: "spring", stiffness: 100 }}
                >
                  {status?.peopleAhead ?? "—"}
                </motion.div>
                <p className="text-xs text-slate-500 mt-2">dynamically updating</p>
              </motion.div>

              {/* ETA Card */}
              <motion.div 
                className="bg-gradient-to-br from-blue-50 to-blue-100 border-2 border-blue-200 p-6 rounded-xl"
                initial={{ opacity: 0, x: 20 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: 0.5 }}
              >
                <div className="flex items-center justify-between mb-3">
                  <p className="text-xs font-semibold text-blue-700 uppercase tracking-wider">Expected Darshan</p>
                  <Clock className="w-4 h-4 text-blue-600" />
                </div>
                <motion.div 
                  className="text-3xl font-black text-blue-900"
                  key={status?.estimatedDarshanTime}
                  initial={{ y: -10, opacity: 0 }}
                  animate={{ y: 0, opacity: 1 }}
                  transition={{ type: "spring", stiffness: 100 }}
                >
                  {status?.estimatedDarshanTime?.substring(0, 5) ?? "—"}
                </motion.div>
                <p className="text-xs text-blue-600 mt-2">
                  {status?.estimatedWaitMinutes ? `${status.estimatedWaitMinutes} min` : "calculating"}
                </p>
              </motion.div>
            </div>

            {/* MOVEMENT INDICATOR */}
            <motion.div 
              className="bg-gradient-to-r from-slate-900 to-slate-800 text-white px-6 py-4 rounded-xl flex items-center gap-3"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.6 }}
            >
              <motion.div 
                animate={{ scale: [1, 1.3, 1] }}
                transition={{ duration: 2, repeat: Infinity }}
              >
                <Activity className="w-5 h-5 text-orange-400" />
              </motion.div>
              <span className={`font-semibold ${movement.color}`}>{movement.text}</span>
            </motion.div>

            {/* GROUP SIZE */}
            <motion.div 
              className="bg-slate-50 border-2 border-slate-200 p-4 rounded-xl text-center"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.7 }}
            >
              <p className="text-sm text-slate-600 font-medium">
                👥 <span className="font-bold text-slate-900">{status?.groupSize || groupSize}</span> {status?.groupSize === 1 ? "pilgrim" : "pilgrims"} in your group
              </p>
            </motion.div>

            {/* LEAVE QUEUE BUTTON */}
            <motion.button
              onClick={handleLeave}
              disabled={loading}
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
              className="w-full bg-red-50 hover:bg-red-100 border-2 border-red-300 text-red-600 hover:text-red-700 px-6 py-4 rounded-xl font-bold transition-all duration-300 flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <AlertCircle className="w-5 h-5" />
              {loading ? "Leaving..." : "Leave Queue"}
            </motion.button>

            {/* REAL-TIME INDICATOR */}
            <motion.div 
              className="text-center text-xs text-slate-500 flex items-center justify-center gap-2"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.8 }}
            >
              <motion.div 
                className="w-2 h-2 bg-green-500 rounded-full"
                animate={{ opacity: [1, 0.3, 1] }}
                transition={{ duration: 2, repeat: Infinity }}
              />
              Real-time tracking active
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}