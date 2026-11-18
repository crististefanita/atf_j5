import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { componentAudit } from './component-audit.mjs';

const { autoGame } = await import(pathToFileURL(join(process.argv[2], 'auto-game.mjs')).href);
const check = componentAudit('shared-automation');
const flush = async () => { for (let turn = 0; turn < 8; turn++) await Promise.resolve(); };
function fixture() {
  const waits = [], decisions = [], selections = [];
  let available = true, pendingStep;
  const auto = autoGame({
    available: () => available,
    wait: signal => new Promise(resolve => {
      const timer = { signal, resolve };
      waits.push(timer);
      signal.addEventListener('abort', resolve, { once: true });
    }),
    step: async () => { decisions.push('accepted'); if (pendingStep) await pendingStep; return true; },
    changed: value => selections.push(value),
  });
  return { auto, waits, decisions, selections, end: () => { available = false; },
    holdStep: () => { let resolve; pendingStep = new Promise(done => { resolve = done; }); return resolve; } };
}
const first = fixture();
first.auto.set(true); first.auto.set(true);
check('Checking the shared AutoPlay control twice schedules one decision', 1, first.waits.length);
first.auto.set(false); await flush();
check('Stopping before the delay commits no move', [], first.decisions);
check('Stopping preserves the visible selection state', [true, false], first.selections);

const menu = fixture();
menu.auto.set(true); menu.auto.pause(true); await flush();
check('Opening a menu suspends scheduling without clearing the checkbox', true, menu.auto.selected());
check('Menu pause cancels the pending decision', [], menu.decisions);
menu.auto.pause(false); await flush();
check('Closing the menu creates exactly one replacement delay', 2, menu.waits.length);
menu.waits[1].resolve(); await flush();
check('Resumed scheduling commits exactly one move', ['accepted'], menu.decisions);
menu.end(); menu.waits[2].resolve(); await flush();
check('Reaching score does not submit another card', ['accepted'], menu.decisions);
check('Reaching score clears AutoPlay', false, menu.auto.selected());

const inflight = fixture();
const finishStep = inflight.holdStep();
inflight.auto.set(true); inflight.waits[0].resolve(); await flush();
inflight.auto.set(false); finishStep(); await flush();
check('Stopping during an accepted action keeps that action exactly once', ['accepted'], inflight.decisions);
check('Stopping during an accepted action schedules no further delay', 1, inflight.waits.length);

const rapid = fixture();
rapid.auto.set(true); rapid.auto.set(false); rapid.auto.set(true); await flush();
check('Rapid stop/start leaves one aborted and one live delay', [true, false], rapid.waits.map(wait => wait.signal.aborted));
rapid.auto.set(false); await flush();
check('Rapid toggles never submit a stale move', [], rapid.decisions);
console.log('PASS shared AutoPlay: idempotence, cancellation, menu pause, score transition and in-flight decisions');
