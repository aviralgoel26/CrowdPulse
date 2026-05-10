/**
 * Centralized Image Utility Service
 * Handles all image mappings and fallback logic across the frontend
 * 
 * This ensures:
 * - Consistent image handling across all components
 * - Centralized image mapping (single source of truth)
 * - Reliable fallback strategies
 * - No duplicate logic across components
 * - Future compatibility with DB-based images
 */

/**
 * Central image mapping for all places
 * Maps place names to arrays of image paths (supports multiple images per place for carousels)
 * All images are stored in frontend/public folder
 */
const PLACE_IMAGE_MAP = {
  "Kedarnath Temple": ["/kedarnath.jpg"],
  "Kashi Vishwanath": ["/kashi-vishwanath.webp"],
  "Vaishno Devi Temple": ["/vaishno_devi.jpg"],
  "Taj Mahal": ["/marine_drive.jpg"], // TODO: Replace with /Taj_Mahal.jpg when asset is added
  "Marine Drive": ["/marine_drive.jpg"],
};

/**
 * ID-based image mapping (primary lookup — IDs are stable and never change)
 * Add entries here as you discover the DB IDs for each place.
 * This takes priority over name-based matching.
 */
const PLACE_ID_IMAGE_MAP = {
  // Populate with your actual DB IDs, e.g.:
  // 1: ["/kedarnath.jpg"],
  // 2: ["/kashi-vishwanath.webp"],
};

/**
 * Keyword aliases — maps common short names / variants to their canonical map key.
 * Each keyword is checked against the lowercased place name.
 * Order matters: first match wins, so put more specific keywords first.
 */
const KEYWORD_ALIASES = [
  { keywords: ["kedarnath"],           canonicalKey: "Kedarnath Temple" },
  { keywords: ["kashi", "vishwanath"], canonicalKey: "Kashi Vishwanath" },
  { keywords: ["vaishno", "devi"],     canonicalKey: "Vaishno Devi Temple" },
  { keywords: ["taj", "mahal"],        canonicalKey: "Taj Mahal" },
  { keywords: ["marine", "drive"],     canonicalKey: "Marine Drive" },
];

/**
 * Default fallback image for unknown places
 * This is used when a place doesn't exist in PLACE_IMAGE_MAP
 */
const DEFAULT_IMAGE = "/marine_drive.jpg"; // Fallback to marine_drive since default.jpg doesn't exist yet

/**
 * Normalize a place name for comparison: lowercase, collapse whitespace, trim.
 */
const normalize = (name) =>
  (name || "").toLowerCase().replace(/\s+/g, " ").trim();

/**
 * Get image path(s) for a place
 * 
 * @param {string} placeName - Name of the place
 * @param {number|string} [placeId] - Optional numeric place ID (most reliable)
 * @returns {string[]} Array of image paths for the place (supports carousel)
 * 
 * Lookup order:
 *  1. ID-based lookup  (if placeId provided and present in PLACE_ID_IMAGE_MAP)
 *  2. Exact name match (trimmed)
 *  3. Substring match  (case-insensitive, either direction)
 *  4. Keyword / alias match
 *  5. Default fallback
 */
export const getImagesForPlace = (placeName, placeId) => {
  // ── 1. ID-based lookup (highest priority) ──────────────────
  if (placeId != null) {
    const idKey = Number(placeId);
    const byId = PLACE_ID_IMAGE_MAP[idKey];
    if (byId && byId.length > 0) {
      console.debug(`🖼️ Image match via ID: ${placeId} → ${byId[0]}`);
      return byId;
    }
  }

  if (!placeName) {
    return [DEFAULT_IMAGE];
  }

  const trimmed = placeName.trim();

  // ── 2. Exact name match ────────────────────────────────────
  const exact = PLACE_IMAGE_MAP[trimmed];
  if (exact && exact.length > 0) {
    return exact;
  }

  const lowerName = normalize(placeName);

  // ── 3. Substring match (case-insensitive, both directions) ─
  for (const [key, value] of Object.entries(PLACE_IMAGE_MAP)) {
    const lowerKey = normalize(key);
    if (lowerKey.includes(lowerName) || lowerName.includes(lowerKey)) {
      if (value && value.length > 0) {
        console.debug(`🖼️ Image match via substring: "${placeName}" → "${key}"`);
        return value;
      }
    }
  }

  // ── 4. Keyword / alias match ───────────────────────────────
  for (const alias of KEYWORD_ALIASES) {
    const matched = alias.keywords.some((kw) => lowerName.includes(kw));
    if (matched) {
      const images = PLACE_IMAGE_MAP[alias.canonicalKey];
      if (images && images.length > 0) {
        console.debug(`🖼️ Image match via keyword alias: "${placeName}" → "${alias.canonicalKey}"`);
        return images;
      }
    }
  }

  // ── 5. Fallback ────────────────────────────────────────────
  console.warn(`⚠️ Place "${placeName}" (id=${placeId}) not found in image map, using default`);
  return [DEFAULT_IMAGE];
};

/**
 * Get single image for a place (for non-carousel components)
 * 
 * @param {string} placeName - Name of the place
 * @param {number|string} [placeId] - Optional numeric place ID
 * @returns {string} Single image path (first image if carousel)
 */
export const getImageForPlace = (placeName, placeId) => {
  const images = getImagesForPlace(placeName, placeId);
  return images[0] || DEFAULT_IMAGE;
};

/**
 * Handle image load error gracefully
 * This function should be used in onError handlers across components
 * 
 * @param {Event} event - Image onError event
 * @param {string} placeName - Optional: place name to attempt lookup
 * 
 * Example:
 * <img onError={(e) => handleImageError(e, place.name)} />
 */
export const handleImageError = (event, placeName) => {
  if (!event?.target) return;

  // Prevent infinite loops by setting onerror to null
  event.target.onerror = null;

  // Try to get fallback for this place
  const fallbackImage = getImageForPlace(placeName);

  // Only set fallback if it's different from current src to avoid loops
  if (fallbackImage && event.target.src !== fallbackImage) {
    event.target.src = fallbackImage;
  } else {
    // If all else fails, use default
    event.target.src = DEFAULT_IMAGE;
  }
};

/**
 * Export centralized constant for tests/debugging
 */
export const IMAGE_CONFIG = {
  MAP: PLACE_IMAGE_MAP,
  ID_MAP: PLACE_ID_IMAGE_MAP,
  ALIASES: KEYWORD_ALIASES,
  DEFAULT: DEFAULT_IMAGE,
};

// Debug: Log image configuration on module load (remove in production)
if (process.env.NODE_ENV === "development") {
  console.log("🖼️ Image Utility Service loaded:", IMAGE_CONFIG);
  console.log("🖼️ Place name mappings:", Object.keys(PLACE_IMAGE_MAP));
}
