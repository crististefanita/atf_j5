export function rentzArrivalView(state, arrival, language) {
  const match = state?.view;
  if (!match?.score || !Array.isArray(match.seats) || !Array.isArray(arrival?.previousTotals)
      || arrival.previousTotals.length !== match.seats.length || !Number.isInteger(arrival.unplayedCards)
      || !Number.isInteger(arrival.durationMillis) || arrival.durationMillis <= 0) {
    throw new Error("Rentz arrival payload does not match the settled public view.");
  }
  const hand = language === "en" ? "Hand" : "Mână";
  const total = language === "en" ? "Total" : "Total";
  return {
    ...state,
    view: {
      ...match,
      prompt: "",
      actions: [],
      seats: match.seats.map((seat, index) => ({
        ...seat,
        firstFact: `${hand} …`,
        secondFact: `${total} ${arrival.previousTotals[index]}`,
        active: false,
      })),
      score: null,
      scoreDetails: null,
      rentzArrival: { ...arrival, language },
    },
  };
}

export async function presentRentzArrival(result, language, { render, wait, stopAutomation }) {
  if (!result?.rentzArrival) return false;
  stopAutomation();
  try {
    await render(rentzArrivalView(result, result.rentzArrival, language));
    await wait(result.rentzArrival.durationMillis);
  } finally {
    await render(result);
  }
  return true;
}

function rentzPlaybackView(result, snapshot, activeSeat = null) {
  const view = snapshot;
  if (!view || !Array.isArray(view.seats) || !Array.isArray(view.rentzSequence)
      || !Array.isArray(view.hand)) {
    throw new Error("Rentz playback snapshot is incomplete or exposes no public table state.");
  }
  return {
    ...result,
    view: {
      ...view,
      actions: [],
      prompt: "",
      score: null,
      scoreDetails: null,
      seats: view.seats.map((seat, index) => ({
        ...seat,
        active: activeSeat === null ? seat.active : index === activeSeat,
      })),
    },
  };
}

/** Replays public Rentz card placements from the same turn snapshots used by the Android session. */
export async function presentRentzPlayback(result, { render, wait, nextFrame }) {
  const frames = result?.rentzPlayback;
  if (!Array.isArray(frames) || frames.length === 0) return false;
  for (const frame of frames) {
    if (!Number.isInteger(frame.actorSeat) || !Number.isInteger(frame.delayMillis) || frame.delayMillis < 0) {
      throw new Error("Rentz playback actor or cadence is invalid.");
    }
    if (frame.delayMillis > 0) {
      await render(rentzPlaybackView(result, frame.before, frame.actorSeat));
      await nextFrame();
      await wait(frame.delayMillis);
    }
    await render(rentzPlaybackView(result, frame.after));
  }
  return true;
}

export function rentzArrivalPanel(arrival, element, language) {
  if (!arrival || !Number.isInteger(arrival.unplayedCards) || !arrival.winner) return null;
  const panel = element("div", "", "rentz-arrival");
  panel.setAttribute("role", "status");
  panel.setAttribute("aria-live", "polite");
  panel.setAttribute("aria-atomic", "true");
  panel.append(element("strong", `${language === "en" ? "Rentz finish" : "Sosire Rentz"} · ${arrival.winner}`));
  panel.append(element("span", `${language === "en" ? "Unplayed cards" : "Cărți neplasate"} · ${arrival.unplayedCards}`));
  return panel;
}

const rentzSequenceModels = new WeakMap();

