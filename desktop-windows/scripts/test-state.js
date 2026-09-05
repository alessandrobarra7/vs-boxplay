const assert = require("node:assert/strict");
const path = require("node:path");
const {
  BOX_COUNT,
  boxToClient,
  clampVolume,
  createDefaultBoxes,
  isSupportedAudioFile,
  mergeState,
  sanitizeFileName,
} = require("../src/state");

const boxes = createDefaultBoxes();
assert.equal(BOX_COUNT, 20);
assert.equal(boxes.length, 20);
assert.deepEqual(boxes.map((box) => box.id), Array.from({ length: 20 }, (_, index) => index + 1));
assert.equal(boxes[0].displayName, "Box 1");
assert.equal(boxes[19].displayName, "Box 20");

assert.equal(clampVolume(-1), 0);
assert.equal(clampVolume(2), 1);
assert.equal(clampVolume("0.45"), 0.45);
assert.equal(clampVolume("not-a-number"), 0.8);

assert.equal(sanitizeFileName("..\\Abertura:final?.mp3"), "Abertura_final_.mp3");
assert.equal(sanitizeFileName("   "), "audio");

assert.equal(isSupportedAudioFile("intro.MP3"), true);
assert.equal(isSupportedAudioFile("efeito.wav"), true);
assert.equal(isSupportedAudioFile("documento.pdf"), false);

const merged = mergeState({
  boxes: [
    {
      id: 3,
      displayName: "Tema.mp3",
      originalFileName: "Tema.mp3",
      internalFilePath: path.join("C:", "boxplay", "tema.mp3"),
      volume: 4,
      isLocked: true,
      updatedAtEpochMillis: "1000",
    },
  ],
});
assert.equal(merged.boxes.length, 20);
assert.equal(merged.boxes[2].displayName, "Tema.mp3");
assert.equal(merged.boxes[2].volume, 1);
assert.equal(merged.boxes[2].isLocked, true);
assert.equal(merged.boxes[0].displayName, "Box 1");

const clientBox = boxToClient(merged.boxes[2], true);
assert.equal(clientBox.hasSavedAudio, true);
assert.equal(clientBox.hasPendingAudio, false);
assert.equal(clientBox.statusMessage, "Bloqueado");
assert.ok(clientBox.audioSrc.startsWith("file:///"));

console.log("BOXPLAY desktop state tests passed.");
