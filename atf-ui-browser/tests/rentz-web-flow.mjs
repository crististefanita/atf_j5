/** Browser MCP acceptance. Uses public controls and rendered DOM only; never injects game state. */
export class RentzWebFlow {
  constructor(tab, capture) {
    this.tab = tab;
    this.capture = capture;
    this.points = [];
    this.actions = [];
    this.completed = [];
  }
  async observe(title, checks) {
    const point = {title, checks, actions:this.actions.splice(0),
      snapshot:await this.tab.playwright.domSnapshot()};
    await this.capture(point, await this.tab.screenshot({fullPage:false}));
    this.points.push(point);
    if (checks.some(check=>!check.passed)) throw new Error(`Failed browser checkpoint: ${title}`);
  }
  async click(name) {
    await this.tab.playwright.getByRole('button',{name,exact:true}).click();
    this.actions.push(`Click ${name}`);
    return this.tab.playwright.domSnapshot();
  }
  async start(automatic = true) {
    await this.click('Partidă nouă');
    await this.tab.playwright.getByRole('combobox',{name:'Jucători',exact:true}).selectOption({label:'4'});
    await this.tab.playwright.getByRole('combobox',{name:'Nivelul adversarilor',exact:true}).selectOption({label:'Greu'});
    await this.click('Începe partida');
    await this.click('Arată cărțile');
    await this.tab.playwright.getByRole('combobox',{name:'Alege sub-jocul',exact:true}).selectOption({label:'Rentz'});
    await this.tab.playwright.getByRole('group',{name:'Șirurile Rentz',exact:true}).waitFor({state:'visible'});
    this.actions.push('Select Rentz after reveal');
    await this.geometry('Four-seat Rentz geometry before play');
    if (automatic) await this.automatic();
  }
  async automatic() {
    await this.tab.playwright.locator('#table[aria-busy="true"]').waitFor({state:'hidden'});
    await this.tab.playwright.getByRole('checkbox',{name:'Joc automat',exact:true}).check();
    this.actions.push('Enable Joc automat through checkbox');
  }
  async waitForScore() {
    try {
      await this.tab.playwright.getByRole('heading',{name:'Rezultatul mâinii',exact:true}).waitFor({state:'visible',timeout:40000});
      return true;
    } catch (error) {
      if (!/timeout|timed out/i.test(String(error))) throw error;
      return false;
    }
  }
  async geometry(title) {
    const geometry = await this.tab.playwright.evaluate(()=> {
      const rect = e => {const r=e.getBoundingClientRect(); return {left:r.left,top:r.top,right:r.right,bottom:r.bottom};};
      const panel=document.querySelector('.rentz-sequence');
      return {seats:Array.from(document.querySelectorAll('.seat')).map(rect), panel:rect(panel),
        logicalWidth:panel.clientWidth,logicalFont:+getComputedStyle(panel).getPropertyValue('--rentz-label-size').replace('px',''),
        scale:panel.getBoundingClientRect().width/panel.clientWidth,
        cards:Array.from(panel.querySelectorAll('.played')).map(rect),
        rows:panel.querySelectorAll('.rentz-sequence-row').length,
        automatic:!!document.querySelector('input[type="checkbox"]')};
    });
    const overlaps=(a,b)=>a.left<b.right && a.right>b.left && a.top<b.bottom && a.bottom>b.top;
    const freeHeight=geometry.seats[0].top-Math.max(...geometry.seats.slice(1).map(s=>s.bottom))-24*geometry.scale;
    const expectedHeight=Math.min(248*geometry.scale,Math.max(0,freeHeight));
    await this.observe(title,[comparison('Four seats',4,geometry.seats.length),
      comparison('Four stable suit rows',4,geometry.rows),
      comparison('Board does not intersect any player',false,geometry.seats.some(seat=>overlaps(seat,geometry.panel))),
      comparison('Played card faces do not intersect players',false,geometry.cards.some(card=>geometry.seats.some(seat=>overlaps(seat,card)))),
      comparison('Auto checkbox remains present',true,geometry.automatic),
      comparison('Rentz uses available space for four full card rows',true,geometry.panel.bottom-geometry.panel.top>=expectedHeight-2*geometry.scale),
      comparison('Card indices are measured before viewport scaling',true,geometry.logicalFont>=(geometry.logicalWidth>=300?12:10))]);
  }
  async table(selector) {
    return this.tab.playwright.locator(selector).evaluate(table=>({
      labels:Array.from(table.tHead.rows[1].cells).slice(1).map(c=>c.textContent.trim()),
      names:Array.from(table.tHead.rows[0].cells).slice(1).map(c=>c.textContent.trim()),
      rows:Array.from(table.tBodies[0].rows).map(row=>({contract:row.cells[0].textContent.trim(),
        values:Array.from(row.cells).slice(1).map(c=>Number(c.textContent.trim()))}))
    }));
  }
  async score(number) {
    const table=await this.table('.whist-score .record-history');
    const text=await this.tab.playwright.locator('.whist-score-awards').innerText();
    const checks=[comparison('One confirmed result row',1,table.rows.length),
      comparison('Two compact headings per seat',['P','T','P','T','P','T','P','T'],table.labels),
      comparison('Four named players',4,table.names.length),
      comparison('One P/T pair per seat',8,table.rows[0].values.length),
      comparison('Legend below the result',true,await this.tab.playwright.getByText('P: punctele mâinii · T: total după mână',{exact:true}).isVisible())];
    const values=table.rows[0].values;
    const deltas=values.filter((_,i)=>i%2===0);
    if(number===1) {
      checks.push(comparison('Rentz completed first','1 · Rentz',table.rows[0].contract),
        comparison('Rentz four-seat finish awards',[0,100,200,300],[...deltas].sort((a,b)=>a-b)),
        comparison('Exactly one Rentz player scores zero',1,deltas.filter(x=>x===0).length));
      this.first=table;
    }
    if(table.rows[0].contract.includes('Rentz')) checks.push(
      comparison('Exactly one zero in every completed Rentz contract',1,deltas.filter(x=>x===0).length));
    const previous=this.completed.at(-1);
    for(let seat=0;seat<4;seat++) {
      checks.push(comparison(`Result details match history for ${table.names[seat]}`,true,
        text.includes(`${table.names[seat]} · P ${values[seat*2]} · T ${values[seat*2+1]}`)),
        comparison(`Cumulative points at seat ${seat}`,(previous?.rows[0].values[seat*2+1]??0)+deltas[seat],values[seat*2+1]));
    }
    if(number===2) this.second=table;
    await this.observe(`Complete contract ${number}: ${table.rows[0].contract}`,checks);
    this.completed.push(table);
    return table;
  }
  async next() {
    await this.click('Mâna următoare');
    await this.tab.playwright.getByRole('heading',{name:'Rezultatul mâinii',exact:true}).waitFor({state:'hidden'});
    // Opponent chooses the next contract through the real engine; its cards remain hidden.
    await this.tab.playwright.getByRole('checkbox',{name:'Joc automat',exact:true}).waitFor({state:'visible'});
    await this.automatic();
  }
  async history() {
    await this.click('Istoricul scorului');
    const table=await this.table('dialog[open] .record-history');
    const expected=this.completed.map(result=>result.rows[0]);
    await this.observe('Full mixed-contract P/T history',[
      comparison('At least two distinct sub-games completed',true,new Set(expected.map(row=>row.contract.split(' · ')[1])).size>=2),
      comparison('All completed contracts in chronological order',expected,table.rows),
      comparison('Seat order remains stable',this.first.names,table.names),
      comparison('P/T headings preserved',this.first.labels,table.labels),
      comparison('Legend visible below history',true,await this.tab.playwright.getByText('P: punctele mâinii · T: total după mână',{exact:true}).last().isVisible())]);
    await this.click('Închide');
  }
  async restore() {
    await this.tab.reload();
    await this.tab.playwright.getByRole('button',{name:'Continuă partida',exact:true}).waitFor({state:'visible'});
    await this.click('Continuă partida');
    await this.tab.playwright.getByRole('heading',{name:'Rezultatul mâinii',exact:true}).waitFor({state:'visible'});
    await this.history();
  }
}
function comparison(name,expected,actual) {
  return {name,expected:JSON.stringify(expected),actual:JSON.stringify(actual),passed:JSON.stringify(expected)===JSON.stringify(actual)};
}
