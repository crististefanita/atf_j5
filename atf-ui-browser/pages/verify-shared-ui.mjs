import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { runInNewContext } from 'node:vm';
import { componentAudit } from './component-audit.mjs';

const [site, contracts, nativeFile] = process.argv.slice(2);
const check = componentAudit('shared-ui-adoption');
const load = path => import(pathToFileURL(path).href);
const engine = await load(join(site, 'rentz-engine.mjs'));
const { verifyCardTypography } = await load(join(contracts, 'card-typography.mjs'));
const { verifyHistoryMenu, verifyGameMenuDismissal } = await load(join(contracts, 'menu-presentation.mjs'));
const { verifyScorePresentation } = await load(join(contracts, 'score-presentation.mjs'));
const { verifySeatFitting, verifyActorLabels, verifySeatRetention, verifyCenterContinuity } = await load(join(contracts, 'seat-presentation.mjs'));
const { fitSeatLine, retainSeatFrames } = await load(join(site, 'seat-presentation.mjs'));
const { rentzChoiceDropdown } = await load(join(site, 'rentz-ui.mjs'));
const { historyFirst } = await load(join(site, 'table-menu.mjs'));
const { dismissGameMenuOnOutsideClick } = await load(join(site, 'profile.mjs'));
const native = JSON.parse(readFileSync(nativeFile, 'utf8').replace(/^\uFEFF/, ''));
engine.initialize(0, 31);
const call = (path, values = {}) => JSON.parse(engine.commandLocal(path, JSON.stringify(values), null, null, null, null)).result;
const setup = call('/api/setup');
check('The delivered renderer keeps the same card origin across playback and human turns',
  { emptyStates: 4, emptyControlSiblings: 0, realActionsPreserved: true, legacyPromptPreserved: true },
  verifyCenterContinuity(readFileSync(join(site, 'table.mjs'), 'utf8'), { rentzChoiceDropdown, action() {}, language: 'ro' }));
const palette = new Map(setup.palette.map(token => [token.name, token.color]));
check('Shared menu target matches the Android header 48dp touch contract', '48px', palette.get('--whist-menu-touch') ?? 'missing');
check('Shared title keeps the Android header 8dp gap after the menu', '8px', palette.get('--whist-title-gap') ?? 'missing');
for (const expected of await verifyCardTypography(setup)) {
  const actual = native.faces.find(face => face.height === expected.height);
  check(`The delivered shared typography fixture exists in the Android AAR at ${expected.height}dp`, true, !!actual);
  for (const key of Object.keys(expected)) {
    check(`Native/browser face ${expected.height}dp/${key}`, true, Math.abs(actual[key] - expected[key]) < 0.00001);
  }
}
check('Delivered history menu contract runs on the actual shared module',
  { languages: 2, historyRoutes: 1, constructorActions: 0, selectedActions: 1, invalidPlansRefused: true }, verifyHistoryMenu(historyFirst));
check('Delivered dismissal contract survives a menu redraw',
  { insideKeepsOpen: true, outsideDismisses: true, afterRerenderDismisses: true }, verifyGameMenuDismissal(dismissGameMenuOnOutsideClick));

// Execute the actual packaged renderer function. Only the browser's measurement surface is mocked.
const table = readFileSync(join(site, 'table.mjs'), 'utf8');
check('Stable seat elements survive AI/human handoffs and resize across 3–6 places',
  {tables: 96, transitions: 480, resizeAdmitted: true, incompatibleTablesUntouched: 4}, verifySeatRetention(retainSeatFrames));
const retainAt = table.indexOf('  retainSeatFrames(stage.querySelector(');
check('The actual shared renderer retains measured seats at commit, after preparing the complete view', true,
  retainAt > table.indexOf('    await placeTable(content);')
    && table.indexOf('  stage.replaceChildren(content);', retainAt) > retainAt
    && table.includes('position.dataset.seat = String(seatIndex)'));
check('Measured seat ink keeps its text and height while scores change',
  {cases: 75, heightChanges: 0, textChanges: 0, idempotent: true}, verifySeatFitting(fitSeatLine));
const actorSource = table.match(/function markSeat\(node, active, bidding, actorLabel\) \{[\s\S]*?\n\}/)?.[0];
check('One actual shared actor renderer is available', true, !!actorSource);
const markActor = runInNewContext(`${actorSource}; markSeat`, {copy: (key, fallback) => fallback});
check('The actor label belongs to the game and survives in-place playback',
  {localizedLabels: 5, nativeHumanLabelVisible: true, legacyBiddingPolicyRetained: true}, verifyActorLabels(markActor));
