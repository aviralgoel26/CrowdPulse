import {
  ResponsiveContainer,
  LineChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  Legend,
  Area,
  AreaChart,
  ReferenceLine
} from "recharts";

// Merge forecast (historical) + community (actual) into a single chart-friendly array
function mergeTimeSeries(forecast, community) {
  const map = new Map();

  // Add forecast data points (historical + prediction)
  if (forecast && forecast.length > 0) {
    forecast.forEach((point) => {
      map.set(point.time, {
        time: point.time,
        forecast: point.wait,
        community: null,
      });
    });
  }

  // Add community data points (actual ground truth)
  if (community && community.length > 0) {
    community.forEach((point) => {
      const existing = map.get(point.time);
      if (existing) {
        existing.community = point.wait;
      } else {
        map.set(point.time, {
          time: point.time,
          forecast: null,
          community: point.wait,
        });
      }
    });
  }

  // Sort by time
  return Array.from(map.values()).sort((a, b) => a.time.localeCompare(b.time));
}

// Custom tooltip
const CustomTooltip = ({ active, payload, label }) => {
  if (!active || !payload || !payload.length) return null;
  return (
    <div
      style={{
        background: "white",
        border: "1px solid #e2e8f0",
        borderRadius: "12px",
        padding: "12px 16px",
        boxShadow: "0 4px 20px rgba(0,0,0,0.08)",
      }}
    >
      <p style={{ fontSize: 12, fontWeight: 700, color: "#334155", marginBottom: 6 }}>
        {label}
      </p>
      {payload.map((entry, idx) => (
        <p key={idx} style={{ fontSize: 12, color: entry.color, fontWeight: 600, margin: "2px 0" }}>
          {entry.name}: {entry.value} min
        </p>
      ))}
    </div>
  );
};

export default function CrowdChart({ data, communityData, currentHour }) {
  const merged = mergeTimeSeries(data, communityData);

  // If no merged data, show a default fallback
  const chartData =
    merged && merged.length > 0
      ? merged
      : [
          { time: "10:00", forecast: 20, community: 22 },
          { time: "12:00", forecast: 45, community: 50 },
          { time: "14:00", forecast: 60, community: 55 },
          { time: "16:00", forecast: 35, community: 40 },
        ];

  const hasForecast = chartData.some((d) => d.forecast !== null);
  const hasCommunity = chartData.some((d) => d.community !== null);

  return (
    <div className="w-full h-[350px] relative">

      <ResponsiveContainer width="100%" height="100%">
        <AreaChart
          data={chartData}
          margin={{
            top: 20,
            right: 30,
            left: 10,
            bottom: 10,
          }}
        >
          {/* Grid */}
          <CartesianGrid
            strokeDasharray="3 3"
            stroke="#e5e7eb"
            vertical={false}
          />

          {/* X Axis */}
          <XAxis
            dataKey="time"
            tick={{ fontSize: 12, fill: "#94a3b8" }}
            axisLine={{ stroke: "#e2e8f0" }}
            tickLine={false}
          />

          {/* Y Axis */}
          <YAxis
            tick={{ fontSize: 12, fill: "#94a3b8" }}
            axisLine={false}
            tickLine={false}
            label={{
              value: "Wait (min)",
              angle: -90,
              position: "insideLeft",
              style: { fontSize: 12, fill: "#94a3b8" },
            }}
          />

          {/* NOW Reference Line */}
          {currentHour && (
            <ReferenceLine
              x={currentHour}
              stroke="#FF9933"
              strokeWidth={2}
              strokeDasharray="4 4"
              label={{
                value: "NOW",
                position: "top",
                fill: "#FF9933",
                fontSize: 11,
                fontWeight: 700,
              }}
            />
          )}

          {/* Tooltip */}
          <Tooltip content={<CustomTooltip />} />

          {/* Legend */}
          <Legend
            iconType="circle"
            wrapperStyle={{ fontSize: 12, paddingTop: 8 }}
          />

          {/* Community Area + Line (solid orange) — Actual ground truth */}
          {hasCommunity && (
            <>
              <defs>
                <linearGradient id="communityGradient" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#FF9933" stopOpacity={0.2} />
                  <stop offset="95%" stopColor="#FF9933" stopOpacity={0} />
                </linearGradient>
              </defs>
              <Area
                type="monotone"
                dataKey="community"
                name="Community (Actual)"
                stroke="#FF9933"
                strokeWidth={3}
                fill="url(#communityGradient)"
                dot={{ r: 4, fill: "#FF9933", stroke: "#fff", strokeWidth: 2 }}
                activeDot={{ r: 7, fill: "#FF9933", stroke: "#fff", strokeWidth: 2 }}
                connectNulls
              />
            </>
          )}

          {/* Forecast Line (dashed blue) — Historical data + prediction */}
          {hasForecast && (
            <>
              <defs>
                <linearGradient id="forecastGradient" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.1} />
                  <stop offset="95%" stopColor="#3b82f6" stopOpacity={0} />
                </linearGradient>
              </defs>
              <Area
                type="monotone"
                dataKey="forecast"
                name="Forecast (Historical)"
                stroke="#3b82f6"
                strokeWidth={2}
                strokeDasharray="6 4"
                fill="url(#forecastGradient)"
                dot={{ r: 3, fill: "#3b82f6", stroke: "#fff", strokeWidth: 2 }}
                activeDot={{ r: 6, fill: "#3b82f6", stroke: "#fff", strokeWidth: 2 }}
                connectNulls
              />
            </>
          )}
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}