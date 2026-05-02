export default function Hero() {
  return (
    <div className="w-full flex flex-col items-center text-center mt-20 px-4">

      {/* Badge */}
      <div className="bg-orange-100 text-orange-600 px-4 py-1 rounded-full text-sm mb-6">
        ● Live density across 6 places — updated in real-time
      </div>

      {/* Heading */}
      <h1 className="text-5xl md:text-7xl font-extrabold leading-tight max-w-4xl">
        Know the crowd,{" "}
        
          before you go.
        
      </h1>

      {/* Subtext */}
      <p className="text-gray-500 mt-6 max-w-2xl text-lg">
        India's first universal density-awareness platform. From Himalayan
        shrines to Mumbai promenades — skip the wait, time it right.
      </p>

      {/* Search */}
      <div className="mt-8 w-full max-w-2xl">
        <input
          type="text"
          placeholder="Search monuments, beaches, temples, or urban hubs across India..."
          className="w-full border rounded-full px-6 py-4 shadow-sm focus:outline-none focus:ring-2 focus:ring-orange-400"
        />
      </div>
    </div>
  );
}