class ScoreElement {
  constructor(tag, text = '') { this.tagName = tag; this.textContent = text; this.children = []; this.style = {}; this.dataset = {}; this.classList = {add() {}}; }
  append(...children) { this.children.push(...children); }
  setAttribute(key, value) { this[key] = value; }
}
const scoreElement = (tag, text = '') => new ScoreElement(tag, text);
const scoreSource = table.match(/function scoreGrid\(data, native = false\) \{[\s\S]*?\n\}/)?.[0];
check('One actual delivered score renderer is available', true, !!scoreSource);
const renderScore = runInNewContext(`${scoreSource}; scoreGrid`, {element: scoreElement});
const renderHistory = (await load(join(site, 'records.mjs'))).historyGrid;
check('Delivered score contracts preserve consumer captions and signed values',
  {cases: 8, columnFamilies: 2, scoringCalculations: 0}, verifyScorePresentation(renderScore, renderHistory, scoreElement));
const source = table.match(/function placePlayed\(cards\) \{[\s\S]*?\n\}/)?.[0];
check('Exactly one shared card-placement function is available', 1, table.match(/function placePlayed\(cards\)/g)?.length ?? 0);
const playedLayouts = new WeakMap();
const place = runInNewContext(`${source}; placePlayed`, {
  playedLayouts, setup, getComputedStyle: () => ({ getPropertyValue: () => '88px' }),
});
const style = () => ({ setProperty(key, value) { this[key] = value; } });
const makeCard = seat => {
  const face = { style: style(), classList: { contains: value => value === 'whist-play-face' } };
  const label = { style: style() };
  return { dataset: { seat: String(seat) }, style: style(), face, label,
    querySelector: selector => selector === '.card' ? face : label };
};
const rect = value => [value.left, value.top, value.right, value.bottom];
const contractsWithTricks = ['TRICKS', 'QUEENS', 'DIAMONDS', 'KING_HEARTS', 'TOTALS', 'WHIST', 'TEN_CLUBS'];
for (const reference of native.boards) {
  const key = `${reference.players}/${reference.width}/${reference.viewport}`;
  const input = { width: String(reference.width), height: String(reference.height), mode: 'RENTZ',
    seatHeight: '66', bidOptions: '0', players: String(reference.players), viewport: String(reference.viewport),
    handWidth: String(reference.width), cards: '8' };
  for (const contract of contractsWithTricks) {
    const layout = call('/api/layout', { ...input, mode: contract });
    check(`${key}/${contract}: native and browser keep the same seat and center rectangles`,
      { seats: reference.seats, center: reference.center },
      { seats: layout.board.seats.map(rect), center: rect(layout.board.center) });
    check(`${key}/${contract}: same faces and clockwise slots from the pinned Android AAR`,
      { width: reference.cardWidth, height: reference.cardHeight, slots: reference.slots },
      { width: layout.played.cardWidthDp, height: layout.played.cardHeightDp,
        slots: layout.played.slots.map(slot => [slot.seat, slot.row, slot.leftDp]) });
    for (let visible = 1; visible <= reference.players; visible++) {
      const children = Array.from({ length: visible }, (_, index) => makeCard(reference.players - 1 - index));
      const cards = { children, childElementCount: children.length, style: style(), classList: { add() {} } };
      playedLayouts.set(cards, { ...layout.played, ...layout.playedStyle });
      place(cards);
      check(`${key}/${contract}: ${visible} visible cards use seat IDs, shared sizing and rank scaling`,
        children.map(tile => {
          const slot = layout.played.slots[Number(tile.dataset.seat)];
          return [slot.leftDp + 'px', (slot.row * layout.played.rowStrideDp + layout.playedStyle.verticalPaddingDp) + 'px',
            reference.cardWidth + 'px', reference.cardHeight + 'px', reference.cardHeight / 88];
        }),
        children.map(tile => [tile.style.left, tile.style.top, tile.face.style.width,
          tile.face.style.height, tile.face.style['--card-face-scale']]));
    }
  }
  const sequence = call('/api/layout', { ...input, rentzSequence: 'true' });
  check(`${key}: sequence retains seat anchors and bypasses the trick renderer`,
    { seats: reference.seats, played: null, playedStyle: {} },
    { seats: sequence.board.seats.map(rect), played: sequence.played, playedStyle: sequence.playedStyle });
}
console.log(`PASS shared UI adoption: ${native.boards.length} native/browser profiles, seven trick contracts, delivered typography and menu contracts`);
