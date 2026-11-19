import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {pathToFileURL} from 'node:url';
const [site,contracts,nativeFile,output]=process.argv.slice(2);
const load=p=>import(pathToFileURL(p));
const {withBrowser}=await load(path.join(contracts,'fixtures.mjs'));
const {verifyScoreActionTypography}=await load(path.join(contracts,'score-presentation.mjs'));
const engine=await load(path.join(site,'rentz-engine.mjs'));engine.initialize(0,31);
const setup=JSON.parse(engine.commandLocal('/api/setup','{}',null,null,null,null)).result;
const native=JSON.parse(fs.readFileSync(nativeFile,'utf8').replace(/^\uFEFF/,''));
const palette=Object.fromEntries(setup.palette.map(row=>[row.name,row.color]));
const tokens=Object.fromEntries(setup.palette.filter(r=>r.name.startsWith('--score-text-')).map(r=>[r.name,r.color]));
assert.deepEqual(tokens,native.scoreTypography,'Actual Android AAR and compiled browser roles must agree');
const shell=fs.readFileSync(path.join(site,'table.mjs'),'utf8');
function fragment(start,end){const from=shell.indexOf(start),to=shell.indexOf(end,from);assert.ok(from>=0&&to>from);return shell.slice(from,to);}
const render=fragment('function whistScore(','function writeSeatFact('),grid=fragment('function scoreGrid(','function showHistory(');
const helpers=fs.readFileSync(path.join(site,'rentz-score.mjs'),'utf8').replace(/^export /gm,'');
const css=fs.readFileSync(path.join(site,'table.css'),'utf8')+'\n'+fs.readFileSync(path.join(site,'rentz.css'),'utf8');
const browser=process.env.ATF_BROWSER_EXECUTABLE??'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
fs.mkdirSync(output,{recursive:true});const cases=[];
await withBrowser(browser,path.join(output,'browser-profile'),async({send,evaluate})=>{
 await send('Page.enable');
 for(const language of ['ro','en'])for(const width of [280,414,760])for(const root of [16,24,32]){
  const height=width===280?568:896;
  await send('Emulation.setDeviceMetricsOverride',{width,height,deviceScaleFactor:1,mobile:true});
  const copy=setup.copy[language];
  const observed=await evaluate(`(()=>{
   document.documentElement.style.fontSize=${JSON.stringify(root+'px')};
   for(const [key,value] of Object.entries(${JSON.stringify(palette)}))document.documentElement.style.setProperty(key,value);
   document.head.replaceChildren(Object.assign(document.createElement('meta'),{name:'viewport',content:'width=device-width, initial-scale=1'}),Object.assign(document.createElement('style'),{textContent:${JSON.stringify(css)}}));document.body.replaceChildren();
   function element(tag,text='',type=''){const node=document.createElement(tag);node.textContent=text;node.className=type;return node;}
   function button(text,fn,type=''){const node=element('button',text,type);node.onclick=fn;return node;}
   const stage=element('main');stage.id='table';stage.dataset.surface='whist';stage.style.setProperty('--stage-width','${width}px');stage.style.setProperty('--stage-height','${height}px');document.body.append(stage);
   const captions=${JSON.stringify(copy)},events=[],copy=(key,fallback)=>captions[key]??fallback;
   const automation={set:()=>{}},errorBox=element('p'),refreshAutomationGate=()=>{};
   const menu=()=>events.push('home'),showHistory=()=>events.push('history');
   const profileDialogs={message:()=>events.push('details')};
   const control=move=>button(move.label,()=>events.push(move.key),'primary');
   ${helpers}
   ${grid}
   ${render}
   const names=Array.from({length:6},(_,i)=>'Player '+i),values=names.map((name,i)=>[name,String(-25*i),String(120-50*i)]);
   whistScore({heading:'6 / 24',prompt:'',score:{title:copy('scoreSheet','Punctajul mâinii'),table:{headings:['Player','Δ:','Σ:'],rows:values,
    grid:{players:names,legend:'Δ: / Σ:',rows:[{cells:values.map(v=>({bid:v[1],total:v[2]}))}]}}},
    scoreDetails:{summary:'Rentz',rules:['Confirmed contributions']},history:{},actions:[{key:'next',label:copy('nextHand','Mâna următoare')}]});
   const buttons=[...stage.querySelectorAll('button')];
   const result={viewport:{width:innerWidth,scale:visualViewport.scale,stageWidth:stage.getBoundingClientRect().width},buttons:buttons.map(node=>({label:node.textContent,quiet:node.classList.contains('quiet'),size:parseFloat(getComputedStyle(node).fontSize),
    height:node.getBoundingClientRect().height,overflow:node.scrollWidth>node.clientWidth+1})),horizontalOverflow:stage.scrollWidth-stage.clientWidth};
   result.reachable=buttons.every(node=>{node.scrollIntoView({block:'center'});const r=node.getBoundingClientRect(),s=stage.getBoundingClientRect();return r.top>=Math.max(0,s.top)-1&&r.bottom<=Math.min(innerHeight,s.bottom)+1;});stage.scrollTop=0;
   buttons.forEach(node=>node.click());result.events=events;return result;
  })()`);
  const verified=verifyScoreActionTypography(tokens,root,observed.buttons);
  assert.deepEqual(observed.viewport,{width,scale:1,stageWidth:width},'Mobile viewport must preserve the visible font size');assert.deepEqual(observed.events,['details','next','history','home']);assert.equal(observed.reachable,true);assert.ok(observed.horizontalOverflow<=1);
  const images=[];
  if(width===414&&[16,32].includes(root)){const file=`rentz-score-${language}-${root}.png`;const image=await send('Page.captureScreenshot',{format:'png'});fs.writeFileSync(path.join(output,file),Buffer.from(image.data,'base64'));images.push({file,title:`Rentz ${language}, ${root}px accessible root`});}
  cases.push({id:'RENTZ-SCORE-ACTIONS',name:`Rentz score actions ${language} ${width}x${height} root ${root}`,status:'PASS',
   expected:{primary:24*root/16,secondary:21*root/16,minTouch:48,callbacks:['details','next','history','home'],reachable:true},observed:{...observed,...verified},images});
 }
});
fs.writeFileSync(path.join(output,'browser-checks.json'),JSON.stringify(cases,null,2)+'\n');
console.log(JSON.stringify({status:'PASS',profiles:cases.length,actualNativeAndBrowserRoles:Object.keys(tokens).length}));