/** Mirrors RentzCardSpace.sequence with CSS-pixel density so WEB cards use the Android face geometry. */
export function rentzSequenceLayout(players, width, height, fontScale = 1) {
  if (!Number.isInteger(players) || players < 3 || players > 6
      || !Number.isFinite(width) || !Number.isFinite(height) || width < 1 || height < 1
      || !Number.isFinite(fontScale) || fontScale <= 0) {
    throw new RangeError("Invalid Rentz sequence viewport.");
  }
  const round = value => Math.floor(value + 0.5);
  const columns = 2 * players;
  const rowHeight = Math.min(round(62), Math.floor(height / 4));
  const shallow = rowHeight < 40;
  const faceHeight = rowHeight - Math.min(round(shallow ? 2 : 4), Math.floor(rowHeight / 6));
  const stackedIndex = faceHeight >= 24;
  const minimumIndex = Math.min(Math.floor(width / columns), round(20));
  const portraitWidth = Math.max(1, round(faceHeight * 5 / 7));
  const readableWidth = shallow
    ? Math.min(Math.floor(width / columns) + round(6), round(faceHeight * (stackedIndex ? 1.25 : 1.9))) : 0;
  const faceWidth = Math.min(Math.max(portraitWidth, readableWidth), width - (columns - 1) * minimumIndex);
  const advance = Math.min(faceWidth + round(3), Math.floor((width - faceWidth) / (columns - 1)));
  const start = Math.floor((width - faceWidth - (columns - 1) * advance) / 2);
  const indexWidth = Math.min(faceWidth, advance);
  const indexPadding = Math.min(round(2), Math.min(Math.floor(indexWidth / 8), Math.floor(faceHeight / 6)));
  const labelSizePx = Math.min(15 * fontScale,
    Math.min((indexWidth - 2 * indexPadding) / (stackedIndex ? 1.5 : 2.2),
      (faceHeight - 2 * indexPadding) / (stackedIndex ? 2.6 : 1.4)));
  return { columns, rowHeight, faceHeight, faceWidth, advance, start, indexWidth, indexPadding, labelSizePx, stackedIndex };
}

/** Applies one measured native-compatible layout to the four sequence rows. */
export function applyRentzSequenceLayout(panel, width, height, fontScale = 1) {
  const model = rentzSequenceModels.get(panel);
  if (!model) throw new TypeError("Rentz sequence panel was not created by rentzSequence.");
  const metrics = rentzSequenceLayout(model.players, width, height, fontScale);
  panel.dataset.layoutReady = "true";
  panel.dataset.layoutPlayers = String(model.players);
  panel.dataset.stackedIndex = String(metrics.stackedIndex);
  panel.style.setProperty("--rentz-columns", String(metrics.columns));
  panel.style.setProperty("--rentz-row-height", `${metrics.rowHeight}px`);
  panel.style.setProperty("--rentz-face-height", `${metrics.faceHeight}px`);
  panel.style.setProperty("--rentz-face-width", `${metrics.faceWidth}px`);
  panel.style.setProperty("--rentz-advance", `${metrics.advance}px`);
  panel.style.setProperty("--rentz-start", `${metrics.start}px`);
  panel.style.setProperty("--rentz-index-width", `${metrics.indexWidth}px`);
  panel.style.setProperty("--rentz-index-padding", `${metrics.indexPadding}px`);
  panel.style.setProperty("--rentz-label-size", `${metrics.labelSizePx}px`);
  for (const { data, node, tiles } of model.rows) {
    node.style.height = `${metrics.rowHeight}px`;
    for (const [column, tile] of tiles.entries()) {
      const cell = data.cells[column];
      const covered = column > 0 && data.cells[column - 1].played && metrics.advance < metrics.faceWidth;
      tile.style.position = "absolute";
      tile.style.left = `${metrics.start + column * metrics.advance}px`;
      tile.style.top = `${Math.floor((metrics.rowHeight - metrics.faceHeight) / 2)}px`;
      tile.style.width = `${cell.played ? metrics.faceWidth : metrics.indexWidth}px`;
      tile.style.height = `${metrics.faceHeight}px`;
      const rightPadding = metrics.indexPadding + (cell.played ? metrics.faceWidth - metrics.indexWidth : 0);
      tile.style.padding = `${metrics.indexPadding}px ${rightPadding}px ${metrics.indexPadding}px ${metrics.indexPadding}px`;
      tile.style.fontSize = `${metrics.labelSizePx}px`;
      tile.dataset.covered = String(covered && !cell.opening && !cell.played);
      if (cell.played) {
        const rank = cell.label.slice(0, -1);
        const suit = cell.label.slice(-1);
        tile.textContent = metrics.stackedIndex ? `${rank}\n${suit}` : cell.label;
      } else if (cell.opening) tile.textContent = cell.label.slice(0, -1);
      else tile.textContent = covered ? "" : "·";
    }
  }
  return metrics;
}

