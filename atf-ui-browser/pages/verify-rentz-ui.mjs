import assert from "node:assert/strict";
import { rentzChoiceDropdown, rentzSequence } from "./web/rentz-ui.mjs";

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
check("all four suit rows are rendered", 4, panel.children.length);
check("each row keeps its stable suit identity", ["0", "1", "2", "3"], panel.children.map(row => row.dataset.suitRow));
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
