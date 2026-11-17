import assert from "node:assert/strict";
import { presentRentzArrival, rentzArrivalView, rentzChoiceDropdown, rentzSequence } from "./web/rentz-ui.mjs";

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
}

let checks = 0;
const check = (message, expected, actual) => {
  assert.deepEqual(actual, expected, message);
  checks++;
};
const element = (tag, text, className) => new FakeElement(tag, text, className);
const rows = ["♣", "♦", "♥", "♠"].map((suit, suitIndex) => ({
  suit,
  cells: [
    { label: `J${suit}`, legal: suitIndex === 0, action: suitIndex === 0 ? `card:J${suit}` : "", opening: true, played: false },
    { label: `Q${suit}`, legal: false, action: "", opening: false, played: suitIndex === 1 },
    { label: `K${suit}`, legal: suitIndex === 3, action: suitIndex === 3 ? `card:K${suit}` : "", opening: false, played: false },
  ],
}));
const actions = [];
const panel = rentzSequence(rows, element, action => actions.push(action), "ro");

check("sequence is an accessible group", "group", panel.attributes.role);
check("sequence uses the Romanian accessible name", "Șirurile Rentz", panel.attributes["aria-label"]);
check("all four suit rows are rendered", 4, panel.children.filter(row => row.className === "rentz-sequence-row").length);
check("each row keeps its stable suit identity", ["0", "1", "2", "3"],
  panel.children.filter(row => row.className === "rentz-sequence-row").map(row => row.dataset.suitRow));
check("only two legal cards become buttons", 2,
  panel.children.flatMap(row => row.children).filter(child => child.tagName === "button").length);
check("played card stays visible as a tile", 1,
  panel.children[1].children.filter(child => child.className.includes("played")).length);
check("opening card is marked", true, panel.children[0].children.some(child => child.className.includes("opening")));
check("legal card gets exact action id", "card:J♣", panel.children[0].children[1].dataset.action);
panel.children[0].children[1].click();
panel.children[3].children[3].click();
check("click dispatches only the selected legal cards", ["card:J♣", "card:K♠"], actions);
check("red suits get red card styling", true, panel.children[1].children[1].className.includes("red"));
check("black suits get black card styling", true, panel.children[0].children[1].className.includes("black"));

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
const arrivalBoard = rentzSequence(rows, element, () => {}, "ro", { ...arrival, language: "ro" });
const arrivalPanel = arrivalBoard.children.at(-1);
check("arrival is an accessible live status", "status", arrivalPanel.attributes.role);
check("arrival announces politely without stealing focus", "polite", arrivalPanel.attributes["aria-live"]);
check("arrival identifies the finisher", "Sosire Rentz · Ana", arrivalPanel.children[0].textContent);
check("arrival reports unplayed cards", "Cărți neplasate · 7", arrivalPanel.children[1].textContent);
const englishBoard = rentzSequence(rows, element, () => {}, "en", { ...arrival, language: "en" });
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
