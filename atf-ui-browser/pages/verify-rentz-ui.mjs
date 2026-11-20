import assert from "node:assert/strict";
import { presentRentzArrival, presentRentzPlayback, rentzArrivalView, rentzChoiceDropdown, rentzSequence,
  rentzSequenceLayout, rentzCenterInsets, rentzSequenceLowerShift, applyRentzSequenceLayout, refreshRentzSequence } from "./web/rentz-ui.mjs";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { pathToFileURL } from "node:url";
import { rentzScoreTable, rentzHistoryGrid, rentzScoreRules } from "./web/rentz-score.mjs";
import { componentAudit } from './component-audit.mjs';
import { runInNewContext } from 'node:vm';
import { RentzWebFlow } from "../tests/rentz-web-flow.mjs";
const recordCheck = componentAudit('browser-rendering');

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
    this.classList = { add: value => { this.className += ` ${value}`; } };
  }
  append(...children) { this.children.push(...children); }
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
  recordCheck(message, expected, actual);
  checks++;
};
function refuses(message, action) {
  let observed = "No exception";
  try { action(); } catch (error) { observed = error.name; }
  check(message, "TypeError", observed);
}
async function rejectedPlaybackIsAtomic(description, frames, expectedMessage) {
  const effects = [];
  let failure;
  try {
    await presentRentzPlayback({ ...settled, rentzPlayback: frames }, {
      render: async state => effects.push(state.view.heading),
      nextFrame: async () => effects.push("frame"),
      wait: async () => effects.push("wait"),
    });
  } catch (error) {
    failure = error;
  }
  check(`${description} is rejected`, true, failure instanceof Error);
  check(`${description} reports the failed contract`, true, failure?.message.includes(expectedMessage));
  check(`${description} is rejected before changing the rendered table`, [], effects);
}
const element = (tag, text, className) => new FakeElement(tag, text, className);
const autoGateCalls = [];
let transitionFinished = false;
const autoGateFlow = new RentzWebFlow({playwright: {
  locator: selector => ({waitFor: async options => {
    assert.equal(selector, '#table[aria-busy="true"]');
    assert.equal(options.state, 'hidden');
    assert.equal(options.timeout, 40000, 'A real multi-card playback can exceed the default selector deadline');
    autoGateCalls.push('visible transition finishes'); transitionFinished = true;
  }}),
  getByRole: () => ({check: async () => {
    assert.equal(transitionFinished, true, 'A re-render must finish before the checkbox is checked');
    autoGateCalls.push('checkbox checked');
  }})
}}, async () => {});
await autoGateFlow.automatic();
check("MCP AutoPlay waits for the public busy marker before touching a replacement checkbox",
  ['visible transition finishes', 'checkbox checked'], autoGateCalls);
const visibleScore = {labels:['Jucător','Δ:','Σ:'],rows:[['Ana','300','300'],['Dan','100','100'],['Ioana','200','200'],['Radu','0','0']],weights:Array(15).fill('400')};
const scoreFlow = new RentzWebFlow({screenshot:async()=>new Uint8Array(),playwright:{
  domSnapshot:async()=> 'Confirmed Rentz score and complete public history',
  locator:selector=>({evaluate:async()=>visibleScore,innerText:async()=>selector.includes('status')?'Rentz · Mâna 1':visibleScore.rows.map(row=>`${row[0]} · Δ: ${row[1]} · Σ: ${row[2]}`).join('\n')}),
  getByRole:()=>({click:async()=>{}}),getByText:()=>({isVisible:async()=>true})
}},async()=>{});
let scoreFlowError;
try { await scoreFlow.score(1); } catch(error) { scoreFlowError=error.message; }
check('Real WEB flow accepts the canonical native Δ:/Σ: settlement instead of obsolete P/T headings','PASS',scoreFlowError??'PASS');
check('History expectations use Δ/Σ without colons',Array.from({length:4},()=>['Δ','Σ']).flat(),scoreFlow.first?.labels);
visibleScore.labels=['Jucător','P','T'];
const staleCaptures=[];
const staleScoreFlow=new RentzWebFlow(scoreFlow.tab,async point=>staleCaptures.push(point));
let staleScoreRejected=false;
try { await staleScoreFlow.score(1); } catch { staleScoreRejected=true; }
check('Obsolete settlement captions remain rejected by the real WEB flow',true,staleScoreRejected);
check('The negative neighbor fails only the caption contract',['Compact settlement headings'],staleCaptures[0].checks.filter(row=>!row.passed).map(row=>row.name));
visibleScore.labels=['Jucător','Δ:','Σ:'];
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

