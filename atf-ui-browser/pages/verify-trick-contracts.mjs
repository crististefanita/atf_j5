import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { runInNewContext } from 'node:vm';
import { componentAudit } from './component-audit.mjs';

// Exercise the delivered shell, not a second implementation of its asynchronous pipeline.
const site = process.argv[2];
const shell = readFileSync(join(site, 'table.mjs'), 'utf8');
const issue = shell.slice(shell.indexOf('async function issue('), shell.indexOf('function applySeatFacts('));
const collection = readFileSync(join(site, 'collection.mjs'), 'utf8').replaceAll('export function ', 'function ');
const engine = await import(pathToFileURL(join(site, 'rentz-engine.mjs')).href);
const { presentRentzPlayback, presentRentzArrival } = await import(pathToFileURL(join(site, 'rentz-ui.mjs')).href);
const check = componentAudit('trick-contracts');
const contracts = ['TRICKS', 'QUEENS', 'DIAMONDS', 'KING_HEARTS', 'TOTALS', 'WHIST', 'TEN_CLUBS'];
const rank = face => ({ J: 11, Q: 12, K: 13, A: 14 }[face.rank] ?? Number(face.rank));
const faceId = play => `${play.seat}/${play.card.rank}${play.card.suit}`;
const points = (contract, cards) => {
  const queens = cards.filter(card => rank(card) === 12).length;
  const diamonds = cards.filter(card => card.suit === '♦').length;
  const king = cards.some(card => rank(card) === 13 && card.suit === '♥');
  return ({ TRICKS: -10, QUEENS: -25 * queens, DIAMONDS: -20 * diamonds,
    KING_HEARTS: king ? -100 : 0, TOTALS: -10 - 25 * queens - 20 * diamonds - (king ? 100 : 0),
    WHIST: 10, TEN_CLUBS: cards.some(card => rank(card) === 10 && card.suit === '♣') ? 100 : 0 })[contract];
};

