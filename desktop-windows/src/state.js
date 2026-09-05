const path = require("path");
const { pathToFileURL } = require("url");

const BOX_COUNT = 20;
const DEFAULT_VOLUME = 0.8;
const AUDIO_EXTENSIONS = [".mp3", ".wav", ".ogg", ".m4a", ".aac", ".flac"];

function clampVolume(value) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return DEFAULT_VOLUME;
  return Math.min(1, Math.max(0, parsed));
}

function createDefaultBox(id) {
  return {
    id,
    displayName: `Box ${id}`,
    originalFileName: null,
    internalFilePath: null,
    volume: DEFAULT_VOLUME,
    isLocked: false,
    updatedAtEpochMillis: null,
  };
}

function createDefaultBoxes() {
  return Array.from({ length: BOX_COUNT }, (_, index) => createDefaultBox(index + 1));
}

function sanitizeFileName(fileName) {
  const baseName = path.basename(String(fileName || "audio"));
  const cleaned = baseName
    .replace(/[<>:"/\\|?*\x00-\x1F]/g, "_")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 120);
  return cleaned || "audio";
}

function isSupportedAudioFile(filePath) {
  return AUDIO_EXTENSIONS.includes(path.extname(String(filePath || "")).toLowerCase());
}

function normalizeBox(rawBox, id) {
  const fallback = createDefaultBox(id);
  if (!rawBox || typeof rawBox !== "object") return fallback;

  const originalFileName = typeof rawBox.originalFileName === "string" && rawBox.originalFileName.trim()
    ? rawBox.originalFileName.trim()
    : null;
  const internalFilePath = typeof rawBox.internalFilePath === "string" && rawBox.internalFilePath.trim()
    ? rawBox.internalFilePath.trim()
    : null;
  const displayName = typeof rawBox.displayName === "string" && rawBox.displayName.trim()
    ? rawBox.displayName.trim()
    : originalFileName || fallback.displayName;

  return {
    id,
    displayName,
    originalFileName,
    internalFilePath,
    volume: clampVolume(rawBox.volume),
    isLocked: Boolean(rawBox.isLocked),
    updatedAtEpochMillis: Number.isFinite(Number(rawBox.updatedAtEpochMillis))
      ? Number(rawBox.updatedAtEpochMillis)
      : null,
  };
}

function mergeState(rawState) {
  const rawBoxes = rawState && Array.isArray(rawState.boxes) ? rawState.boxes : [];
  return {
    boxes: createDefaultBoxes().map((fallbackBox) => {
      const matchingBox = rawBoxes.find((box) => Number(box && box.id) === fallbackBox.id);
      return normalizeBox(matchingBox, fallbackBox.id);
    }),
  };
}

function boxToClient(box, fileExists) {
  const hasSavedAudio = Boolean(box.internalFilePath && fileExists);
  return {
    ...box,
    hasPendingAudio: false,
    hasSavedAudio,
    audioSrc: hasSavedAudio ? pathToFileURL(box.internalFilePath).href : null,
    statusMessage: box.isLocked ? "Bloqueado" : hasSavedAudio ? "Salvo" : "Vazio",
  };
}

module.exports = {
  AUDIO_EXTENSIONS,
  BOX_COUNT,
  DEFAULT_VOLUME,
  boxToClient,
  clampVolume,
  createDefaultBoxes,
  isSupportedAudioFile,
  mergeState,
  sanitizeFileName,
};
