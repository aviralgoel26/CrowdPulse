import { BrowserRouter, Routes, Route } from "react-router-dom";
import Home from "./pages/Home";
import Explore from "./pages/Explore";
import PlaceDetail from "./pages/PlaceDetail";
import { getPlaces } from "./services/api";
import CommunityPortal from "./pages/CommunityPortal";
function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/explore" element={<Explore />} />
        <Route path="/place/:id" element={<PlaceDetail />} />
        <Route path="/community" element={<CommunityPortal />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;