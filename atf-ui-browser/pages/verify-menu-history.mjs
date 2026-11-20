import fs from 'node:fs';import path from 'node:path';import assert from 'node:assert/strict';import {pathToFileURL} from 'node:url';
const [site,contracts,nativeFile,output]=process.argv.slice(2);const load=file=>import(pathToFileURL(file));
const {withBrowser}=await load(path.join(contracts,'fixtures.mjs'));
const {verifyMenuEntries,verifyHistoryMeasurements}=await load(path.join(contracts,'menu-history-contract.mjs'));
const {menuFromEntries}=await load(path.join(site,'table-menu.mjs'));
const native=JSON.parse(fs.readFileSync(nativeFile,'utf8').replace(/^\uFEFF/,''));
const engine=await load(path.join(site,'rentz-engine.mjs'));engine.initialize(0,31);
const setup=JSON.parse(engine.commandLocal('/api/setup','{}',null,null,null,null)).result;
const palette=Object.fromEntries(setup.palette.map(row=>[row.name,row.color]));
const css=fs.readFileSync(path.join(site,'table.css'),'utf8')+'\n'+fs.readFileSync(path.join(site,'rentz.css'),'utf8');
const records=fs.readFileSync(path.join(site,'records.mjs'),'utf8');
const grid=records.slice(records.indexOf('export function historyGrid('),records.indexOf('export function rankingPanel(')).replaceAll('export function','function');
const shell=fs.readFileSync(path.join(site,'table.mjs'),'utf8');const header=shell.slice(shell.indexOf('function whistHeader('),shell.indexOf('\nfunction whistScore('));
assert.ok(header.length>100);fs.mkdirSync(output,{recursive:true});const observations=[];
for(const plan of native.menuPlans)verifyMenuEntries(menuFromEntries,plan.entries);
await withBrowser(process.env.ATF_BROWSER_EXECUTABLE??'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',path.join(output,'browser-profile'),async({send,evaluate})=>{
 await send('Page.enable');
 for(const plan of native.menuPlans)for(const automatic of [false,true]){
  await send('Emulation.setDeviceMetricsOverride',{width:414,height:896,deviceScaleFactor:1,mobile:true});
  const actual=await evaluate(`(()=>{
   document.head.replaceChildren(Object.assign(document.createElement('meta'),{name:'viewport',content:'width=device-width, initial-scale=1'}),Object.assign(document.createElement('style'),{textContent:${JSON.stringify(css)}}));document.body.replaceChildren();document.documentElement.style.fontSize='16px';
   for(const [key,value]of Object.entries(${JSON.stringify(palette)}))document.documentElement.style.setProperty(key,value);
   function element(tag,text='',type=''){const node=document.createElement(tag);node.textContent=text;node.className=type;return node;}
   function button(text,action,type=''){const node=element('button',text,type);node.onclick=action;return node;}
   const calls=[],pending=false,copy=(key,fallback)=>(${JSON.stringify(setup.copy[plan.language])})[key]??fallback;
   const automation={selected:()=>${automatic},set:()=>calls.push('stop-auto')},refreshAutomationGate=()=>{},isRedSuit=()=>false;
   const profileDialogs={liveScore:()=>calls.push('score-sheet'),edit:()=>calls.push('settings')},scoreGrid=()=>element('table');
   const menu=()=>calls.push('home'),showHistory=()=>calls.push('score-history'),showRankings=()=>calls.push('rankings');
   const state={profile:{name:'Fixture'}};
   const historyFirst=(label,action,others)=>[{id:'score-history',label,action},...others];
   const menuFromEntries=${menuFromEntries.toString()};
   ${header}
   const match={header:{mode:'RENTZ',title:'Rentz',titleSize:22,progressSize:14,bidSize:12,progress:'1/24',bids:'Whist',trump:'—',trumpDescription:'Contract'},
    center:[],actions:[],menuEntries:${JSON.stringify(plan.entries)},history:{rows:[{}]},scoreDetails:{liveTable:{}}};
   const view=whistHeader(match);document.body.append(view);const nav=view.querySelector('details');nav.open=true;
   const buttons=[...view.querySelectorAll('.whist-menu-items button')].filter(button=>!button.hidden);
   const result={entries:buttons.map(button=>({id:button.classList.contains('stop-auto')?'stop-auto':button.dataset.menuAction,label:button.textContent}))};
   buttons.forEach(button=>button.click());result.calls=calls;return result;
  })()`);
  const expected=plan.entries.filter(e=>automatic||e.id!=='stop-auto');assert.deepEqual(actual.entries,expected);assert.deepEqual(actual.calls,expected.map(e=>e.id));
  observations.push({id:'RENTZ-MENU',name:`Rentz menu ${plan.language}, settled ${plan.settled}, AutoPlay ${automatic}`,status:'PASS',expected:{entries:expected,calls:expected.map(e=>e.id)},observed:actual,images:[]});
 }
 for(const language of ['ro','en'])for(const players of [3,4,5,6])for(const width of [280,414,760])for(const font of [16,32]){
  await send('Emulation.setDeviceMetricsOverride',{width,height:896,deviceScaleFactor:1,mobile:true});
  const actual=await evaluate(`(()=>{
   document.body.replaceChildren();document.documentElement.style.fontSize='${font}px';
   function element(tag,text='',type=''){const node=document.createElement(tag);node.textContent=text;node.className=type;return node;}
   ${grid}
   const dialog=element('dialog');dialog.id='history';applyHistoryAppearance(dialog,${JSON.stringify(native.historyAppearance)});
   dialog.append(element('h2',${JSON.stringify(setup.copy[language].history)}));document.body.append(dialog);
   const pane=element('div');pane.style.cssText='max-width:100%;min-width:0;overflow:auto';dialog.append(pane);
   const players=Array.from({length:${players}},(_,i)=>'Player '+i),data={players,cardsLabel:'Hand',bidLabel:'Δ',pointsLabel:'Σ',appearance:${JSON.stringify(native.historyAppearance)},
    rows:[{cards:1,status:'Confirmed',cells:players.map(()=>({bid:'−20',total:'−120',description:'Fixture confirmed result',missed:false,series:0}))}]};
   const table=historyGrid(data,element);pane.append(table);dialog.showModal();
   const names=[...table.querySelectorAll('thead tr:first-child th')],groups=[...table.querySelectorAll('.history-seat')];pane.scrollLeft=pane.scrollWidth;
   const edge=pane.getBoundingClientRect(),last=names.at(-1).getBoundingClientRect(),hex=c=>'#'+c.match(/\\d+/g).slice(0,3).map(n=>Number(n).toString(16).padStart(2,'0')).join('');
   const result={weights:names.map(n=>getComputedStyle(n).fontWeight),titleWeight:getComputedStyle(dialog.querySelector('h2')).fontWeight,
    dividers:groups.map(n=>parseFloat(getComputedStyle(n).borderLeftWidth)),colors:groups.map(n=>hex(getComputedStyle(n).borderLeftColor)),
    lastPlayerVisible:last.left>=edge.left-1&&last.right<=edge.right+1,values:table.querySelector('tbody').textContent};pane.scrollLeft=0;return result;
  })()`);
  verifyHistoryMeasurements(actual,native.historyAppearance);assert.equal(actual.titleWeight,'400');assert.ok(actual.values.includes('−120'));
  const images=[];if(players===4&&width===414){const file=`rentz-history-${language}-${font}.png`;const png=await send('Page.captureScreenshot',{format:'png'});fs.writeFileSync(path.join(output,file),Buffer.from(png.data,'base64'));images.push({file,title:`Rentz history ${language}, accessible root ${font}`});}
  observations.push({id:'RENTZ-HISTORY',name:`Rentz history ${language} ${players} players ${width}px root ${font}`,status:'PASS',expected:{appearance:native.historyAppearance,titleWeight:'400',lastPlayerVisible:true},observed:actual,images});
 }
});
fs.writeFileSync(path.join(output,'browser-checks.json'),JSON.stringify(observations,null,2)+'\n');console.log(JSON.stringify({status:'PASS',cases:observations.length,captures:observations.reduce((n,row)=>n+row.images.length,0)}));
