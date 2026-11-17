import assert from "node:assert/strict";
import { presentRentzArrival, rentzArrivalView, rentzChoiceDropdown, rentzSequence,
  rentzSequenceLayout, applyRentzSequenceLayout } from "./web/rentz-ui.mjs";
import { readFileSync } from "node:fs";

class FakeElement {
  constructor(tag, text = "", className = "") {
    this.tagName = tag;
    this.textContent = text;
    this.className = className;
    this.children = [];
    this.attributes = {};
    this.dataset = {};
    this.style = { properties: {}, setProperty: (key, value) => { this.style.properties[key] = value; } };
    this.listeners = {};
  }
  append(child) { this.children.push(child); }
  setAttribute(key, value) { this.attributes[key] = value; }
  addEventListener(name, listener) { this.listeners[name] = listener; }
  click() { this.listeners.click?.(); }
  setBounds(width, height) { this.bounds = { width, height }; }
  getBoundingClientRect() { return this.bounds ?? { width: 0, height: 0 }; }
}
class FakeResizeObserver {
  static instances = [];
  constructor(callback) { this.callback = callback; FakeResizeObserver.instances.push(this); }
  observe(target) { this.target = target; }
  trigger() { this.callback([{ target: this.target }]); }
}
globalThis.ResizeObserver = FakeResizeObserver;

let checks = 0;
const check = (message, expected, actual) => {
  assert.deepEqual(actual, expected, message);
  checks++;
};
const element = (tag, text, className) => new FakeElement(tag, text, className);
const rows = ["♣", "♦", "♥", "♠"].map((suit, suitIndex) => ({
  suit,
  cells: ["9", "10", "J", "Q", "K", "A"].map((rank, column) => ({
    label: `${rank}${suit}`, legal: (suitIndex === 0 && column === 3) || (suitIndex === 3 && column === 4),
    action: suitIndex === 0 && column === 3 ? `card:Q${suit}`
      : suitIndex === 3 && column === 4 ? `card:K${suit}` : "",
    opening: column === 3, played: (suitIndex === 1 && column === 2) || (suitIndex === 3 && column === 5),
  })),
}));
const panel = rentzSequence(rows, element, "ro");
const sequenceObserver = FakeResizeObserver.instances.at(-1);
check("sequence observes its display container for responsive layout", true, sequenceObserver.target === panel);
check("played Rentz card starts with its full card label before viewport sizing", "J♦", panel.children[1].children[2].textContent);
panel.setBounds(372, 640);
sequenceObserver.trigger();
check("resize observer applies native geometry after the panel is measured", "true", panel.dataset.layoutReady);
check("resize observer adopts the measured desktop-width card band", `${rentzSequenceLayout(3, 372, 640).rowHeight}px`,
  panel.children[0].style.height);
check("measured layout stacks the played rank and suit like Android", "J\n♦", panel.children[1].children[2].textContent);
panel.setBounds(280, 168);
sequenceObserver.trigger();
check("resize observer recalculates geometry when the viewport becomes compact", `${rentzSequenceLayout(3, 280, 168).rowHeight}px`,
  panel.children[0].style.height);

check("sequence is an accessible group", "group", panel.attributes.role);
check("sequence uses the Romanian accessible name", "Șirurile Rentz", panel.attributes["aria-label"]);
check("all four suit rows are rendered", 4, panel.children.filter(row => row.className === "rentz-sequence-row").length);
check("each row keeps its stable suit identity", ["0", "1", "2", "3"],
  panel.children.filter(row => row.className === "rentz-sequence-row").map(row => row.dataset.suitRow));
check("sequence cards remain display-only like the native Android board", 0,
  panel.children.flatMap(row => row.children).filter(child => child.tagName === "button").length);
check("played card stays visible as a tile", 1,
  panel.children[1].children.filter(child => child.className.includes("played")).length);
check("opening card is marked", true, panel.children[0].children.some(child => child.className.includes("opening")));
check("sequence adds no suit badge absent from the native board", false,
  panel.children.some(child => child.className === "rentz-suit-symbol"));
