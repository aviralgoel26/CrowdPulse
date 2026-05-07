import { useNavigate } from "react-router-dom";
import { MapPin, ArrowRight } from "lucide-react";

const PlaceCard = ({ place }) => {
  const navigate = useNavigate();

  // Parse images from backend JSON
  let images = [];
  try {
    images = JSON.parse(place.images || "[]");
  } catch {
    images = [];
  }

  const image =
    images.length > 0
      ? images[0].startsWith("http")
        ? images[0]
        : `http://localhost:8081${images[0]}`
      : "https://images.unsplash.com/photo-1587135941948-670b381f08ce";

  // Crowd status from place data
  const status = place.crowdStatus || "Moderate";
  const statusColors = {
    Low: { dot: "bg-emerald-400", text: "text-emerald-600", bg: "bg-white/90 border-emerald-200" },
    Moderate: { dot: "bg-amber-400", text: "text-amber-600", bg: "bg-white/90 border-amber-200" },
    Bustling: { dot: "bg-orange-400", text: "text-orange-600", bg: "bg-white/90 border-orange-200" },
    Congested: { dot: "bg-red-400", text: "text-red-600", bg: "bg-white/90 border-red-200" },
    Heavy: { dot: "bg-red-400", text: "text-red-600", bg: "bg-white/90 border-red-200" },
  };
  const sc = statusColors[status] || statusColors.Moderate;

  return (
    <div
      onClick={() => navigate(`/place/${place.id}`)}
      className="group relative rounded-2xl overflow-hidden cursor-pointer bg-white border border-slate-200 hover:border-slate-300 transition-all duration-300 hover:translate-y-[-2px] hover:shadow-lg"
    >
      {/* Image */}
      <div className="relative h-56 overflow-hidden">
        <img
          src={image}
          alt={place.name}
          onError={(e) => {
            e.target.src = "https://images.unsplash.com/photo-1587135941948-670b381f08ce";
          }}
          className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-black/70 via-black/20 to-transparent" />

        {/* Status badge */}
        <div className={`absolute top-3 right-3 flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-bold border backdrop-blur-sm ${sc.bg}`}>
          <span className={`w-2 h-2 rounded-full ${sc.dot}`} />
          <span className={sc.text}>{status}</span>
        </div>

        {/* Name & location overlay */}
        <div className="absolute bottom-0 left-0 right-0 p-5">
          <h3 className="text-lg font-bold text-white mb-1">{place.name}</h3>
          <div className="flex items-center gap-1.5 text-white/80 text-sm">
            <MapPin className="w-3.5 h-3.5 text-[#FF9933]" />
            <span>{place.city}, {place.state}</span>
          </div>
        </div>
      </div>

      {/* Bottom info */}
      <div className="flex items-center justify-between px-5 py-4 border-t border-slate-100">
        <div className="flex items-center gap-2">
          <span className="text-xs text-slate-500 font-medium">
            {place.type || "Sacred Site"}
          </span>
        </div>
        <ArrowRight className="w-4 h-4 text-slate-400 group-hover:text-[#FF9933] group-hover:translate-x-1 transition-all duration-200" />
      </div>
    </div>
  );
};

export default PlaceCard;