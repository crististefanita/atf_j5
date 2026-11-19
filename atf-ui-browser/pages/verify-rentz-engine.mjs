import assert from "node:assert/strict";
import { pathToFileURL } from "node:url";
import { componentAudit } from './component-audit.mjs';
const recordCheck = componentAudit('compiled-engine');

const engine = await import(pathToFileURL(process.argv[2]).href);
const { verifyHandPresentation } = await import(pathToFileURL(process.argv[3]).href);
const { bindHandFeedback } = await import(pathToFileURL(process.argv[4]).href);
const { presentRentzPlayback } = await import(pathToFileURL(process.argv[5]).href);
let checks = 0;
let sharedHandComponentChecked = false;
const nativeSuitOrder = { "♠": 0, "♥": 1, "♣": 2, "♦": 3 };
const nativeRank = label => ({ J: 11, Q: 12, K: 13, A: 14 }[label.slice(0, -1)] ?? Number(label.slice(0, -1)));

function nativeHandOrder(labels) {
  return [...labels].sort((left, right) => nativeSuitOrder[left.slice(-1)] - nativeSuitOrder[right.slice(-1)]
    || nativeRank(left) - nativeRank(right));
}

function check(description, expected, actual) {
  if (arguments.length === 3) recordCheck(description, expected, actual);
  else recordCheck(description, true, Boolean(expected));
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
    if (!sharedHandComponentChecked) {
      const cards = { first: { face: "8♦", command: "first" }, second: { face: "10♦", command: "second" },
        wrong: { face: "8♣", command: "" } };
      const motion = await verifyHandPresentation(bindHandFeedback, setup.result.handMotion, cards, ({ card }) => {
        const wrong = card === cards.wrong.face;
        return { action: wrong ? "HINT" : "PLAY", rejected: wrong ? card : "", selected: !wrong,
          revision: wrong ? 1 : 2, suggestions: wrong ? [cards.first.face, cards.second.face] : [] };
      }, command => assert.equal(command, cards.second.command));
      check("Rentz uses the shared J4 hand hint, lift and cancel behavior from the pinned component test",
        motion.submitted === 1 && motion.duration === setup.result.handMotion.liftMillis);
      sharedHandComponentChecked = true;
    }
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
    const supplementaryLetter = "\uD801\uDC00";
    const unicodeName = call("/api/profile", { revision: renamed.result.revision,
      name: supplementaryLetter.repeat(10) });
    check(`${players}/${difficulty}: compiled browser accepts ten supplementary Unicode letters`,
      !unicodeName.status && unicodeName.name === supplementaryLetter.repeat(10));
    const overlongUnicodeName = call("/api/profile", { revision: unicodeName.result.revision,
      name: supplementaryLetter.repeat(11) });
    check(`${players}/${difficulty}: compiled browser counts Unicode code points like the APK`,
      overlongUnicodeName.status === 400);
    view = unicodeName.result.view;

    let reveal = view.actions.find(action => action.key === "reveal");
    check(`${players}/${difficulty}: reveal is an offered action`, !!reveal);
    let response = call("/api/action", { revision: unicodeName.result.revision, action: reveal.key });
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
      const renderedFrames = [];
      const playbackFrames = advanced.rentzPlayback ?? [];
      const replayed = await presentRentzPlayback(advanced, {
        render: async snapshot => renderedFrames.push(snapshot.view),
        nextFrame: async () => {},
        wait: async () => {},
      });
      check(`${players}/${difficulty}: real engine playback is routed through the browser adapter`,
        playbackFrames.length > 0, replayed);
      const expectedRenderCount = playbackFrames.reduce((count, frame) => count + (frame.delayMillis > 0 ? 2 : 1), 0);
      check(`${players}/${difficulty}: browser adapter renders each actor/card step exactly once`,
        expectedRenderCount, renderedFrames.length);
      if (playbackFrames.length > 0) {
        const labels = sequence => sequence.flatMap(row => row.cells)
          .filter(cell => cell.played).map(cell => cell.label).sort();
        check(`${players}/${difficulty}: displayed Rentz board equals the last public frame`,
          labels(playbackFrames.at(-1).after.rentzSequence), labels(renderedFrames.at(-1).rentzSequence));
        check(`${players}/${difficulty}: last public frame equals the committed engine state`,
          labels(advanced.result.view.rentzSequence), labels(renderedFrames.at(-1).rentzSequence));
      }
    }
    check(`${players}/${difficulty}: complete Rentz hand reaches score history`, completed);
    check(`${players}/${difficulty}: score history has every table seat`, view.history.grid.rows[0].cells.length === players);
    check(`${players}/${difficulty}: compiled settlement keeps the canonical native column roles`,
      ["Jucător","P","T"], view.score.table.headings);
    check(`${players}/${difficulty}: compiled settlement and full history have identical confirmed values`,
      view.history.grid.players.map((name,seat)=>[name,view.history.grid.rows[0].cells[seat].bid,view.history.grid.rows[0].cells[seat].total]),
      view.score.table.rows);
    check(`${players}/${difficulty}: confirmed hand enables the native score-menu route`, [], view.rentzMenuUnavailable);
    const rentzDeltas = view.history.grid.rows[0].cells.map(cell => Number(cell.bid));
    const awardsByPlayers = {
      3: [300, 200, 0], 4: [300, 200, 100, 0],
      5: [300, 175, 100, 25, 0], 6: [300, 150, 75, 50, 25, 0],
    };
    const sortedDeltas = [...rentzDeltas].sort((left, right) => right - left);
    check(`${players}/${difficulty}: compiled Rentz settlement matches the Android award schedule`,
      JSON.stringify(sortedDeltas) === JSON.stringify(awardsByPlayers[players]));
    check(`${players}/${difficulty}: compiled score row has exactly one zero award`,
      rentzDeltas.filter(points => points === 0).length === 1);
    const awardLines = view.scoreDetails.awards.split("\n");
    const playerLines = awardLines.filter(line => !line.startsWith("  "));
    const contributionLines = awardLines.filter(line => line.startsWith("  "));
    const historyCells = view.history.grid.rows[0].cells;
    const orderedAwards = playerLines.map((line, row) => {
      const match = line.match(/^(?:\d+\. )?(.+?) · P (-?\d+) · T (-?\d+)$/);
      const seat = match ? view.seats.findIndex(value => value.name === match[1]) : -1;
      check(`${players}/${difficulty}: score line exposes confirmed delta and total for seat ${seat}`,
        !!match && seat >= 0 && Number(match[2]) === Number(historyCells[seat]?.bid)
          && Number(match[3]) === Number(historyCells[seat]?.total));
      return match ? Number(match[2]) : Number.NaN;
    });
    check(`${players}/${difficulty}: compiled score details retain Rentz finish order and the single zero`,
      JSON.stringify(orderedAwards) === JSON.stringify(awardsByPlayers[players])
        && playerLines.length === players && contributionLines.length === players);
    check(`${players}/${difficulty}: confirmed Rentz hand persists achievement progress`,
      typeof profile.progress === "string" && profile.progress.length > 0);

    const checkpoint = profile.checkpoint;
    engine.initialize(0, 20261116 + players * 10 + (difficulty === "HARD" ? 1 : 0));
    const resumed = call("/api/state");
    check(`${players}/${difficulty}: checkpoint restores completed hand and history`,
      !resumed.status && resumed.result.view.history.grid.rows.length === 1);
    const next = call("/api/action", { revision: resumed.result.revision, action: "next" });
    check(`${players}/${difficulty}: next-hand transition remains available after restore`, !next.status);
    if (players === 4) {
      const firstRow = JSON.stringify(view.history.grid.rows[0]);
      let second = next;
      for (let step = 0; step < 100 && second.result.view.history.grid.rows.length < 2; step++) {
        const current = second.result.view;
        const action = current.actions.find(a => a.key === "auto-step")?.key
          ?? current.actions.find(a => a.key === "reveal")?.key
          ?? current.actions.find(a => a.key === "choose:TRICKS")?.key;
        check(`${difficulty}: second consecutive contract always offers a public action`, typeof action === "string");
        second = call("/api/action", {revision:second.result.revision, action});
        check(`${difficulty}: second consecutive action succeeds`, !second.status);
      }
      const rows = second.result.view.history.grid.rows;
      check(`${difficulty}: two complete contracts are retained`, 2, rows.length);
      check(`${difficulty}: first Rentz row is unchanged by later play`, firstRow, JSON.stringify(rows[0]));
      check(`${difficulty}: another contract follows Rentz`, rows[1].status !== rows[0].status);
      for (let seat = 0; seat < players; seat++) {
        check(`${difficulty}: cumulative total equals both contributions for seat ${seat}`,
          Number(rows[0].cells[seat].bid) + Number(rows[1].cells[seat].bid), Number(rows[1].cells[seat].total));
      }
      check(`${difficulty}: result table contains only the just-completed hand`,
        [rows[1]], second.result.view.score.table.grid.rows);
      const restored = call("/api/state");
      check(`${difficulty}: checkpoint preserves the complete two-contract history`, rows, restored.result.view.history.grid.rows);
    }
  }
}

console.log(`Rentz compiled engine component checks passed: ${checks}`);
