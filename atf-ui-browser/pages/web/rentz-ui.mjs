export function rentzArrivalView(state, arrival, language, autoStepAction = null) {
  const match = state?.view;
  if (!match?.score || !Array.isArray(match.seats) || !Array.isArray(arrival?.previousTotals)
      || arrival.previousTotals.length !== match.seats.length || !Number.isInteger(arrival.unplayedCards)
      || !Number.isInteger(arrival.durationMillis) || arrival.durationMillis <= 0) {
    throw new Error("Rentz arrival payload does not match the settled public view.");
  }
  return {
    ...state,
    view: {
      ...match,
      prompt: "",
      actions: autoStepAction?.key === "auto-step" ? [autoStepAction] : [],
      seats: match.seats.map((seat, index) => ({
        ...seat,
        firstFact: `Δ: …`,
        secondFact: `Σ: ${arrival.previousTotals[index]}`,
        active: false,
      })),
      score: null,
      scoreDetails: null,
      rentzArrival: { ...arrival, language },
    },
  };
}

export async function presentRentzArrival(result, language, { render, wait, stopAutomation, autoStepAction }) {
  if (!result?.rentzArrival) return false;
  stopAutomation();
  try {
    await render(rentzArrivalView(result, result.rentzArrival, language, autoStepAction));
    await wait(result.rentzArrival.durationMillis);
  } finally {
    await render(result);
  }
  return true;
}

