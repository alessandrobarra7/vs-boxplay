const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("boxplayApi", {
  getState: () => ipcRenderer.invoke("boxplay:get-state"),
  selectAudio: (boxId) => ipcRenderer.invoke("boxplay:select-audio", boxId),
  saveAudio: (boxId) => ipcRenderer.invoke("boxplay:save-audio", boxId),
  setVolume: (boxId, volume) => ipcRenderer.invoke("boxplay:set-volume", boxId, volume),
  toggleLock: (boxId) => ipcRenderer.invoke("boxplay:toggle-lock", boxId),
  openStorageFolder: () => ipcRenderer.invoke("boxplay:open-storage-folder"),
});
