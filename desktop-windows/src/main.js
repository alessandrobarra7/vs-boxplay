const { app, BrowserWindow, dialog, ipcMain, shell } = require("electron");
const fs = require("fs");
const fsp = require("fs/promises");
const path = require("path");
const {
  AUDIO_EXTENSIONS,
  boxToClient,
  clampVolume,
  isSupportedAudioFile,
  mergeState,
  sanitizeFileName,
} = require("./state");

app.setName("BOXPLAY");
if (process.platform === "win32") {
  app.setAppUserModelId("com.boxplay.windows");
}

let mainWindow;
const pendingSelections = new Map();

function getStoragePaths() {
  const userData = app.getPath("userData");
  return {
    userData,
    audioDirectory: path.join(userData, "boxplay-audios"),
    statePath: path.join(userData, "boxes.json"),
  };
}

async function ensureStorage() {
  const { audioDirectory } = getStoragePaths();
  await fsp.mkdir(audioDirectory, { recursive: true });
}

async function readState() {
  await ensureStorage();
  const { statePath } = getStoragePaths();
  try {
    const content = await fsp.readFile(statePath, "utf8");
    return mergeState(JSON.parse(content));
  } catch (error) {
    if (error.code !== "ENOENT") {
      console.warn("Failed to read BOXPLAY state:", error);
    }
    return mergeState(null);
  }
}

async function writeState(state) {
  const { statePath } = getStoragePaths();
  await fsp.writeFile(statePath, JSON.stringify(mergeState(state), null, 2), "utf8");
}

function findBox(state, boxId) {
  const id = Number(boxId);
  return state.boxes.find((box) => box.id === id);
}

function fileExists(filePath) {
  return Boolean(filePath && fs.existsSync(filePath) && fs.statSync(filePath).isFile());
}

function toClientState(state) {
  return {
    boxes: state.boxes.map((box) => boxToClient(box, fileExists(box.internalFilePath))),
    storagePath: getStoragePaths().userData,
  };
}

function isInternalAudioPath(filePath) {
  if (!filePath) return false;
  const { audioDirectory } = getStoragePaths();
  const relativePath = path.relative(audioDirectory, filePath);
  return relativePath && !relativePath.startsWith("..") && !path.isAbsolute(relativePath);
}

async function deletePreviousAudio(previousPath, nextPath) {
  if (!previousPath || previousPath === nextPath || !isInternalAudioPath(previousPath)) return;
  try {
    await fsp.unlink(previousPath);
  } catch (error) {
    if (error.code !== "ENOENT") {
      console.warn("Failed to delete previous BOXPLAY audio:", error);
    }
  }
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1220,
    height: 780,
    minWidth: 960,
    minHeight: 620,
    backgroundColor: "#e8eef5",
    title: "BOXPLAY",
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      preload: path.join(__dirname, "preload.js"),
    },
  });

  mainWindow.removeMenu();
  mainWindow.loadFile(path.join(__dirname, "index.html"));
}

app.whenReady().then(() => {
  createWindow();

  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") app.quit();
});

ipcMain.handle("boxplay:get-state", async () => toClientState(await readState()));

ipcMain.handle("boxplay:select-audio", async (_event, boxId) => {
  const state = await readState();
  const box = findBox(state, boxId);
  if (!box) return { ok: false, message: "Box nao encontrado." };
  if (box.isLocked) return { ok: false, message: "Box bloqueado." };

  const result = await dialog.showOpenDialog(mainWindow, {
    title: `Selecionar audio para Box ${box.id}`,
    properties: ["openFile"],
    filters: [
      { name: "Audios", extensions: AUDIO_EXTENSIONS.map((extension) => extension.slice(1)) },
      { name: "Todos os arquivos", extensions: ["*"] },
    ],
  });

  if (result.canceled || result.filePaths.length === 0) return { ok: false, canceled: true };

  const selectedPath = result.filePaths[0];
  if (!isSupportedAudioFile(selectedPath)) {
    return { ok: false, message: "Selecione um arquivo de audio compativel." };
  }

  const originalFileName = sanitizeFileName(path.basename(selectedPath));
  pendingSelections.set(box.id, { selectedPath, originalFileName });

  return {
    ok: true,
    box: {
      ...boxToClient(box, fileExists(box.internalFilePath)),
      displayName: originalFileName,
      originalFileName,
      hasPendingAudio: true,
      statusMessage: "Selecionado - salvar",
    },
  };
});

ipcMain.handle("boxplay:save-audio", async (_event, boxId) => {
  const state = await readState();
  const box = findBox(state, boxId);
  if (!box) return { ok: false, message: "Box nao encontrado." };
  if (box.isLocked) return { ok: false, message: "Box bloqueado." };

  const pendingSelection = pendingSelections.get(box.id);
  if (!pendingSelection) return { ok: false, message: "Nenhum audio selecionado." };

  await ensureStorage();
  const { audioDirectory } = getStoragePaths();
  const extension = path.extname(pendingSelection.originalFileName).toLowerCase() || ".mp3";
  const destinationName = `box-${String(box.id).padStart(2, "0")}-${Date.now()}${extension}`;
  const destinationPath = path.join(audioDirectory, destinationName);

  await fsp.copyFile(pendingSelection.selectedPath, destinationPath);
  pendingSelections.delete(box.id);

  const updatedBox = {
    ...box,
    displayName: pendingSelection.originalFileName,
    originalFileName: pendingSelection.originalFileName,
    internalFilePath: destinationPath,
    updatedAtEpochMillis: Date.now(),
  };
  state.boxes = state.boxes.map((currentBox) => currentBox.id === updatedBox.id ? updatedBox : currentBox);
  await writeState(state);
  await deletePreviousAudio(box.internalFilePath, destinationPath);

  return { ok: true, box: boxToClient(updatedBox, true) };
});

ipcMain.handle("boxplay:set-volume", async (_event, boxId, volume) => {
  const state = await readState();
  const box = findBox(state, boxId);
  if (!box) return { ok: false, message: "Box nao encontrado." };
  if (box.isLocked) return { ok: false, message: "Box bloqueado." };

  const updatedBox = {
    ...box,
    volume: clampVolume(volume),
    updatedAtEpochMillis: Date.now(),
  };
  state.boxes = state.boxes.map((currentBox) => currentBox.id === updatedBox.id ? updatedBox : currentBox);
  await writeState(state);

  return { ok: true, box: boxToClient(updatedBox, fileExists(updatedBox.internalFilePath)) };
});

ipcMain.handle("boxplay:toggle-lock", async (_event, boxId) => {
  const state = await readState();
  const box = findBox(state, boxId);
  if (!box) return { ok: false, message: "Box nao encontrado." };

  const updatedBox = {
    ...box,
    isLocked: !box.isLocked,
    updatedAtEpochMillis: Date.now(),
  };
  state.boxes = state.boxes.map((currentBox) => currentBox.id === updatedBox.id ? updatedBox : currentBox);
  await writeState(state);

  return { ok: true, box: boxToClient(updatedBox, fileExists(updatedBox.internalFilePath)) };
});

ipcMain.handle("boxplay:open-storage-folder", async () => {
  await ensureStorage();
  await shell.openPath(getStoragePaths().userData);
  return { ok: true };
});
