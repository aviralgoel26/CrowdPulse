import { useNavigate } from "react-router-dom";

const PlaceCard = ({ place }) => {
  const navigate = useNavigate();

  // TEMP IMAGE (we'll make dynamic later)
  const image =
    place.name === "Kedarnath Temple"
      ? "https://images.unsplash.com/photo-1609947017136-9daf32a5eb16"
      : place.name === "Marine Drive"
      ? "https://images.unsplash.com/photo-1595658658481-d53d3f999875"
      : "https://images.unsplash.com/photo-1587135941948-670b381f08ce";

  // TEMP status (later from backend)
  const status = "Bustling";

  return (
    <div
      onClick={() => navigate(`/place/${place.id}`)}
      className="relative rounded-2xl overflow-hidden cursor-pointer group shadow-md hover:shadow-xl transition"
    >
      {/* Image */}
      <img
        src={image}
        alt={place.name}
        className="w-full h-64 object-cover group-hover:scale-105 transition duration-300"
      />

      {/* Gradient overlay */}
      <div className="absolute inset-0 bg-gradient-to-t from-black/70 to-transparent"></div>

      {/* Status badge */}
      <div className="absolute top-3 left-3 bg-white px-3 py-1 rounded-full text-sm font-medium shadow">
        <span className="inline-block w-2 h-2 bg-yellow-400 rounded-full mr-2"></span>
        {status}
      </div>

      {/* Bottom content */}
      <div className="absolute bottom-0 p-4 text-white w-full">
        <h2 className="text-lg font-semibold">{place.name}</h2>
        <p className="text-sm text-gray-200">
          {place.city}, {place.state}
        </p>

        {/* Tag */}
        <div className="mt-2 inline-block bg-white text-black text-xs px-3 py-1 rounded-full">
          {place.type}
        </div>
      </div>
    </div>
  );
};

export default PlaceCard;