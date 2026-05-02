const BASE_URL = "http://localhost:8081/api/v1/crowdpulse";

// ✅ Get all places
export const getPlaces = async () => {
  try {
    const res = await fetch(`${BASE_URL}/places`);
    return await res.json();
  } catch (err) {
    console.error("Error fetching places", err);
    return [];
  }
};

// ✅ Get vibe
export const getVibe = async (placeId) => {
  try {
    const res = await fetch(`${BASE_URL}/metrics/vibe/${placeId}`);
    return await res.json();
  } catch (err) {
    console.error("Error fetching vibe", err);
    return null;
  }
};

// ✅ Get recommendation
export const getRecommendation = async (placeId) => {
  try {
    const res = await fetch(`${BASE_URL}/metrics/recommendation/${placeId}`);
    return await res.json();
  } catch (err) {
    console.error("Error fetching recommendation", err);
    return null;
  }
};

// ✅ Get place details
export const getPlaceDetails = async (id) => {
  const res = await fetch(`${BASE_URL}/places/${id}`);
  return res.json();
};
//  ✅ Get trend data
export const getTrend = async (id) => {
  const res = await fetch(`${BASE_URL}/metrics/trend/${id}`);
  return res.json();
};

//  ✅ Get wait time
export const getWaitTime = async (id) => {
  const res = await fetch(`${BASE_URL}/queue/wait-time/${id}`);
  return res.json();
};