for (const players of [3, 4, 5, 6]) {
  const delayed = rentzSequence(rows.map(row => ({ ...row,
    cells: Array.from({length: players * 2}, (_, i) => ({...row.cells[i % row.cells.length]})) })), element, "ro");
  const observer = FakeResizeObserver.instances.at(-1);
  delayed.clientWidth = 353; delayed.clientHeight = 248;
  delayed.setBounds(353, 248);
  delayed.parentElement = { clientHeight: 217 };
  observer.trigger();
  check(`delayed panel measurement cannot exceed the current card lane at ${players} seats`,
    `${rentzSequenceLayout(players, 353, 217).rowHeight}px`, delayed.children[0].style.height);
  delayed.parentElement.clientHeight = 193;
  refreshRentzSequence(delayed);
  check(`prepared board is measured synchronously before exposure at ${players} seats`,
    `${rentzSequenceLayout(players, 353, 193).faceHeight}px`, delayed.children[0].children[0].style.height);
  observer.trigger();
  check(`late observer preserves the committed card metrics at ${players} seats`,
    `${rentzSequenceLayout(players, 353, 193).rowHeight}px`, delayed.children[0].style.height);
}
refreshRentzSequence(null);

const scaledPanel = rentzSequence(["♣", "♦", "♥", "♠"].map(suit=>({suit,cells:Array.from({length:8},(_,i)=>({label:`${i+7}${suit}`,played:true,opening:i===4,legal:false,action:""}))})), element, "ro");
const scaledObserver = FakeResizeObserver.instances.at(-1);
scaledPanel.clientWidth=374;
scaledPanel.clientHeight=248;
for(const scale of [.45,.62,1,1.5]) {
  scaledPanel.setBounds(374*scale,248*scale);
  scaledObserver.trigger();
  check(`viewport scale ${scale} cannot shrink Rentz card metrics twice`,
    `${rentzSequenceLayout(4,374,248).faceHeight}px`,scaledPanel.children[0].children[0].style.height);
  check(`viewport scale ${scale} preserves readable logical card indices`,
    `${rentzSequenceLayout(4,374,248).labelSizePx}px`,scaledPanel.children[0].children[0].style.fontSize);
}

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
const defaultCenterInsets = { top: 72, bottom: 67 };
for (const players of [3, 5, 6]) {
  check(`seat positioning stays on the shared layout for ${players} players`, defaultCenterInsets,
    rentzCenterInsets(players));
}
const fourPlayerInsets = rentzCenterInsets(4);
check("four-player table keeps six pixels of clearance around 52px player boxes",
  true, fourPlayerInsets.top >= 58 && fourPlayerInsets.bottom >= 58);
check("four-player sequence uses the lower shift when the lane has room", 24,
  rentzSequenceLowerShift(4, 320, 248));
check("four-player sequence caps its shift at half the measured slack", 6,
  rentzSequenceLowerShift(4, 260, 248));
check("four-player sequence stays centered when the lane is full", 0,
  rentzSequenceLowerShift(4, 248, 248));
for (const players of [3, 5, 6]) {
  check(`sequence positioning is unchanged at ${players} seats`, 0,
    rentzSequenceLowerShift(players, 320, 248));
}
check("four-player Rentz reserves 23 extra vertical pixels for played cards", 23,
  defaultCenterInsets.top + defaultCenterInsets.bottom - fourPlayerInsets.top - fourPlayerInsets.bottom);