check("opening slot shows only its rank before it is played", "Q", panel.children[0].children[3].textContent);
check("opening card is not given a separate clickable action", false,
  panel.children[0].children[3].attributes.role === "button");
check("red suits get red card styling", true, panel.children[1].children[2].className.includes("red"));
check("black suits get black card styling", true, panel.children[3].children[5].className.includes("black"));

const nativeMetrics = readFileSync(process.argv[2], "utf8").trim().split(/\r?\n/).map(line => {
  const [players, width, height, fontScale, rowHeight, faceHeight, faceWidth, advance, start,
    indexWidth, indexPadding, labelSizePx, stackedIndex] = line.split("\t");
  return { players: Number(players), width: Number(width), height: Number(height), fontScale: Number(fontScale),
    rowHeight: Number(rowHeight), faceHeight: Number(faceHeight), faceWidth: Number(faceWidth),
    advance: Number(advance), start: Number(start), indexWidth: Number(indexWidth),
    indexPadding: Number(indexPadding), labelSizePx: Number(labelSizePx), stackedIndex: stackedIndex === "true" };
});
for (const expected of nativeMetrics) {
  const { players, width, height, fontScale, ...nativeLayout } = expected;
  const actualLayout = rentzSequenceLayout(players, width, height, fontScale);
  const { labelSizePx: nativeLabelSize, ...nativeIntegerLayout } = nativeLayout;
  const { labelSizePx: actualLabelSize, ...actualIntegerLayout } = actualLayout;
  nativeIntegerLayout.columns = players * 2;
  check(`sequence integer metrics match native RentzCardSpace at ${players}/${width}x${height}/${fontScale}`,
    nativeIntegerLayout, actualIntegerLayout);
  check(`sequence label metric matches native float precision at ${players}/${width}x${height}/${fontScale}`,
    true, Math.abs(nativeLabelSize - actualLabelSize) < 0.00001);
}
const denseRanks = ["3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"];
const denseRows = ["♣", "♦", "♥", "♠"].map((suit, suitIndex) => ({
  suit,
  cells: denseRanks.map((rank, column) => ({ label: `${rank}${suit}`, legal: false, action: "",
    opening: column === 6, played: suitIndex === 1 && column === 2 })),
}));
const densePanel = rentzSequence(denseRows, element, "ro");
const denseLayout = applyRentzSequenceLayout(densePanel, 280, 235, 1);
check("compact table uses the native overlapping card advance", true, denseLayout.advance < denseLayout.faceWidth);
check("played faces use the native card width", `${denseLayout.faceWidth}px`,
  densePanel.children[1].children[2].style.width);
const nativeRightPadding = denseLayout.indexPadding + denseLayout.faceWidth - denseLayout.indexWidth;
check("overlapped card labels reserve the same visible index area as Android",
  `${denseLayout.indexPadding}px ${nativeRightPadding}px ${denseLayout.indexPadding}px ${denseLayout.indexPadding}px`,
  densePanel.children[1].children[2].style.padding);
check("opening rank remains plain text rather than a fabricated card face", "9",
  densePanel.children[0].children[6].textContent);
check("covered empty slots disappear only when native cards overlap", "",
  densePanel.children[1].children[3].textContent);
const rentzCss = readFileSync(new URL("./web/rentz.css", import.meta.url), "utf8");
check("empty sequence slots use the Android sequence text color", true, rentzCss.includes("color: #c2d3c5;"));
check("black card faces use the Android card text color", true, rentzCss.includes("#2a352f"));
check("red card faces use the Android card text color", true, rentzCss.includes("#ad393a"));
check("Rentz arrival uses the Android idle-seat text color", true, rentzCss.includes("color: #d3efe2;"));
check("Rentz sequence indices keep Android's left edge alignment", true,
  rentzCss.includes("justify-content: flex-start;"));
check("Rentz sequence uses a condensed card-face font", true,
  rentzCss.includes("Roboto Condensed") && rentzCss.includes("Arial Narrow"));

