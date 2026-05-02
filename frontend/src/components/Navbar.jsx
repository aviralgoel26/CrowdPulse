import { Link } from "react-router-dom";

export default function Navbar() {
  return (
    <div className="w-full flex justify-between items-center px-8 py-4 border-b">
      
      {/* Logo */}
      <div className="flex items-center gap-2">
        <div className="bg-orange-500 text-white px-2 py-1 rounded font-bold">
          ⚡
        </div>
        <h1 className="text-lg font-semibold">CrowdFlow India</h1>
      </div>

      {/* Links */}
      <div className="flex gap-8 text-gray-600 font-medium">
        <Link to="/">Discover</Link>
        <Link to="/explore">For Operators</Link>
        <Link to="/about">About</Link>
      </div>
    </div>
  );
}