check("compact four-player cards gain a larger readable face from the extra center space", true,
  rentzSequenceLayout(4, 380, 330 - fourPlayerInsets.top - fourPlayerInsets.bottom).faceHeight
    > rentzSequenceLayout(4, 380, 330 - defaultCenterInsets.top - defaultCenterInsets.bottom).faceHeight);
check("invalid Rentz seat counts cannot produce table insets", true, (() => {
  try { rentzCenterInsets(2); return false; } catch { return true; }
})());
const denseRanks = ["3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"];
const denseRows = ["♣", "♦", "♥", "♠"].map((suit, suitIndex) => ({
  suit,
  cells: denseRanks.map((rank, column) => ({ label: `${rank}${suit}`, legal: false, action: "",
    opening: column === 6, played: suitIndex === 1 && column === 2 })),
}));
const densePanel = rentzSequence(denseRows, element, "ro");
const denseLayout = applyRentzSequenceLayout(densePanel, 280, 235, 1, 320, 248);
const fourSeatRows = denseRows.map(row => ({ ...row, cells: row.cells.slice(0, 8) }));
const fourSeatPanel = rentzSequence(fourSeatRows, element, "ro");
applyRentzSequenceLayout(fourSeatPanel, 280, 235, 1, 320, 248);
check("measured four-seat Rentz sequence lower shift reaches the panel", "24px",
  fourSeatPanel.style.properties["--rentz-sequence-lower-shift"]);
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
check("four-seat sequence applies the measured lower shift", true,
  rentzCss.includes("transform: translateY(var(--rentz-sequence-lower-shift, 0px))"));
check("Rentz arrival uses the Android idle-seat text color", true, rentzCss.includes("color: #d3efe2;"));
check("Rentz sequence indices keep Android's left edge alignment", true,
  rentzCss.includes("justify-content: flex-start;"));
check("Rentz sequence uses a condensed card-face font", true,
  rentzCss.includes("Roboto Condensed") && rentzCss.includes("Arial Narrow"));
check("Rentz suit symbols use the font shipped by the shared browser library", true,
  rentzCss.includes('"CardTableSuits"'));
check("Rentz center consumes dynamic vertical insets without changing the common seat anchors", true,
  rentzCss.includes("inset-block: var(--rentz-center-top, 72px) var(--rentz-center-bottom, 67px);"));
const generatedShell = readFileSync(process.argv[3], "utf8");
const generatedTableCss = readFileSync(process.argv[4], "utf8");
const generatedRentzCss = readFileSync(process.argv[5], "utf8");
const generatedPage = readFileSync(process.argv[6], "utf8");
check("prepared Rentz rows are measured immediately after shared table placement", true,
  generatedShell.includes('await placeTable(content);\n    refreshRentzSequence(content.querySelector(".rentz-sequence"));'));
const { viewportGeometry } = await import(pathToFileURL(join(dirname(process.argv[3]),"viewport.mjs")));
const resizeBody=generatedShell.match(/const resize = \(\) => \{([\s\S]*?)\n\};\nresize\(\);/);
assert.ok(resizeBody,"The shared resize entry point remains discoverable");
for(const visibleHeight of [480,570,740]) {
  const viewport={style:{}};
  const context={animation:null,window:{visualViewport:{width:393,height:visibleHeight}},
    document:{documentElement:{clientWidth:393,style:{setProperty(){}}}},
    viewport,frame:{dataset:{},style:{}},stage:{style:{setProperty(){}},firstElementChild:null},
    history:{style:{setProperty(){}}},navigator:{userAgent:"Android Mobile"},innerHeight:visibleHeight+56,
    getComputedStyle:()=>({paddingLeft:"0",paddingRight:"0",paddingTop:"0",paddingBottom:"0"}),
    isPhone:()=>true,viewportGeometry};
  runInNewContext(`(()=>{${resizeBody[1]}})()`,context);
  check(`mobile browser viewport excludes address-bar overflow at ${visibleHeight}px`,`${visibleHeight}px`,viewport.style.minHeight);
}
const { historyGrid } = await import(pathToFileURL(join(dirname(process.argv[3]), "records.mjs")));
check("contributions are preserved once in quiet detail paragraphs",["Ana · Δ: -10 · Σ: 20","Levată × 1 · -10","Profil confirmat"],
  rentzScoreRules({awards:"Ana · Δ: -10 · Σ: 20\nLevată × 1 · -10",rules:["Profil confirmat"]}));