function rentzPlaybackView(result, snapshot, activeSeat = null, autoStepAction = null) {
  const view = snapshot;
  if (!view || !Array.isArray(view.seats) || !Array.isArray(view.rentzSequence)
      || !Array.isArray(view.hand)) {
    throw new Error("Rentz playback snapshot is incomplete or exposes no public table state.");
  }
  const autoStep = [...(view.actions ?? []), ...(result?.view?.actions ?? []), autoStepAction]
    .find(action => action?.key === "auto-step");
  return {
    ...result,
    view: {
      ...view,
      // The shared shell renders the persistent auto-play checkbox from this action marker.
      // Keep only that marker during playback; card actions stay hidden until the replay ends.
      actions: autoStep ? [autoStep] : [],
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

function rentzPlaybackSnapshot(snapshot, frameIndex, side) {
  const fail = reason => { throw new Error(`Rentz playback frame ${frameIndex} ${side}: ${reason}.`); };
  if (!snapshot || typeof snapshot !== "object" || !Array.isArray(snapshot.seats)
      || !Array.isArray(snapshot.hand) || !Array.isArray(snapshot.rentzSequence)) {
    fail("public table snapshot is incomplete");
  }
  if (snapshot.seats.length < 3 || snapshot.seats.length > 6
      || snapshot.seats.some(seat => !seat || typeof seat.active !== "boolean")) {
    fail("seat projection is outside the Rentz table contract");
  }
  if (snapshot.rentzSequence.length !== 4) fail("sequence must contain four suit rows");
  if (containsPrivateHands(snapshot)) fail("snapshot contains a private opponent-hand field");

  const hand = snapshot.hand.map(card => {
    if (!card || typeof card.label !== "string" || card.label.length === 0) {
      fail("private hand contains a card without a public label");
    }
    return card.label;
  });
  if (new Set(hand).size !== hand.length) fail("private hand contains duplicate cards");

  const slots = [];
  const played = new Set();
  const identities = new Set();
  let width = -1;
  for (const row of snapshot.rentzSequence) {
    if (!row || !Array.isArray(row.cells) || row.cells.length === 0) fail("sequence row has no card slots");
    if (width < 0) width = row.cells.length;
    else if (row.cells.length !== width) fail("sequence rows have different widths");
    for (const cell of row.cells) {
      if (!cell || typeof cell.label !== "string" || typeof cell.played !== "boolean") {
        fail("sequence slot has an invalid card projection");
      }
      if (identities.has(cell.label)) {
        fail("sequence contains a duplicate card identity");
      }
      identities.add(cell.label);
      const slot = { label: cell.label, played: cell.played };
      slots.push(slot);
      if (cell.played) played.add(cell.label);
    }
  }
  if (hand.some(label => played.has(label))) fail("a card is both private and on the public sequence");
  return { seats: snapshot.seats, hand, slots, played };
}

const privateCardCollectionFields = new Set([
  "hands", "opponenthand", "opponenthands", "privatehand", "privatehands", "hiddenhand", "hiddenhands",
  "concealedhand", "concealedhands", "otherhand", "otherhands", "playerhand", "playerhands",
  "handbyseat", "handsbyseat", "cardsbyseat", "cardsbyplayer", "opponentcards", "playercards",
]);

function containsPrivateHands(value, visited = new WeakSet()) {
  if (!value || typeof value !== "object") return false;
  if (visited.has(value)) return false;
  visited.add(value);
  if (Array.isArray(value)) return value.some(item => containsPrivateHands(item, visited));
  return Object.entries(value).some(([key, item]) => privateCardCollectionFields.has(key.toLowerCase().replace(/[^a-z0-9]/g, ""))
    || containsPrivateHands(item, visited));
}

function validateRentzPlaybackFrame(frame, index) {
  const fail = reason => { throw new Error(`Rentz playback frame ${index}: ${reason}.`); };
  if (!frame || !Number.isInteger(frame.actorSeat) || !Number.isInteger(frame.delayMillis) || frame.delayMillis < 0) {
    fail("actor or cadence is invalid");
  }
  const before = rentzPlaybackSnapshot(frame.before, index, "before");
  const after = rentzPlaybackSnapshot(frame.after, index, "after");
  if (frame.actorSeat >= before.seats.length || after.seats.length !== before.seats.length) {
    fail("actor does not belong to the projected table");
  }
  const activeBefore = before.seats.flatMap((seat, seatIndex) => seat.active ? [seatIndex] : []);
  if (activeBefore.length !== 1 || activeBefore[0] !== frame.actorSeat) {
    fail("pre-play active seat does not match the actor");
  }
  if (after.seats.filter(seat => seat.active).length > 1) fail("post-play snapshot marks multiple active seats");
  if (frame.actorSeat > 0 && frame.delayMillis === 0) fail("automated opponent has no visible-turn delay");
  if (before.slots.length !== after.slots.length
      || before.slots.some((slot, slotIndex) => slot.label !== after.slots[slotIndex].label)) {
    fail("Rentz card slots changed identity during playback");
  }

  const newlyPlayed = [...after.played].filter(label => !before.played.has(label));
  if (newlyPlayed.length !== 1 || after.played.size !== before.played.size + 1) {
    fail("one action must place exactly one new card on the sequence");
  }
  if ([...before.played].some(label => !after.played.has(label))) fail("a played card disappeared from the sequence");
  if (after.hand.some(label => after.played.has(label))) fail("a played card remains in the private hand");

  const removedFromHand = before.hand.filter(label => !after.hand.includes(label));
  if (frame.actorSeat === 0) {
    if (removedFromHand.length !== 1 || removedFromHand[0] !== newlyPlayed[0]
        || after.hand.length !== before.hand.length - 1
        || after.hand.some(label => !before.hand.includes(label))) {
      fail("the human card leaving the hand does not match the card placed on the table");
    }
  } else if (removedFromHand.length !== 0 || before.hand.length !== after.hand.length
      || before.hand.some(label => !after.hand.includes(label))) {
    fail("an opponent action changed the human hand");
  }
}

/** Replays public Rentz card placements from the same turn snapshots used by the Android session. */
export async function presentRentzPlayback(result, { render, wait, nextFrame, autoStepAction }) {
  const frames = result?.rentzPlayback;
  if (!Array.isArray(frames) || frames.length === 0) return false;
  frames.forEach(validateRentzPlaybackFrame);
  for (const frame of frames) {
    if (frame.delayMillis > 0) {
      await render(rentzPlaybackView(result, frame.before, frame.actorSeat, autoStepAction));
      await nextFrame();
      await wait(frame.delayMillis);
    }
    await render(rentzPlaybackView(result, frame.after, null, autoStepAction));
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

/** Preserves the common four-seat arrangement while reserving a larger card lane between seats. */
export function rentzCenterInsets(players) {
  if (!Number.isInteger(players) || players < 3 || players > 6) {
    throw new RangeError("Rentz table needs 3–6 players.");
  }
  return players === 4 ? { top: 58, bottom: 58 } : { top: 72, bottom: 67 };
}

/** Lowers the four-seat Rentz sequence only by the lower slack left in its measured lane. */
export function rentzSequenceLowerShift(players, laneHeight, panelHeight, preferred = 24) {
  if (!Number.isInteger(players) || players < 3 || players > 6) {
    throw new RangeError("Rentz table needs 3–6 players.");
  }
  if (![laneHeight, panelHeight, preferred].every(Number.isFinite) || laneHeight < 0 || panelHeight < 0 || preferred < 0) {
    throw new RangeError("Rentz sequence dimensions must be finite and non-negative.");
  }
  if (players !== 4) return 0;
  return Math.min(preferred, Math.max(0, Math.floor((laneHeight - panelHeight) / 2)));
}

/** Applies one measured native-compatible layout to the four sequence rows. */
export function applyRentzSequenceLayout(panel, width, height, fontScale = 1, laneHeight = height, panelHeight = height) {
  const model = rentzSequenceModels.get(panel);
  if (!model) throw new TypeError("Rentz sequence panel was not created by rentzSequence.");
  const metrics = rentzSequenceLayout(model.players, width, height, fontScale);
  panel.style.setProperty("--rentz-sequence-lower-shift",
    `${rentzSequenceLowerShift(model.players, laneHeight, panelHeight)}px`);
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

/** Measures the prepared board after the shared shell has assigned its final card lane. */
export function refreshRentzSequence(panel) {
  if (panel) rentzSequenceModels.get(panel)?.measure();
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
  const model = { players, rows: rowNodes };
  rentzSequenceModels.set(panel, model);
  const measure = () => {
    const bounds = panel.getBoundingClientRect?.();
    if (!bounds || bounds.width < 1 || bounds.height < 1) return;
    const laneBounds = panel.parentElement?.getBoundingClientRect?.();
    // The shared shell scales the whole stage. DOMRect is already scaled; CSS metrics are not.
    const width = panel.clientWidth ?? bounds.width;
    const panelHeight = panel.clientHeight ?? bounds.height;
    const laneHeight = panel.parentElement?.clientHeight ?? laneBounds?.height ?? panelHeight;
    // A delayed observer can still report the previous 248px panel inside a shorter lane.
    const height = Math.min(panelHeight, laneHeight);
    const footer = summary?.offsetHeight ?? summary?.getBoundingClientRect?.().height ?? 0;
    const textScale = Number.parseFloat(globalThis.getComputedStyle?.(panel)
      ?.getPropertyValue("--whist-text-scale")) || 1;
    applyRentzSequenceLayout(panel, Math.floor(width), Math.max(1, Math.floor(height - footer)), textScale,
      Math.floor(laneHeight), Math.floor(height));
  };
  model.measure = measure;
  if (typeof ResizeObserver === "function") {
    const observer = new ResizeObserver(measure);
    observer.observe(panel);
    if (panel.parentElement) observer.observe(panel.parentElement);
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
