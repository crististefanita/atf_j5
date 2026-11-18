import {readFileSync} from "node:fs";
import {runInNewContext} from "node:vm";
import {componentAudit} from "./component-audit.mjs";

// Execute the pinned renderer itself; mock only DOM transport, clock and collection completion.
const check = componentAudit("trick-playback");
const shell = readFileSync(process.argv[2], "utf8");
const source = shell.slice(shell.indexOf("async function playCards("), shell.indexOf("function applySeatFacts("));
for (const count of [3, 4, 5, 6]) for (const reduced of [false, true]) for (const humanLast of [false, true]) {
  const context = `${count}/${reduced}/${humanLast}`;
  const life = [], visible = [], seats = Array.from({length:count}, () => ({}));
  let collected, release;
  const done = new Promise(resolve => {release = resolve;});
  const center = {isConnected:true, replaceChildren() {visible.length = 0; life.push("clear");},
    append(card) {visible.push(card.seat); life.push("card:" + card.seat);}};
  const order = humanLast ? [...Array.from({length:count-1}, (_,i)=>i+1), 0]
    : Array.from({length:count}, (_,i)=>i);
  const cue = {cards:order.map(seat=>({seat})), holdMillis:1000, travelMillis:500,
    seatFacts:Array.from({length:count}, () => "after")};
  const steps = order.map((seat,index)=>({play:{seat,card:{rank:"J",suit:"C"}}, startsTrick:index===0,
    bids:"Levate",delayMillis:seat===0?0:550,seatFacts:seats.map(()=>"before"),
    collection:index===count-1?cue:null}));
  const vm = {stage:{dataset:{},querySelector:selector=>selector===".cards-on-table"?center:{},
      querySelectorAll:selector=>selector===".seat"?seats:[{dataset:{face:"JC"},remove(){life.push("hand-remove");}}]},
    matchMedia:()=>({matches:reduced}),placeTable:async()=>{},fitBidSummary:()=>{},
    markSeat:(node,active)=>{if(active) life.push("active:"+seats.indexOf(node));},
    applySeatFacts:()=>{},playedCard:play=>play,placePlayed:()=>{},
    state:{view:{seats}},setup:{},collectionFace:()=>{},
    requestAnimationFrame:callback=>callback(),setTimeout:(callback,delay)=>{life.push("wait:"+delay);callback();},
    collect:(_stage,value)=>{collected=value;life.push("collect");return {done};}};
  const run = runInNewContext("(" + source.trim() + ")", vm)(steps);
  for (let turn=0;turn<64 && !collected;turn++) await Promise.resolve();
  check(context+": all faces stay visible before collection", order, [...visible]);
  check(context+": exactly the accepted faces are collected", cue, collected);
  check(context+": next view cannot clear the held trick", 1, life.filter(x=>x==="clear").length);
  check(context+": every actor is marked before its card appears", true,
    order.every(seat=>life.indexOf("active:"+seat)<life.indexOf("card:"+seat)));
  check(context+": automated cards keep shared first-frame delay", count-1, life.filter(x=>x==="wait:550").length);
  check(context+": own card leaves the fan once", 1, life.filter(x=>x==="hand-remove").length);
  release(); await run;
  check(context+": table clears only after collection completes", 0, visible.length);
  check(context+": reduced motion still waits for collection completion", 2, life.filter(x=>x==="clear").length);
}
console.log("PASS pinned trick playback: 16 clock/DOM scenarios");