const sharedScoreSource = generatedShell.slice(generatedShell.indexOf("function scoreGrid("), generatedShell.indexOf("function showHistory(")).trim();
const sharedScoreGrid = runInNewContext("(" + sharedScoreSource + ")", {element});
for (const players of [3, 4, 5, 6]) for (const language of ["ro", "en"]) {
  const grid = {players:Array.from({length:players}, (_,i)=>"Loc " + (i+1)), legend:"Δ: punctele mâinii · Σ: total după mână",
    cardsLabel:"Mână",bidLabel:"Δ",pointsLabel:"Σ",
    rows:[{cards:1,status:"Levate",cells:Array.from({length:players},(_,i)=>({bid:String(i*25-50),total:String(i*25+10),description:"",missed:false,series:0}))}]};
  let result;
  const data={grid,headings:[language==="ro"?"Jucător":"Player","Δ:","Σ:"],
    rows:grid.players.map((name,seat)=>[name,grid.rows[0].cells[seat].bid,grid.rows[0].cells[seat].total])};
  let sharedInput;
  assert.doesNotThrow(()=>{result=rentzScoreTable(data,element,(value,human)=>{sharedInput=value;return sharedScoreGrid(value,human);});}, "Result forwards the native three-column projection to the shared renderer");
  check(players+"p: renderer receives the original canonical data",true,sharedInput===data);
  for (const changed of [{headings:["Player","Bid","Σ:"]},{rows:[...data.rows].reverse()},
      {rows:data.rows.map((row,index)=>index===0?[row[0],"wrong",row[2]]:row)}]) {
    refuses(`${players}/${language}: reject disagreement with the confirmed native Δ/Σ projection ${JSON.stringify(changed)}`,
      ()=>rentzScoreTable({...data,...changed},element,sharedScoreGrid));
  }
  const table=result.children[0],header=table.children.find(row=>row.tagName==="thead"),body=table.children.find(row=>row.tagName==="tbody");
  check(players+"p: result uses shared Whist grid", "whist-score-grid", table.className);
  check(players+"p: short headings in "+language,JSON.stringify([language==="ro"?"Jucător":"Player","Δ:","Σ:"]),JSON.stringify(header.children[0].children.map(cell=>cell.textContent)));
  check(players+"p: one visible row per player",players,body.children.length);
  for(let i=0;i<players;i++) check(players+"p: signed scores retain seat identity "+i,
    JSON.stringify([grid.players[i],grid.rows[0].cells[i].bid,grid.rows[0].cells[i].total]),JSON.stringify(body.children[i].children.map(cell=>cell.textContent)));
  check(players+"p: quiet legend follows table",grid.legend,result.children[1].textContent);
  const history=rentzHistoryGrid({...grid,rows:[...grid.rows,{...grid.rows[0],cards:2,status:"Whist"}]},element,historyGrid);
  check(players+"p: full history keeps contract labels",["1 · Levate","2 · Whist"],history.children[1].children.map(row=>row.children[0].textContent));
}
for(const rows of [[],[{cells:[]}],[{cells:[{},{},{}]},{cells:[{},{},{}]}]]) {
  assert.throws(()=>rentzScoreTable({grid:{players:["A","B","C"],rows}},element,sharedScoreGrid,"ro"), TypeError,"Reject missing, malformed or ambiguous current hand");
}
check("score adaptation uses shared score renderer",true,generatedShell.includes("rentzScoreTable(match.score.table, element, scoreGrid)"));
const {menuFromEntries}=await import(pathToFileURL(join(dirname(process.argv[3]),'table-menu.mjs')));
const menuRoutes=['score-history','score-sheet','stop-auto','settings','rankings','home'].map(id=>({id,label:id,action(){}}));
const nativeEntries=[{id:'score-history',label:'Istoricul jocului'},{id:'home',label:'Meniul principal'},{id:'stop-auto',label:'Oprește jocul automat'},{id:'settings',label:'Nume'}];
check('Native descriptor order excludes unsupported in-game routes',nativeEntries.map(e=>e.id),menuFromEntries(menuRoutes,nativeEntries).map(e=>e.id));
check('Native descriptor labels remain authoritative',nativeEntries.map(e=>e.label),menuFromEntries(menuRoutes,nativeEntries).map(e=>e.label));
refuses('An unknown native intention fails closed',()=>menuFromEntries(menuRoutes,[{id:'unknown',label:'Unknown'}]));
check('Shared menu renderer consumes the same native entries',true,generatedShell.includes('menuFromEntries(routes, match.menuEntries)'));
check("full history keeps its renderer",true,generatedShell.includes("rentzHistoryGrid(data.grid, element, historyGrid)"));
check("contributions remain available in quiet details",true,generatedShell.includes("rentzScoreRules(details)"));
check("score values override the shared bold human cells",true,/\.whist-score \.rentz-score-table \.whist-score-grid :is\(th, td\)\s*\{[^}]*font-weight: 400/.test(generatedRentzCss));
const sharedLegalCardRule = generatedTableCss.match(
  /#table:not\(\[data-automatic\]\) \.whist-match \.fan \.legal-card:not\(\.suggested-card\)\s*\{([^}]*)\}/);
check("shared shell keeps the manual legal-card cue available as a comparison", true,
  !!sharedLegalCardRule && sharedLegalCardRule[1].includes("border: 2px solid #dcb569"));
const rentzLegalCardRule = generatedRentzCss.match(
  /#table \.whist-match\[data-rentz-sequence="true"\] \.fan \.card\.legal-card\s*\{([^}]*)\}/);
check("legal-card cue does not require CSS :has on older mobile Chrome",true,
  generatedShell.includes('content.dataset.rentzSequence = "true";'));
check("Rentz legal-card styling is scoped to the Rentz sequence and survives automatic mode", true,
  !!rentzLegalCardRule
    && rentzLegalCardRule[1].includes("background: var(--whist-hint-legal-fill)")
    && rentzLegalCardRule[1].includes("border: var(--whist-hint-stroke) solid var(--whist-hint-legal-line)"));
check("Rentz legal-card cue uses the Android fill and outline tokens in manual and automatic mode", true,
  !!rentzLegalCardRule
    && generatedTableCss.includes("--whist-hint-legal-fill")
    && generatedTableCss.includes("--whist-hint-legal-line")
    && generatedTableCss.includes("--whist-hint-stroke"));
check("published page loads the Rentz cue after the shared card-table stylesheet", true,
  (() => {
    const sharedStyle = generatedPage.match(/<link rel="stylesheet" href="\.\/table\.css(?:\?[^\"]*)?">/);
    const rentzStyle = generatedPage.match(/<link rel="stylesheet" href="\.\/rentz\.css(?:\?[^\"]*)?">/);
    return !!sharedStyle && !!rentzStyle && rentzStyle.index > sharedStyle.index;
  })());
check("Rentz header removes the extra product title used by the generic Whist shell", true,
  generatedShell.includes('title.hidden = match.header.mode === "RENTZ";'));
check("Rentz header omits the trump panel and preserves the selected-game line", true,
  generatedShell.includes('top.insertBefore(status, title); details.hidden = true;')
    && generatedShell.includes('else details.append(status, trump);'));
check("Rentz render derives four-player card-space insets from the actual seat count", true,
  generatedShell.includes("const insets = rentzCenterInsets(match.seats.length);")
    && generatedShell.includes('center.style.setProperty("--rentz-center-top", `${insets.top}px`);'));
check("Rentz playback receives the pre-action auto-play checkbox marker", true,
  generatedShell.includes('autoStepAction: state.view?.actions?.find(move => move.key === "auto-step"),'));
check("auto-play checkbox is disabled, not removed, during the terminal arrival pause", true,
  generatedShell.includes('input.disabled = !!match.rentzArrival;'));

const autoStepAction = { key: "auto-step", label: "Joc automat" };
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
      { name: "Ioana", firstFact: "Mână 8", secondFact: "Σ: 50", active: false },
    ],
  },
};
const arrival = { winner: "Ana", unplayedCards: 7, previousTotals: [100, -200, 50], durationMillis: 1800 };
const waiting = rentzArrivalView(settled, arrival, "ro");
check("arrival view suspends the score only in its render copy", null, waiting.view.score);
check("arrival view withholds the score details", null, waiting.view.scoreDetails);
check("arrival view hides next-hand actions", [], waiting.view.actions);
const waitingWithAuto = rentzArrivalView(settled, arrival, "ro", autoStepAction);
check("arrival keeps the auto-play checkbox visible using its marker", [autoStepAction], waitingWithAuto.view.actions);
check("arrival view has no stale completion prompt", "", waiting.view.prompt);
check("arrival seats show pending hand points and pre-hand totals", [
  ["Δ: …", "Σ: 100", false], ["Δ: …", "Σ: -200", false], ["Δ: …", "Σ: 50", false],
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
const arrivalActions = [];
const presented = await presentRentzArrival({ ...settled, rentzArrival: arrival }, "ro", {
  autoStepAction,
  stopAutomation: () => lifecycle.push("stop"),
  render: async state => {
    arrivalActions.push(state.view.actions.map(action => action.key));
    lifecycle.push(state.view.score ? "score" : "arrival");
  },
  wait: async duration => lifecycle.push(`wait:${duration}`),
});
check("arrival lifecycle is activated", true, presented);
check("arrival lifecycle disables auto, renders the pause, waits, then restores score",
  ["stop", "arrival", "wait:1800", "score"], lifecycle);
check("auto-play remains visible as an action marker during the disabled arrival pause",
  [["auto-step"], ["next"]], arrivalActions);
const ignoredLifecycle = [];
check("ordinary result skips Rentz arrival lifecycle", false, await presentRentzArrival(
  { ...settled, rentzArrival: undefined }, "ro", {
    stopAutomation: () => ignoredLifecycle.push("stop"), render: async () => ignoredLifecycle.push("render"),
    wait: async () => ignoredLifecycle.push("wait"),
  }));
check("ordinary result has no transient rendering effects", [], ignoredLifecycle);

const playbackLife = [];
const playbackActions = [];
const playbackRanks = ["J", "Q", "K", "A", "9", "10"];
const playbackSuits = ["♣", "♦", "♥", "♠"];
const publicSnapshot = (heading, playedLabels, activeSeat, handLabels = []) => ({
  ...settled.view,
  heading,
  hand: handLabels.map(label => ({ label, action: `card:${label}` })),
  actions: [autoStepAction, { key: "card:must-be-hidden", label: "hidden during playback" }],
  seats: settled.view.seats.map((seat, index) => ({ ...seat, active: index === activeSeat })),
  rentzSequence: playbackSuits.map(suit => ({ suit, cells: playbackRanks.map(rank => ({
    label: `${rank}${suit}`, played: playedLabels.includes(`${rank}${suit}`),
  })) })),
});
const sequencePlayback = {
  ...settled,
  rentzPlayback: [
    { actorSeat: 0, delayMillis: 0, before: publicSnapshot("before-human", [], 0, ["J♣", "Q♣"]),
      after: publicSnapshot("after-human", ["J♣"], 1, ["Q♣"]) },
    { actorSeat: 2, delayMillis: 550, before: publicSnapshot("before-bot", ["J♣"], 2, ["Q♣"]),
      after: publicSnapshot("after-bot", ["J♣", "K♣"], 0, ["Q♣"]) },
  ],
};
const playedBack = await presentRentzPlayback(sequencePlayback, {
  autoStepAction,
  stopAutomation: () => playbackLife.push("stop"),
  render: async state => {
    const view = state.view;
    playbackActions.push(view.actions.map(action => action.key));
    playbackLife.push(`render:${view.heading}:${view.seats.findIndex(seat => seat.active)}:${view.rentzSequence[0].cells.filter(cell => cell.played).length}:${view.actions.length}:${view.score}`);
  },
  nextFrame: async () => playbackLife.push("visible-frame"),
  wait: async duration => playbackLife.push(`wait:${duration}`),
});
check("incremental Rentz replay activates only when public snapshots exist", true, playedBack);
check("manual play is shown immediately, then bot actor is visible for one J4 interval before its card without cancelling auto-play",
  ["render:after-human:1:1:1:null", "render:before-bot:2:1:1:null", "visible-frame",
    "wait:550", "render:after-bot:0:2:1:null"], playbackLife);
check("the Joc automat checkbox action stays visible while Rentz cards animate and card actions stay hidden",
  [["auto-step"], ["auto-step"], ["auto-step"]], playbackActions);
check("empty Rentz replay has no delay, render, or automation side effects", false,
  await presentRentzPlayback({ ...settled, rentzPlayback: [] }, {
    stopAutomation: () => playbackLife.push("unexpected-stop"), render: async () => playbackLife.push("unexpected-render"),
    nextFrame: async () => playbackLife.push("unexpected-frame"), wait: async () => playbackLife.push("unexpected-wait"),
  }));
check("empty replay leaves the lifecycle trace untouched", 5, playbackLife.length);

await rejectedPlaybackIsAtomic("a malformed later frame", [
  ...sequencePlayback.rentzPlayback,
  { actorSeat: 1, delayMillis: 550,
    before: publicSnapshot("before-missing-card", ["J♣", "K♣"], 1, ["Q♣"]),
    after: publicSnapshot("after-missing-card", ["J♣", "K♣"], 0, ["Q♣"]) },
], "exactly one new card");
const humanCardStillInHand = {
  ...sequencePlayback.rentzPlayback[0],
  after: publicSnapshot("duplicated-human-card", ["J♣"], 1, ["J♣", "Q♣"]),
};
await rejectedPlaybackIsAtomic("a played card duplicated between hand and board", [humanCardStillInHand],
  "both private and on the public sequence");
for (const field of ["opponentHands", "opponent_hands", "cardsBySeat"]) {
  const privateOpponentFrame = {
    ...sequencePlayback.rentzPlayback[0],
    before: { ...sequencePlayback.rentzPlayback[0].before, nested: { [field]: [] } },
  };
  await rejectedPlaybackIsAtomic(`a private card collection field (${field})`, [privateOpponentFrame],
    "private opponent-hand field");
}

const terminalSequence = publicSnapshot("terminal", ["J♣", "Q♣"], null).rentzSequence;
const terminalSnapshot = (heading, playedLabels, activeSeat) => ({
  ...publicSnapshot(heading, playedLabels, activeSeat),
  actions: [{ key: "next", label: "must be hidden" }],
});
const terminalResult = {
  ...settled,
  view: { ...settled.view, rentzSequence: terminalSequence },
  rentzPlayback: [{
    actorSeat: 1, delayMillis: 550,
    before: terminalSnapshot("before-final-card", ["J♣"], 1),
    after: terminalSnapshot("after-final-card", ["J♣", "Q♣"], null),
  }],
  rentzArrival: arrival,
};
const terminalLife = [];
const terminalRender = async state => {
  const view = state.view;
  const cards = view.rentzSequence?.flatMap(row => row.cells).filter(cell => cell.played).length ?? 0;
  terminalLife.push(view.score ? "score" : view.rentzArrival ? `arrival:${cards}:${view.actions.length}` : `board:${cards}`);
};
await presentRentzPlayback(terminalResult, {
  stopAutomation: () => terminalLife.push("unexpected-stop-playback"), render: terminalRender,
  nextFrame: async () => terminalLife.push("visible-frame"), wait: async duration => terminalLife.push(`wait:${duration}`),
});
await presentRentzArrival(terminalResult, "ro", {
  stopAutomation: () => terminalLife.push("stop-arrival"), render: terminalRender,
  wait: async duration => terminalLife.push(`wait:${duration}`),
});
check("the last Rentz card remains on the board through the native arrival pause before score appears",
  ["board:1", "visible-frame", "wait:550", "board:2",
    "stop-arrival", "arrival:2:0", "wait:1800", "score"], terminalLife);

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