function presentation(previous, result, reduced, options) {
  let now = 0, sequence = 0, openCollection = false, scoreShown = false;
  let activeCue;
  const queue = new Map(), events = [], violations = [], poses = [];
  const clock = {
    setTimeout(callback, delay) { const id = ++sequence; queue.set(id, { callback, at: now + delay }); return id; },
    clearTimeout(id) { queue.delete(id); }
  };
  const node = (seat = -1) => ({
    dataset: { seat: String(seat) }, style: {}, children: [], offsetWidth: 50, offsetHeight: 70,
    getBoundingClientRect: () => ({ left: seat * 10 + 10, top: 10, width: 50, height: 70 }),
    append(child) { this.children.push(child); }, remove() {}, setAttribute() {},
    cloneNode() { return node(seat); },
    animate(keyframes, timing) {
      const original = this.getBoundingClientRect();
      const target = node(activeCue.winningSeat).getBoundingClientRect();
      const end = keyframes.at(-1).transform.match(/translate\((-?[\d.]+)px, (-?[\d.]+)px\) scale/);
      const left = Number.parseFloat(this.style.left), top = Number.parseFloat(this.style.top);
      const failures = [];
      const compare = (name, expected, actual) => { if (JSON.stringify(expected) !== JSON.stringify(actual)) failures.push({ name, expected, actual }); };
      compare('hold starts on the actual public face', [original.left + original.width / 2, original.top + original.height / 2], [left, top]);
      compare('clone retains the measured face size', [original.width, original.height], [Number.parseFloat(this.style.width), Number.parseFloat(this.style.height)]);
      compare('motion ends at the confirmed winner', [target.left + target.width / 2, target.top + target.height / 2], [left + Number(end?.[1]), top + Number(end?.[2])]);
      compare('shared hold and travel timing', [activeCue.holdMillis, activeCue.travelMillis], [timing.delay, timing.duration]);
      poses.push({ seat, failures });
      return { cancel() {} };
    }
  });
  const seats = previous.view.seats.map((_, seat) => node(seat));
  const baseline = [...seats];
  const center = Object.assign(node(), {
    isConnected: true,
    replaceChildren() { if (openCollection) violations.push('clear before collection deadline'); this.children = []; },
    querySelector(selector) { return this.children.find(card => selector.includes(`data-seat="${card.dataset.seat}"`))?.face; }
  });
  const summary = node();
  const hand = previous.view.hand.map(card => Object.assign(node(), {
    dataset: { face: card.rank + card.suit }, remove() { hand.splice(hand.indexOf(this), 1); }
  }));
  const stage = Object.assign(node(), {
    dataset: {}, scrollTop: 0, offsetWidth: 360,
    getBoundingClientRect: () => ({ left: 0, top: 0, width: 360, height: 740 }),
    querySelector(selector) {
      if (selector === '.board') return node();
      if (selector === '.cards-on-table') return center;
      if (selector === '.whist-bids') return summary;
      return null;
    },
    querySelectorAll: selector => selector === '.seat' ? seats : selector === '.fan .card' ? hand : []
  });
  // An incomplete trick survives across distinct human commands.
  for (const play of previous.view.played) center.children.push(Object.assign(node(play.seat), { face: node(play.seat) }));
  const vm = {
    state: previous, pending: false, animation: undefined, stage, language: 'ro',
    presentRentzPlayback, presentRentzArrival, automation: { set() {} }, setup: { collectionSamples: options.collectionSamples },
    errorBox: { textContent: '' }, selectedBid: undefined,
    document: { createElement: () => node() }, matchMedia: () => ({ matches: reduced }),
    ...clock, requestAnimationFrame: callback => clock.setTimeout(callback, 16),
    placeTable: async () => {}, fitBidSummary() {}, applySeatFacts() {},
    markSeat(target, active) { target.active = active; },
    playedCard(play) {
      if (!seats[play.seat].active) violations.push('card before actor indication');
      events.push(`play:${faceId(play)}`);
      return Object.assign(node(play.seat), { face: node(play.seat) });
    },
    placePlayed() {}, collectionFace: () => node(),
    call: async () => result, transport: {}, copy: (_key, fallback) => fallback,
    menuSetup() {}, helpPage() {}, menu() {}, rememberLanguage() {}, showStorageIssue() {},
    profileDialogs: { notice() {} },
    async render() {
      if (openCollection) violations.push('score render before collection deadline');
      if ((result.collections?.length ?? 0) > events.filter(event => event.startsWith('collected:')).length)
        violations.push('render before all command collections');
      if (vm.state.view.score) scoreShown = true;
      events.push(scoreShown ? 'score' : 'render');
    }
  };
  const commonCollect = runInNewContext(`${collection}; collect`, vm);
  vm.collect = (target, cue, draw, samples) => {
    activeCue = cue;
    if (scoreShown) violations.push('collection started after the score');
    assert.deepEqual(center.children.map(card => Number(card.dataset.seat)), cue.cards.map(play => play.seat),
      'all public faces remain in play order before collection');
    events.push('collect'); openCollection = true;
    const motion = commonCollect(target, cue, draw, samples, clock);
    const deadline = now + cue.displayMillis;
    clock.setTimeout(() => {
      if (scoreShown || center.children.length !== cue.cards.length) violations.push('incomplete hold at deadline minus one');
    }, cue.displayMillis - 1);
    return { ...motion, done: motion.done.then(() => { openCollection = false; events.push(`collected:${deadline}`); }) };
  };
  const executable = options.earlyScore ? issue.replace('await playCards(result.playback);', 'void playCards(result.playback);') : issue;
  const run = runInNewContext(`${executable}; issue`, vm)('/api/action', { action: 'auto-step' });
  return { async finish() {
    let settled = false, failure;
    run.then(() => { settled = true; }, error => { failure = error; settled = true; });
    for (let ticks = 0; ticks < 5000 && !settled; ticks++) {
      for (let tick = 0; tick < 12; tick++) await Promise.resolve();
      if (settled) break;
      const next = [...queue.entries()].sort((a, b) => a[1].at - b[1].at || a[0] - b[0])[0];
      if (!next) continue; // Cross-realm promises may still be resolving after the last timer.
      queue.delete(next[0]); now = next[1].at; next[1].callback();
    }
    if (failure) throw failure;
    assert.equal(settled, true, 'shared issue completes within the bounded virtual clock');
    assert.equal(vm.errorBox.textContent, '', 'the shell does not hide a playback exception');
    assert.deepEqual(seats, baseline, 'playback keeps each physical frame object');
    return { events, violations, poses, scoreShown, frames: seats.length };
  } };
}

