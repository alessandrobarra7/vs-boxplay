const gridElement = document.querySelector("#box-grid");
const toastElement = document.querySelector("#toast");
const stopAllButton = document.querySelector("#stop-all-button");
const storageButton = document.querySelector("#storage-button");
const storagePathElement = document.querySelector("#storage-path");

const audioPlayers = new Map();
const runtimeStatus = new Map();
let boxes = [];
let toastTimer;

const icons = {
  upload: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3v12"></path><path d="m7 8 5-5 5 5"></path><path d="M5 21h14"></path></svg>',
  save: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 3h12l2 2v16H5z"></path><path d="M8 3v6h8V3"></path><path d="M8 16h8"></path></svg>',
  play: '<svg viewBox="0 0 24 24" aria-hidden="true"><path class="filled" d="M8 5v14l11-7z"></path></svg>',
  pause: '<svg viewBox="0 0 24 24" aria-hidden="true"><path class="filled" d="M7 5h4v14H7z"></path><path class="filled" d="M13 5h4v14h-4z"></path></svg>',
  restart: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 12a8 8 0 1 0 2.4-5.7"></path><path d="M4 4v6h6"></path></svg>',
  lock: '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="5" y="10" width="14" height="10" rx="2"></rect><path d="M8 10V7a4 4 0 0 1 8 0v3"></path></svg>',
  unlock: '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="5" y="10" width="14" height="10" rx="2"></rect><path d="M8 10V7a4 4 0 0 1 7.6-1.7"></path></svg>',
  minus: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14"></path></svg>',
  plus: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 5v14"></path><path d="M5 12h14"></path></svg>',
  stop: '<svg viewBox="0 0 24 24" aria-hidden="true"><rect class="filled" x="7" y="7" width="10" height="10" rx="2"></rect></svg>',
  folder: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 7h7l2 2h9v10H3z"></path></svg>',
  wave: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 13V9"></path><path d="M8 17V5"></path><path d="M12 20V2"></path><path d="M16 17V5"></path><path d="M20 13V9"></path></svg>',
};

function escapeHtml(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

function clampVolume(value) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return 0.8;
  return Math.min(1, Math.max(0, parsed));
}

function findBox(boxId) {
  return boxes.find((box) => box.id === Number(boxId));
}

function replaceBox(updatedBox) {
  boxes = boxes.map((box) => box.id === updatedBox.id ? updatedBox : box);
  render();
}

function isPlaying(boxId) {
  const audio = audioPlayers.get(Number(boxId));
  return Boolean(audio && !audio.paused && !audio.ended);
}

function statusFor(box) {
  return runtimeStatus.get(box.id) || box.statusMessage || (box.hasSavedAudio ? "Salvo" : "Vazio");
}

function showToast(message) {
  if (!message) return;
  toastElement.textContent = message;
  toastElement.classList.add("show");
  window.clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => toastElement.classList.remove("show"), 3200);
}

function setStatus(boxId, message) {
  runtimeStatus.set(Number(boxId), message);
  render();
}

function clearStatus(boxId) {
  runtimeStatus.delete(Number(boxId));
  render();
}

function destroyAudio(boxId) {
  const id = Number(boxId);
  const audio = audioPlayers.get(id);
  if (!audio) return;
  audio.pause();
  audio.removeAttribute("src");
  audio.load();
  audioPlayers.delete(id);
}

function getAudio(box) {
  const currentAudio = audioPlayers.get(box.id);
  if (currentAudio && currentAudio.dataset.src === box.audioSrc) {
    currentAudio.volume = clampVolume(box.volume);
    return currentAudio;
  }

  destroyAudio(box.id);

  const audio = new Audio(box.audioSrc);
  audio.dataset.src = box.audioSrc;
  audio.preload = "auto";
  audio.volume = clampVolume(box.volume);
  audio.addEventListener("ended", () => {
    audio.currentTime = 0;
    setStatus(box.id, "Pronto");
  });
  audio.addEventListener("error", () => {
    setStatus(box.id, "Erro ao tocar");
    showToast(`Nao foi possivel tocar o audio do Box ${box.id}.`);
  });
  audioPlayers.set(box.id, audio);
  return audio;
}

function actionButton(action, box, icon, label, variant, disabled = false) {
  return `
    <button class="icon-button ${variant}" type="button" data-action="${action}" data-box-id="${box.id}" aria-label="${escapeHtml(label)}" title="${escapeHtml(label)}" ${disabled ? "disabled" : ""}>
      ${icon}
    </button>
  `;
}

function renderWaveform(box) {
  const bars = Array.from({ length: 22 }, (_, index) => {
    const level = 1 + ((index * 17 + box.id * 11) % 6);
    return `<span class="level-${level}"></span>`;
  }).join("");
  return `<div class="waveform ${box.hasSavedAudio || box.hasPendingAudio ? "" : "dimmed"}" aria-hidden="true">${bars}</div>`;
}

