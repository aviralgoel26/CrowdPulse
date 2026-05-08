import { useEffect, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  submitCommunityUpdate,
  getLatestCommunityUpdate,
  getCommunityHistory,
} from "../services/api";
import Navbar from "../components/Navbar";
import {
  Users,
  Zap,
  Activity,
  FileText,
  Send,
  Clock,
  Hash,
  MessageSquare,
  Radio,
  ChevronDown,
  Pencil,
  Check,
  X,
} from "lucide-react";

const statusConfig = {
  ACTIVE: { color: "text-emerald-600", dot: "bg-emerald-400", bg: "bg-emerald-50 border-emerald-200" },
  PAUSED: { color: "text-amber-600", dot: "bg-amber-400", bg: "bg-amber-50 border-amber-200" },
};

export default function CommunityPortal() {
  const [placeId, setPlaceId] = useState(1);
  const [queueLength, setQueueLength] = useState("");
  const [throughput, setThroughput] = useState("");
  const [status, setStatus] = useState("ACTIVE");
  const [note, setNote] = useState("");
  const [latest, setLatest] = useState(null);
  const [history, setHistory] = useState([]);
  const [submitting, setSubmitting] = useState(false);

  // ── Edit mode state ──
  const [editing, setEditing] = useState(false);
  const [editQueueLength, setEditQueueLength] = useState("");
  const [editThroughput, setEditThroughput] = useState("");
  const [editStatus, setEditStatus] = useState("ACTIVE");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const loadLatest = async () => {
      try {
        const data = await getLatestCommunityUpdate(placeId);
        setLatest(data);
        const historyData = await getCommunityHistory(placeId);
        setHistory(historyData);
      } catch (err) {
        console.error(err);
      }
    };
    loadLatest();
  }, [placeId]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);

    const payload = {
      placeId: Number(placeId),
      reportedQueueLength: Number(queueLength),
      throughputPerMin: Number(throughput),
      queueStatus: status,
      note: note,
    };

    try {
      const data = await submitCommunityUpdate(payload);
      console.log("Submitted:", data);
      alert("✅ Update submitted successfully");
      const latestData = await getLatestCommunityUpdate(placeId);
      setLatest(latestData);
      const historyData = await getCommunityHistory(placeId);
      setHistory(historyData);
      setQueueLength("");
      setThroughput("");
      setNote("");
    } catch (err) {
      console.error(err);
      alert("❌ Failed to submit");
    } finally {
      setSubmitting(false);
    }
  };

  // ── Start editing: populate fields from current latest ──
  const startEditing = () => {
    if (!latest) return;
    setEditQueueLength(latest.reportedQueueLength?.toString() || "");
    setEditThroughput(latest.throughputPerMin?.toString() || "");
    setEditStatus(latest.queueStatus || "ACTIVE");
    setEditing(true);
  };

  const cancelEditing = () => {
    setEditing(false);
  };

  // ── Save edited values as a new community update ──
  const saveEdit = async () => {
    setSaving(true);
    const payload = {
      placeId: Number(placeId),
      reportedQueueLength: Number(editQueueLength),
      throughputPerMin: Number(editThroughput),
      queueStatus: editStatus,
      note: "Updated via edit",
    };

    try {
      await submitCommunityUpdate(payload);
      const latestData = await getLatestCommunityUpdate(placeId);
      setLatest(latestData);
      const historyData = await getCommunityHistory(placeId);
      setHistory(historyData);
      setEditing(false);
    } catch (err) {
      console.error(err);
      alert("❌ Failed to save edit");
    } finally {
      setSaving(false);
    }
  };

  const inputCls =
    "w-full px-4 py-3 bg-white border border-slate-200 rounded-xl text-slate-800 placeholder-slate-400 text-sm font-medium focus:outline-none focus:border-[#FF9933]/50 focus:ring-1 focus:ring-[#FF9933]/20 transition-all";

  const editInputCls =
    "w-full px-3 py-2 bg-white border border-[#FF9933]/30 rounded-lg text-slate-800 text-sm font-bold focus:outline-none focus:border-[#FF9933] focus:ring-2 focus:ring-[#FF9933]/20 transition-all";

  return (
    <div className="min-h-screen bg-[#F8FAFC]">
      <Navbar />

      <div className="max-w-4xl mx-auto px-6 md:px-12 py-12">
        {/* Header */}
        <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="mb-10">
          <div className="flex items-center gap-3 mb-3">
            <div className="p-2.5 bg-[#FF9933]/10 border border-[#FF9933]/20 rounded-xl">
              <Radio className="w-5 h-5 text-[#FF9933]" />
            </div>
            <div>
              <h1 className="text-2xl md:text-3xl font-bold text-slate-900">Operator Portal</h1>
              <p className="text-sm text-slate-500 font-medium">Submit live ground updates</p>
            </div>
          </div>
        </motion.div>

        <div className="grid grid-cols-1 lg:grid-cols-5 gap-8">
          {/* ── LEFT: Form + Current State (3 cols) ── */}
          <div className="lg:col-span-3 space-y-6">

            {latest && (
              <motion.div
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                className="bg-white border border-slate-200 rounded-2xl p-6"
              >
                <div className="flex items-center justify-between mb-5">
                  <h2 className="text-sm font-bold text-slate-500 uppercase tracking-wider flex items-center gap-2">
                    <Activity className="w-4 h-4 text-[#FF9933]" />
                    Current Operational State
                  </h2>

                  {/* ── EDIT / SAVE / CANCEL BUTTONS ── */}
                  {!editing ? (
                    <motion.button
                      onClick={startEditing}
                      whileHover={{ scale: 1.03 }}
                      whileTap={{ scale: 0.97 }}
                      className="flex items-center gap-2 px-4 py-2 rounded-xl font-bold text-xs uppercase tracking-wider bg-slate-50 border border-slate-200 text-slate-600 hover:bg-slate-100 hover:border-slate-300 transition-all duration-200"
                    >
                      <Pencil className="w-3.5 h-3.5" />
                      Edit
                    </motion.button>
                  ) : (
                    <div className="flex items-center gap-2">
                      <motion.button
                        onClick={saveEdit}
                        disabled={saving}
                        whileHover={{ scale: 1.03 }}
                        whileTap={{ scale: 0.97 }}
                        className="flex items-center gap-1.5 px-3.5 py-2 rounded-xl font-bold text-xs uppercase tracking-wider bg-emerald-50 border border-emerald-200 text-emerald-600 hover:bg-emerald-100 transition-all duration-200 disabled:opacity-50"
                      >
                        {saving ? (
                          <motion.div
                            className="w-3.5 h-3.5 border-2 border-emerald-500 border-t-transparent rounded-full"
                            animate={{ rotate: 360 }}
                            transition={{ duration: 0.8, repeat: Infinity, ease: "linear" }}
                          />
                        ) : (
                          <Check className="w-3.5 h-3.5" />
                        )}
                        Save
                      </motion.button>
                      <motion.button
                        onClick={cancelEditing}
                        whileHover={{ scale: 1.03 }}
                        whileTap={{ scale: 0.97 }}
                        className="flex items-center gap-1.5 px-3.5 py-2 rounded-xl font-bold text-xs uppercase tracking-wider bg-red-50 border border-red-200 text-red-500 hover:bg-red-100 transition-all duration-200"
                      >
                        <X className="w-3.5 h-3.5" />
                        Cancel
                      </motion.button>
                    </div>
                  )}
                </div>

                <div className="grid grid-cols-2 gap-4">
                  {/* Queue Length */}
                  <div className={`rounded-xl p-4 border transition-all duration-200 ${editing ? "bg-[#FF9933]/5 border-[#FF9933]/20" : "bg-slate-50 border-slate-200"}`}>
                    <div className="flex items-center gap-2 mb-2">
                      <Users className="w-3.5 h-3.5 text-[#FF9933]" />
                      <span className="text-xs text-slate-500 font-semibold uppercase tracking-wide">Queue Length</span>
                    </div>
                    {editing ? (
                      <input
                        type="number"
                        value={editQueueLength}
                        onChange={(e) => setEditQueueLength(e.target.value)}
                        className={editInputCls}
                        placeholder="e.g. 500"
                        autoFocus
                      />
                    ) : (
                      <p className="text-2xl font-bold text-slate-900">{latest.reportedQueueLength}</p>
                    )}
                  </div>

                  {/* Throughput */}
                  <div className={`rounded-xl p-4 border transition-all duration-200 ${editing ? "bg-[#FF9933]/5 border-[#FF9933]/20" : "bg-slate-50 border-slate-200"}`}>
                    <div className="flex items-center gap-2 mb-2">
                      <Zap className="w-3.5 h-3.5 text-blue-500" />
                      <span className="text-xs text-slate-500 font-semibold uppercase tracking-wide">Throughput</span>
                    </div>
                    {editing ? (
                      <input
                        type="number"
                        value={editThroughput}
                        onChange={(e) => setEditThroughput(e.target.value)}
                        className={editInputCls}
                        placeholder="e.g. 50"
                      />
                    ) : (
                      <p className="text-2xl font-bold text-slate-900">{latest.throughputPerMin}<span className="text-sm text-slate-400 ml-1">/min</span></p>
                    )}
                  </div>

                  {/* Status */}
                  <div className={`rounded-xl p-4 border transition-all duration-200 ${editing ? "bg-[#FF9933]/5 border-[#FF9933]/20" : "bg-slate-50 border-slate-200"}`}>
                    <div className="flex items-center gap-2 mb-2">
                      <Activity className="w-3.5 h-3.5 text-emerald-500" />
                      <span className="text-xs text-slate-500 font-semibold uppercase tracking-wide">Status</span>
                    </div>
                    {editing ? (
                      <div className="relative">
                        <select
                          value={editStatus}
                          onChange={(e) => setEditStatus(e.target.value)}
                          className={`${editInputCls} appearance-none pr-8 cursor-pointer`}
                        >
                          <option value="ACTIVE">Active</option>
                          <option value="PAUSED">Paused</option>
                        </select>
                        <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-slate-400 pointer-events-none" />
                      </div>
                    ) : (
                      <div className="flex items-center gap-2">
                        <span className={`w-2 h-2 rounded-full ${statusConfig[latest.queueStatus]?.dot || "bg-slate-400"}`} />
                        <p className={`text-lg font-bold ${statusConfig[latest.queueStatus]?.color || "text-slate-600"}`}>
                          {latest.queueStatus}
                        </p>
                      </div>
                    )}
                  </div>

                  {/* Note */}
                  <div className="bg-slate-50 border border-slate-200 rounded-xl p-4">
                    <div className="flex items-center gap-2 mb-2">
                      <MessageSquare className="w-3.5 h-3.5 text-slate-400" />
                      <span className="text-xs text-slate-500 font-semibold uppercase tracking-wide">Note</span>
                    </div>
                    <p className="text-sm text-slate-600 font-medium leading-relaxed">{latest.note || "No notes"}</p>
                  </div>
                </div>
              </motion.div>
            )}

            {/* Submit Form */}
            <motion.form
              onSubmit={handleSubmit}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.1 }}
              className="bg-white border border-slate-200 rounded-2xl p-6 space-y-5"
            >
              <h2 className="text-sm font-bold text-slate-500 uppercase tracking-wider flex items-center gap-2">
                <Send className="w-4 h-4 text-[#FF9933]" />
                Submit Ground Update
              </h2>

              <div>
                <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wide mb-2">Place ID</label>
                <input type="number" value={placeId} onChange={(e) => setPlaceId(e.target.value)} className={inputCls} />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wide mb-2">Queue Length</label>
                  <input type="number" value={queueLength} onChange={(e) => setQueueLength(e.target.value)} className={inputCls} placeholder="e.g. 150" required />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wide mb-2">Throughput / min</label>
                  <input type="number" value={throughput} onChange={(e) => setThroughput(e.target.value)} className={inputCls} placeholder="e.g. 8" required />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wide mb-2">Queue Status</label>
                <div className="relative">
                  <select value={status} onChange={(e) => setStatus(e.target.value)} className={`${inputCls} appearance-none pr-10 cursor-pointer`}>
                    <option value="ACTIVE">Active</option>
                    <option value="PAUSED">Paused</option>
                  </select>
                  <ChevronDown className="absolute right-4 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 pointer-events-none" />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wide mb-2">Notes (optional)</label>
                <textarea value={note} onChange={(e) => setNote(e.target.value)} className={`${inputCls} resize-none`} rows={3} placeholder="Any observations..." />
              </div>

              <motion.button
                type="submit"
                disabled={submitting}
                whileHover={{ scale: 1.01 }}
                whileTap={{ scale: 0.99 }}
                className="w-full flex items-center justify-center gap-2 bg-[#FF9933] hover:bg-[#FF9933]/90 disabled:bg-slate-200 disabled:text-slate-400 text-white py-3.5 rounded-xl font-bold text-sm transition-all duration-300"
              >
                {submitting ? (
                  <>
                    <motion.div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full" animate={{ rotate: 360 }} transition={{ duration: 0.8, repeat: Infinity, ease: "linear" }} />
                    Submitting...
                  </>
                ) : (
                  <>
                    <Send className="w-4 h-4" />
                    Submit Update
                  </>
                )}
              </motion.button>
            </motion.form>
          </div>

          {/* ── RIGHT: History (2 cols) ── */}
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.2 }}
            className="lg:col-span-2"
          >
            <div className="bg-white border border-slate-200 rounded-2xl p-6 sticky top-24">
              <h2 className="text-sm font-bold text-slate-500 uppercase tracking-wider mb-5 flex items-center gap-2">
                <Clock className="w-4 h-4 text-blue-500" />
                Recent Updates
              </h2>

              <div className="space-y-3 max-h-[600px] overflow-y-auto pr-1" style={{ scrollbarWidth: "thin", scrollbarColor: "rgba(255,153,51,0.2) transparent" }}>
                {history.length === 0 ? (
                  <p className="text-sm text-slate-400 text-center py-8">No updates yet</p>
                ) : (
                  history.map((item, i) => (
                    <motion.div
                      key={item.id}
                      initial={{ opacity: 0, x: 10 }}
                      animate={{ opacity: 1, x: 0 }}
                      transition={{ delay: i * 0.03 }}
                      className="bg-slate-50 border border-slate-200 hover:border-slate-300 rounded-xl p-4 transition-all duration-200"
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="space-y-1.5">
                          <div className="flex items-center gap-2">
                            <Hash className="w-3 h-3 text-slate-400" />
                            <span className="text-sm font-bold text-slate-800">Queue: {item.reportedQueueLength}</span>
                          </div>
                          <p className="text-xs text-slate-500">{item.throughputPerMin}/min throughput</p>
                          {item.note && <p className="text-xs text-slate-400 italic">{item.note}</p>}
                        </div>

                        <div className="text-right flex-shrink-0">
                          <div className="flex items-center gap-1.5 mb-1">
                            <span className={`w-1.5 h-1.5 rounded-full ${statusConfig[item.queueStatus]?.dot || "bg-slate-400"}`} />
                            <span className={`text-xs font-bold ${statusConfig[item.queueStatus]?.color || "text-slate-500"}`}>
                              {item.queueStatus}
                            </span>
                          </div>
                          <p className="text-[10px] text-slate-400">
                            {new Date(item.createdAt).toLocaleTimeString()}
                          </p>
                        </div>
                      </div>
                    </motion.div>
                  ))
                )}
              </div>
            </div>
          </motion.div>
        </div>
      </div>
    </div>
  );
}