import { initialize, commandLocal } from "./rentz-engine.mjs";

const seed = crypto.getRandomValues(new Int32Array(2));
initialize(seed[0], seed[1]);

globalThis.addEventListener("message", ({ data: { id, path, values, profile } }) => {
  try {
    const reply = JSON.parse(commandLocal(path, JSON.stringify(values), profile.name, profile.progress,
      profile.checkpoint, profile.rankings));
    globalThis.postMessage({ id, ...reply });
  } catch (failure) {
    console.error(failure);
    globalThis.postMessage({ id, status: 500, error: "Motorul Rentz nu a putut procesa comanda." });
  }
});