function renderCard(box) {
  const locked = Boolean(box.isLocked);
  const saved = Boolean(box.hasSavedAudio && box.audioSrc);
  const pending = Boolean(box.hasPendingAudio);
  const playing = isPlaying(box.id);
  const status = statusFor(box);
  const volume = Math.round(clampVolume(box.volume) * 100);

  return `
    <article class="audio-card ${locked ? "locked" : ""} ${playing ? "playing" : ""}" data-box-id="${box.id}">
      <header class="card-header">
        <div class="title-block">
          <h2>${box.id}. ${escapeHtml(box.displayName)}</h2>
          <p>${escapeHtml(status)}</p>
        </div>
        ${actionButton("lock", box, locked ? icons.lock : icons.unlock, locked ? "Desbloquear box" : "Bloquear box", "ghost")}
      </header>

      ${renderWaveform(box)}

      <div class="controls-row">
        ${actionButton("upload", box, icons.upload, "Enviar audio", "light", locked)}
        ${actionButton("save", box, icons.save, "Salvar audio", "light", locked || !pending)}
        ${actionButton("play", box, playing ? icons.pause : icons.play, playing ? "Pausar" : "Tocar", "primary", !saved)}
        ${actionButton("restart", box, icons.restart, "Reiniciar", "ghost", !saved)}
      </div>

      <div class="volume-row">
        ${actionButton("volume-down", box, icons.minus, "Diminuir volume", "ghost small", locked || volume <= 0)}
        <div class="volume-track" aria-label="Volume ${volume} por cento">
          <span class="fill width-${Math.round(volume / 10) * 10}"></span>
        </div>
        <strong>${volume}%</strong>
        ${actionButton("volume-up", box, icons.plus, "Aumentar volume", "ghost small", locked || volume >= 100)}
      </div>
    </article>
  `;
}

function render() {
  gridElement.innerHTML = boxes.map(renderCard).join("");
}

async function handleUpload(boxId) {
  const result = await window.boxplayApi.selectAudio(boxId);
  if (!result.ok) {
    if (!result.canceled) showToast(result.message || "Selecao cancelada.");
    return;
  }
  runtimeStatus.set(boxId, "Selecionado - salvar");
  replaceBox(result.box);
}

async function handleSave(boxId) {
  const box = findBox(boxId);
  if (!box) return;
  destroyAudio(boxId);
  setStatus(boxId, "Salvando...");
  const result = await window.boxplayApi.saveAudio(boxId);
  if (!result.ok) {
    showToast(result.message || "Nao foi possivel salvar o audio.");
    setStatus(boxId, box.statusMessage || "Erro");
    return;
  }
  runtimeStatus.delete(boxId);
  replaceBox(result.box);
}

async function handleTogglePlay(boxId) {
  const box = findBox(boxId);
  if (!box || !box.hasSavedAudio || !box.audioSrc) return;

  const audio = getAudio(box);
  if (!audio.paused && !audio.ended) {
    audio.pause();
    setStatus(boxId, "Pausado");
    return;
  }

  try {
    if (audio.ended) audio.currentTime = 0;
    await audio.play();
    setStatus(boxId, "Tocando");
  } catch (error) {
    setStatus(boxId, "Erro ao tocar");
    showToast(error.message || "Nao foi possivel tocar o audio.");
  }
}

async function handleRestart(boxId) {
  const box = findBox(boxId);
  if (!box || !box.hasSavedAudio || !box.audioSrc) return;

  const audio = getAudio(box);
  const shouldContinuePlaying = !audio.paused && !audio.ended;
  audio.currentTime = 0;

  if (!shouldContinuePlaying) {
    setStatus(boxId, "Pronto");
    return;
  }

  try {
    await audio.play();
    setStatus(boxId, "Tocando");
  } catch (error) {
    setStatus(boxId, "Erro ao tocar");
    showToast(error.message || "Nao foi possivel reiniciar o audio.");
  }
}

async function handleVolume(boxId, direction) {
  const box = findBox(boxId);
  if (!box || box.isLocked) return;

  const nextVolume = clampVolume(box.volume + direction * 0.1);
  const previousBox = { ...box };
  replaceBox({ ...box, volume: nextVolume });

  const audio = audioPlayers.get(boxId);
  if (audio) audio.volume = nextVolume;

  const result = await window.boxplayApi.setVolume(boxId, nextVolume);
  if (!result.ok) {
    showToast(result.message || "Nao foi possivel alterar o volume.");
    replaceBox(previousBox);
    return;
  }
  replaceBox(result.box);
}

async function handleLock(boxId) {
  const result = await window.boxplayApi.toggleLock(boxId);
  if (!result.ok) {
    showToast(result.message || "Nao foi possivel alterar o cadeado.");
    return;
  }
  runtimeStatus.delete(boxId);
  replaceBox(result.box);
}

function stopAll() {
  audioPlayers.forEach((audio, boxId) => {
    audio.pause();
    audio.currentTime = 0;
    const box = findBox(boxId);
    if (box && box.hasSavedAudio) runtimeStatus.set(boxId, "Pronto");
  });
  render();
}

gridElement.addEventListener("click", async (event) => {
  const button = event.target.closest("button[data-action]");
  if (!button || button.disabled) return;

  const boxId = Number(button.dataset.boxId);
  const action = button.dataset.action;

  try {
    if (action === "upload") await handleUpload(boxId);
    if (action === "save") await handleSave(boxId);
    if (action === "play") await handleTogglePlay(boxId);
    if (action === "restart") await handleRestart(boxId);
    if (action === "volume-down") await handleVolume(boxId, -1);
    if (action === "volume-up") await handleVolume(boxId, 1);
    if (action === "lock") await handleLock(boxId);
  } catch (error) {
    showToast(error.message || "Acao nao concluida.");
  }
});

stopAllButton.addEventListener("click", stopAll);
storageButton.addEventListener("click", async () => {
  const result = await window.boxplayApi.openStorageFolder();
  if (!result.ok) showToast("Nao foi possivel abrir a pasta do BOXPLAY.");
});

window.addEventListener("beforeunload", () => {
  audioPlayers.forEach((audio) => audio.pause());
});

async function initialize() {
  const state = await window.boxplayApi.getState();
  boxes = state.boxes;
  storagePathElement.textContent = state.storagePath || "";
  render();
}

initialize().catch((error) => {
  showToast(error.message || "Nao foi possivel iniciar o BOXPLAY.");
});
