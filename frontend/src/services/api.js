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
    `${BASE_URL}/queue/status/${placeId}?userId=${userId}`,
    { cache: "no-store" }
  );
  return res.json();
};

export const getPrediction = async (placeId) => {
  const res = await fetch(`http://localhost:8081/api/v1/crowdpulse/predict/${placeId}`);
  return res.json();
};

// ✅ LEAVE QUEUE (NEW - CRITICAL FIX)
export const leaveQueue = async (placeId, userId) => {
  const res = await fetch(
    `${BASE_URL}/queue/leave/${placeId}?userId=${userId}`,
    { method: "POST" }
  );
  return res.json();
};


export const submitCommunityUpdate = async (payload) => {

  const res = await fetch(
    `${BASE_URL}/community/update`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(payload),
    }
  );

  return res.json();
};

export const getLatestCommunityUpdate = async (placeId) => {

  const res = await fetch(
    `${BASE_URL}/community/latest/${placeId}`
  );

  return res.json();
};

export const getCommunityHistory = async (placeId) => {

  const res = await fetch(
    `${BASE_URL}/community/history/${placeId}`
  );

  return res.json();
};

// ✅ Get historical time-series data (hourly aggregated)
export const getTimeSeries = async (placeId) => {
  try {
    const res = await fetch(`${BASE_URL}/queue/timeseries/${placeId}`);
    return await res.json();
  } catch (err) {
    console.error("Error fetching time series", err);
    return [];
  }
};