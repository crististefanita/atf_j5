import assert from "node:assert/strict";
import { pathToFileURL } from "node:url";

const engine = await import(pathToFileURL(process.argv[2]).href);
let checks = 0;
const nativeSuitOrder = { "♠": 0, "♥": 1, "♣": 2, "♦": 3 };
const nativeRank = label => ({ J: 11, Q: 12, K: 13, A: 14 }[label.slice(0, -1)] ?? Number(label.slice(0, -1)));

function nativeHandOrder(labels) {
  return [...labels].sort((left, right) => nativeSuitOrder[left.slice(-1)] - nativeSuitOrder[right.slice(-1)]
    || nativeRank(left) - nativeRank(right));
}

function check(description, condition) {
  assert.ok(condition, description);
  checks++;
}

for (let players = 3; players <= 6; players++) {
  for (const difficulty of ["NORMAL", "HARD"]) {
    engine.initialize(0, 20261116 + players * 10 + (difficulty === "HARD" ? 1 : 0));
    let profile = { name: null, progress: null, checkpoint: null, rankings: null };
    const call = (path, values = {}) => {
      const strings = Object.fromEntries(Object.entries(values).map(([key, value]) => [key, String(value)]));
      const response = JSON.parse(engine.commandLocal(path, JSON.stringify(strings), profile.name,
        profile.progress, profile.checkpoint, profile.rankings));
      if (!response.status) {
        for (const key of Object.keys(profile)) profile[key] = response[key] ?? null;
      }
      return response;
    };

    const setup = call("/api/setup");
    check(`${players}/${difficulty}: setup contract`, !setup.status && setup.result.fields.length > 0);
    const initial = call("/api/state");
    check(`${players}/${difficulty}: initial state`, !initial.status && initial.result.revision === 0);
    const started = call("/api/start", {
      players, mode: "RENTZ", difficulty, series: "5", streaks: "no", zero: "no",
      revision: initial.result.revision,
    });
    check(`${players}/${difficulty}: setup starts without a runtime error`, !started.status);
    let view = started.result.view;
    check(`${players}/${difficulty}: native session seat count`, view.seats.length === players);
    check(`${players}/${difficulty}: opponents remain hidden before reveal`, view.hand.length === 0);
    check(`${players}/${difficulty}: resumable browser checkpoint`, typeof started.checkpoint === "string" && started.checkpoint.length > 0);

    const renamed = call("/api/profile", { revision: started.result.revision, name: "  Jose\u0301  " });
    check(`${players}/${difficulty}: profile uses APK NFC normalization`, !renamed.status && renamed.name === "José");
    const rejectedName = call("/api/profile", { revision: renamed.result.revision, name: "Ana🙂" });
    check(`${players}/${difficulty}: profile rejects names invalid in the APK`, rejectedName.status === 400);
    view = renamed.result.view;

    let reveal = view.actions.find(action => action.key === "reveal");
    check(`${players}/${difficulty}: reveal is an offered action`, !!reveal);
    let response = call("/api/action", { revision: renamed.result.revision, action: reveal.key });
    check(`${players}/${difficulty}: reveal action is accepted (${JSON.stringify(response)})`, !response.status);
    view = response.result.view;
    check(`${players}/${difficulty}: reveal exposes only eight human cards`, view.hand.length === 8);

    const rentzChoice = view.actions.find(action => action.key === "choose:RENTZ");
    check(`${players}/${difficulty}: Rentz is available after reveal`, !!rentzChoice);
    response = call("/api/action", { revision: response.result.revision, action: rentzChoice.key });
    check(`${players}/${difficulty}: Rentz contract is accepted`, !response.status);
    view = response.result.view;
    check(`${players}/${difficulty}: four suit rows reach the page projection`, view.rentzSequence.length === 4);
    const openingRanks = { 3: "Q", 4: "J", 5: "10", 6: "9" };
    const openings = view.rentzSequence.flatMap(row => row.cells
      .filter(cell => cell.opening).map(cell => ({ suit: row.suit, label: cell.label })));
    check(`${players}/${difficulty}: native opening rule emits four suit anchors`, openings.length === 4);
    check(`${players}/${difficulty}: opening rank matches the native table-size rule`,
      openings.every(opening => opening.label === `${openingRanks[players]}${opening.suit}`));
    const handMoves = new Set(view.hand.map(card => card.action).filter(Boolean));
    const sequenceMoves = new Set(view.rentzSequence.flatMap(row => row.cells)
      .filter(cell => cell.legal).map(cell => cell.action));
    check(`${players}/${difficulty}: sequence exposes exactly the native legal moves`,
      JSON.stringify([...handMoves].sort()) === JSON.stringify([...sequenceMoves].sort()));

    // Complete the Rentz contract against the generated TeaVM API, without a browser or emulator.
    // This exercises bot turns, forced passes, the score transition, and saved-page restoration.
    let completed = false;
    for (let turn = 0; turn < players * 8 + 16; turn++) {
      const state = call("/api/state");
      view = state.result.view;
      const displayedHand = view.hand.map(card => card.label);
      check(`${players}/${difficulty}: compiled Web hand follows the Android suit/rank order`,
        JSON.stringify(displayedHand) === JSON.stringify(nativeHandOrder(displayedHand)));
      const handMoves = new Set(view.hand.map(card => card.action).filter(Boolean));
      const sequenceMoves = new Set(view.rentzSequence.flatMap(row => row.cells)
        .filter(cell => cell.legal).map(cell => cell.action));
      check(`${players}/${difficulty}: compiled Web hand and Rentz board expose the same legal moves`,
        JSON.stringify([...handMoves].sort()) === JSON.stringify([...sequenceMoves].sort()));
      if (view.history.grid.rows.length === 1) {
        completed = true;
        break;
      }
      const legalAction = view.hand.map(card => card.action).find(action => action?.startsWith("card:"));
      const action = legalAction ?? view.actions.find(candidate => candidate.key === "auto-step")?.key;
      check(`${players}/${difficulty}: active human has a legal move or forced-pass transition`, typeof action === "string");
      const advanced = call("/api/action", { revision: state.result.revision, action });
      check(`${players}/${difficulty}: native command accepts the selected step`, !advanced.status);
      check(`${players}/${difficulty}: playback facts satisfy the shared shell seat renderer`,
        advanced.result.playback.every(step => step.seatFacts.length === players
          && step.seatFacts.every(fact => fact.split("\n").length === 2
            && fact.split("\n").every(line => line.length > 0))));
    }
    check(`${players}/${difficulty}: complete Rentz hand reaches score history`, completed);
    check(`${players}/${difficulty}: score history has every table seat`, view.history.grid.rows[0].cells.length === players);
    check(`${players}/${difficulty}: confirmed Rentz hand persists achievement progress`,
      typeof profile.progress === "string" && profile.progress.length > 0);

    const checkpoint = profile.checkpoint;
    engine.initialize(0, 20261116 + players * 10 + (difficulty === "HARD" ? 1 : 0));
    const resumed = call("/api/state");
    check(`${players}/${difficulty}: checkpoint restores completed hand and history`,
      !resumed.status && resumed.result.view.history.grid.rows.length === 1);
    const next = call("/api/action", { revision: resumed.result.revision, action: "next" });
    check(`${players}/${difficulty}: next-hand transition remains available after restore`, !next.status);
  }
}

console.log(`Rentz compiled engine component checks passed: ${checks}`);