const settled = {
  revision: 9,
  view: {
    score: { title: "Rezultatul mâinii" },
    scoreDetails: { summary: "Rentz · Mâna 1" },
    prompt: "Mână încheiată",
    actions: [{ key: "next", label: "Mâna următoare" }],
    seats: [
      { name: "Ana", firstFact: "Mână 8", secondFact: "Total 300", active: false },
      { name: "Mihai", firstFact: "Mână 8", secondFact: "Total -100", active: false },
      { name: "Ioana", firstFact: "Mână 8", secondFact: "Total 50", active: false },
    ],
  },
};
const arrival = { winner: "Ana", unplayedCards: 7, previousTotals: [100, -200, 50], durationMillis: 1800 };
const waiting = rentzArrivalView(settled, arrival, "ro");
check("arrival view suspends the score only in its render copy", null, waiting.view.score);
check("arrival view withholds the score details", null, waiting.view.scoreDetails);
check("arrival view hides next-hand actions", [], waiting.view.actions);
check("arrival view has no stale completion prompt", "", waiting.view.prompt);
check("arrival seats show pending hand points and pre-hand totals", [
  ["Mână …", "Total 100", false], ["Mână …", "Total -200", false], ["Mână …", "Total 50", false],
], waiting.view.seats.map(seat => [seat.firstFact, seat.secondFact, seat.active]));
check("original result remains available unchanged for the score screen", "Rezultatul mâinii", settled.view.score.title);
const arrivalBoard = rentzSequence(rows, element, "ro", { ...arrival, language: "ro" });
const arrivalPanel = arrivalBoard.children.at(-1);
check("arrival is an accessible live status", "status", arrivalPanel.attributes.role);
check("arrival announces politely without stealing focus", "polite", arrivalPanel.attributes["aria-live"]);
check("arrival identifies the finisher", "Sosire Rentz · Ana", arrivalPanel.children[0].textContent);
check("arrival reports unplayed cards", "Cărți neplasate · 7", arrivalPanel.children[1].textContent);
const englishBoard = rentzSequence(rows, element, "en", { ...arrival, language: "en" });
check("arrival copy follows the selected language", "Rentz finish · Ana", englishBoard.children.at(-1).children[0].textContent);
check("arrival helper rejects a payload without prior totals", true, (() => {
  try { rentzArrivalView(settled, { ...arrival, previousTotals: [] }, "ro"); return false; }
  catch { return true; }
})());
const lifecycle = [];
const presented = await presentRentzArrival({ ...settled, rentzArrival: arrival }, "ro", {
  stopAutomation: () => lifecycle.push("stop"),
  render: async state => lifecycle.push(state.view.score ? "score" : "arrival"),
  wait: async duration => lifecycle.push(`wait:${duration}`),
});
check("arrival lifecycle is activated", true, presented);
check("arrival lifecycle disables auto, renders the pause, waits, then restores score",
  ["stop", "arrival", "wait:1800", "score"], lifecycle);
const ignoredLifecycle = [];
check("ordinary result skips Rentz arrival lifecycle", false, await presentRentzArrival(
  { ...settled, rentzArrival: undefined }, "ro", {
    stopAutomation: () => ignoredLifecycle.push("stop"), render: async () => ignoredLifecycle.push("render"),
    wait: async () => ignoredLifecycle.push("wait"),
  }));
check("ordinary result has no transient rendering effects", [], ignoredLifecycle);

const selected = [];
const picker = rentzChoiceDropdown([
  { key: "choose:RENTZ", label: "Rentz" },
  { key: "blind:QUEENS", label: "Dame · pe nevăzute" },
], element, key => selected.push(key), "ro");
const select = picker.children.find(child => child.tagName === "select");
check("contract choices use a semantic labelled picker", "Alege sub-jocul", select.attributes["aria-label"]);
check("picker presents a placeholder before a choice", "Alege sub-jocul", select.children[0].textContent);
check("picker contains all available contracts", ["choose:RENTZ", "blind:QUEENS"],
  select.children.slice(1).map(option => option.value));
select.value = "blind:QUEENS";
select.listeners.change();
check("selecting a contract dispatches the matching native action once", ["blind:QUEENS"], selected);
check("no contract choices means no picker", null, rentzChoiceDropdown([], element, () => {}, "ro"));

console.log(`Rentz browser rendering checks passed: ${checks}`);