export function rentzSequence(rows, element, language, arrival = null) {
  if (!Array.isArray(rows) || rows.length !== 4 || !Array.isArray(rows[0]?.cells)
      || rows[0].cells.length % 2 !== 0 || rows.some(row => row.cells.length !== rows[0].cells.length)) {
    throw new TypeError("Rentz sequence needs four complete suit rows.");
  }
  const players = rows[0].cells.length / 2;
  if (players < 3 || players > 6) throw new RangeError("Rentz sequence needs 3–6 players.");
  const panel = element("div", "", "rentz-sequence");
  panel.setAttribute("role", "group");
  panel.setAttribute("aria-label", language === "en" ? "Rentz sequence board" : "Șirurile Rentz");
  panel.dataset.playerCount = String(players);
  const rowNodes = [];
  for (const [suitIndex, rowData] of rows.entries()) {
    const { cells } = rowData;
    const row = element("div", "", "rentz-sequence-row");
    row.dataset.suitRow = String(suitIndex);
    row.style.setProperty("--rentz-ranks", String(cells.length));
    const tiles = [];
    for (const cell of cells) {
      const color = cell.played
        ? (/[♥♦]$/.test(cell.label) ? " red" : " black") : "";
      const style = "rentz-sequence-cell" + color + (cell.played ? " played" : "")
        + (cell.opening ? " opening" : "");
      const tile = element("span", cell.played ? cell.label : (cell.opening ? cell.label.slice(0, -1) : "·"), style);
      tile.setAttribute("aria-label", cell.played ? cell.label
        : `${language === "en" ? "Empty slot " : "Loc liber "}${cell.label}`);
      tile.dataset.played = String(cell.played);
      tile.dataset.opening = String(cell.opening);
      row.append(tile);
      tiles.push(tile);
    }
    panel.append(row);
    rowNodes.push({ data: rowData, node: row, tiles });
  }
  const summary = rentzArrivalPanel(arrival, element, language);
  if (summary) panel.append(summary);
  rentzSequenceModels.set(panel, { players, rows: rowNodes });
  const measure = () => {
    const bounds = panel.getBoundingClientRect?.();
    if (!bounds || bounds.width < 1 || bounds.height < 1) return;
    const footer = summary?.getBoundingClientRect?.().height ?? 0;
    const textScale = Number.parseFloat(globalThis.getComputedStyle?.(panel)
      ?.getPropertyValue("--whist-text-scale")) || 1;
    applyRentzSequenceLayout(panel, Math.floor(bounds.width), Math.max(1, Math.floor(bounds.height - footer)), textScale);
  };
  if (typeof ResizeObserver === "function") {
    const observer = new ResizeObserver(measure);
    observer.observe(panel);
  } else if (typeof requestAnimationFrame === "function") requestAnimationFrame(measure);
  else if (typeof setTimeout === "function") setTimeout(measure, 0);
  return panel;
}
export function rentzChoiceDropdown(actions, element, play, language) {
  const choices = actions.filter(move => move.key.startsWith("choose:") || move.key.startsWith("blind:"));
  if (!choices.length) return null;

  const prompt = language === "en" ? "Choose a sub-game" : "Alege sub-jocul";
  const label = element("label", "", "rentz-contract-picker");
  label.append(element("span", language === "en" ? "Game" : "Sub-joc"));

  const select = element("select", "", "rentz-contract-select");
  select.setAttribute("aria-label", prompt);
  const placeholder = element("option", prompt);
  placeholder.value = "";
  placeholder.disabled = true;
  placeholder.selected = true;
  select.append(placeholder);

  for (const move of choices) {
    const option = element("option", move.label);
    option.value = move.key;
    select.append(option);
  }
  select.addEventListener("change", () => {
    if (choices.some(move => move.key === select.value)) play(select.value);
  });

  label.append(select);
  return label;
}
