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


// ✅ Get places Details
export const getPlaceDetails = async (id) => {
  const res = await fetch(`${BASE_URL}/places/${id}/details`);
  return res.json();
};







//  ✅ Get wait time
export const getWaitTime = async (id) => {
  const res = await fetch(`${BASE_URL}/queue/wait-time/${id}`);
  return res.json();
};


export const joinQueue = async (placeId, userId, groupSize) => {
  const res = await fetch(
    `${BASE_URL}/queue/join/${placeId}?userId=${userId}&groupSize=${groupSize}`,
    { method: "POST" }
  );
  return res.json();
};

export const getQueueStatus = async (placeId, userId) => {
  const res = await fetch(
    `${BASE_URL}/queue/status/${placeId}?userId=${userId}`
  );
  return res.json();
};

export const getPrediction = async (placeId) => {
  const res = await fetch(`http://localhost:8081/api/v1/crowdpulse/predict/${placeId}`);
  return res.json();
};