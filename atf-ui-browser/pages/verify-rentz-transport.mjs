import assert from "node:assert/strict";
import { localTransport } from "./web/local-transport.mjs";

const arrival = { winner: "Ana", unplayedCards: 5, previousTotals: [25, -10, 40], durationMillis: 1800 };
const replies = [
  { result: { view: { score: { title: "Hand result" } } }, rentzArrival: arrival },
  { result: { view: { score: { title: "Other contract" } } } },
];
class FakeWorker {
  listeners = new Map();
  addEventListener(name, listener) { this.listeners.set(name, listener); }
  postMessage({ id }) { this.listeners.get("message")({ data: { id, ...replies.shift() } }); }
}

Object.defineProperty(globalThis, "Worker", { configurable: true, value: FakeWorker });
Object.defineProperty(globalThis, "localStorage", {
  configurable: true,
  value: { getItem: () => null, setItem: () => {} },
});
if (!globalThis.navigator) Object.defineProperty(globalThis, "navigator", { configurable: true, value: {} });
if (globalThis.navigator?.locks) Object.defineProperty(globalThis.navigator, "locks", { configurable: true, value: undefined });

const transport = localTransport();
const rentzResult = await transport.request("/api/action", { action: "card:SPADES:11" });
assert.deepEqual(rentzResult.rentzArrival, arrival, "local transport carries the transient Rentz arrival into the renderer state");
const ordinaryResult = await transport.request("/api/action", { action: "next" });
assert.equal(Object.hasOwn(ordinaryResult, "rentzArrival"), false, "ordinary results do not inherit a previous arrival marker");

console.log("Rentz browser transport checks passed: 2");
