import {
  ResponsiveContainer,
  LineChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid
} from "recharts";

export default function CrowdChart({ data }) {

  // fallback
  const chartData = data && data.length > 0
    ? data
    : [
        { time: "10:00", wait: 20 },
        { time: "12:00", wait: 45 },
        { time: "14:00", wait: 60 },
        { time: "16:00", wait: 35 },
      ];

  return (
    <div className="w-full h-[350px]">

      <ResponsiveContainer width="100%" height="100%">

        <LineChart
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
          />

          {/* X Axis */}
          <XAxis
            dataKey="time"
            tick={{ fontSize: 12 }}
          />

          {/* Y Axis */}
          <YAxis
            tick={{ fontSize: 12 }}
            label={{
              value: "Wait (min)",
              angle: -90,
              position: "insideLeft"
            }}
          />

          {/* Tooltip */}
          <Tooltip />

          {/* Trend Line */}
          <Line
            type="monotone"
            dataKey="wait"
            stroke="#FF9933"
            strokeWidth={4}
            dot={{ r: 5 }}
            activeDot={{ r: 8 }}
          />

        </LineChart>

      </ResponsiveContainer>

    </div>
  );
}