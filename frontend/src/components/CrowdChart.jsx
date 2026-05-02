import {
  BarChart,
  Bar,
  XAxis,
  Tooltip,
  ResponsiveContainer,
} from "recharts";

export default function CrowdChart({ data }) {
  /*if (!data || data.length === 0) {
    return <p className="text-gray-400">No data available</p>;
  }*/
 const fallbackData = [
  { time: "8 AM", value: 20 },
  { time: "9 AM", value: 35 },
  { time: "10 AM", value: 55 },
  { time: "11 AM", value: 80 },
  { time: "12 PM", value: 95 },
  { time: "1 PM", value: 90 },
  { time: "2 PM", value: 75 },
  { time: "3 PM", value: 60 },
  { time: "4 PM", value: 50 },
  { time: "5 PM", value: 65 },
  { time: "6 PM", value: 85 },
  { time: "7 PM", value: 70 },
];

const chartData = data && data.length > 0 ? data : fallbackData;


  return (
    <ResponsiveContainer width="100%" height={250}>
      <BarChart data={chartData}>
        <XAxis dataKey="time" />
        <Tooltip />
        <Bar dataKey="value" radius={[6, 6, 0, 0]} />
      </BarChart>
    </ResponsiveContainer>
  );
}