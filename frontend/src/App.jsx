import { BrowserRouter, Routes, Route } from "react-router-dom";
import Home from "./pages/Home";
import Explore from "./pages/Explore";
import PlaceDetail from "./pages/PlaceDetail";
import { getPlaces, getVibe } from "./services/api";

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/explore" element={<Explore />} />
        <Route path="/place/:id" element={<PlaceDetail />} />
        
      </Routes>
    </BrowserRouter>
  );
}

export default App;