let hands = 0, tricks = 0, negativeExercised = false;
for (const players of [3, 4, 5, 6]) for (const contract of contracts) for (const reduced of [false, true]) {
  const key = `${players}/${contract}/${reduced ? 'reduced' : 'motion'}`;
  engine.initialize(0, 20261119 + players);
  let profile = { name: null, progress: null, checkpoint: null, rankings: null };
  const call = (path, values = {}) => {
    const response = JSON.parse(engine.commandLocal(path, JSON.stringify(Object.fromEntries(
      Object.entries(values).map(([key, value]) => [key, String(value)]))),
      profile.name, profile.progress, profile.checkpoint, profile.rankings));
    assert.equal(response.status, undefined, `${key}: accepted ${path}`);
    for (const field of Object.keys(profile)) profile[field] = response[field] ?? null;
    return response.result;
  };
  const motion = { collectionSamples: call('/api/setup').collectionSamples };
  assert.equal(motion.collectionSamples.length, 11, 'Exercise all motion samples from the compiled native-backed setup');
  let state = call('/api/state');
  state = call('/api/start', { players, mode: 'RENTZ', difficulty: 'NORMAL', series: '5', streaks: 'no', zero: 'no', revision: state.revision });
  state = call('/api/action', { action: 'reveal', revision: state.revision });
  const previous = state;
  state = call('/api/action', { action: `choose:${contract}`, revision: state.revision });
  const awards = Array(players).fill(0), played = [], winners = [], violations = [], poses = [];
  let last = previous, completed = false;
  for (let command = 0; command < 32; command++) {
    for (const step of state.playback ?? []) {
      played.push(faceId(step.play));
      if (!step.collection) continue;
      const cue = step.collection, suit = cue.cards[0].card.suit;
      const winner = cue.cards.filter(play => play.card.suit === suit)
        .reduce((best, play) => rank(play.card) > rank(best.card) ? play : best).seat;
      assert.equal(cue.winningSeat, winner, `${key}: independent lead-suit/rank winner`);
      assert.equal(cue.cards.length, players, `${key}: one public card per seat`);
      assert.equal(new Set(cue.cards.map(play => play.seat)).size, players, `${key}: no duplicated seat`);
      assert.equal(cue.holdMillis, 1000); assert.equal(cue.travelMillis, 500); assert.equal(cue.displayMillis, 1500);
      awards[winner] += points(contract, cue.cards.map(play => play.card));
      winners.push(winner); tricks++;
    }
    const observed = await presentation(last, state, reduced, motion).finish();
    violations.push(...observed.violations);
    poses.push(...observed.poses);
    assert.equal(observed.scoreShown, Boolean(state.view.score), `${key}: score is committed only for a settled hand`);
    if (state.view.score) {
      check(`${key}: complete real hand, actor-before-card, full hold, collection before score`, [], violations);
      check(`${key}: independent trick awards match the native-backed history`, awards,
        state.view.history.grid.rows[0].cells.map(cell => Number(cell.bid)));
      check(`${key}: result table exposes exactly the confirmed history row`, state.view.history.grid.rows, state.view.score.table.grid.rows);
      check(`${key}: all animated clones preserve source pose, size, winner and timing`, [], poses.flatMap(pose => pose.failures));
      check(`${key}: each collected face is covered, with no animation in reduced-motion mode`, reduced ? 0 : winners.length * players, poses.length);
      check(`${key}: distinct played cards and bounded complete tricks`, { unique: played.length, cards: winners.length * players },
        { unique: new Set(played).size, cards: played.length });
      if (!negativeExercised && state.playback?.some(step => step.collection)) {
        const broken = await presentation(last, state, reduced, { ...motion, earlyScore: true }).finish();
        check('negative control: removing the shell await is detected before the last collection', true,
          broken.violations.length > 0 || !broken.events.some(event => event.startsWith('collected:')));
        negativeExercised = true;
      }
      completed = true; hands++; break;
    }
    last = state;
    state = call('/api/action', { action: 'auto-step', revision: state.revision });
  }
  assert.equal(completed, true, `${key}: the real contract must finish`);
}
check('coverage is the full seven-contract × four-table-size × two-motion-mode matrix', 56, hands);
console.log(`PASS real trick contracts: ${hands} complete hands, ${tricks} collections, exact delivered issue/playCards/collect`);
