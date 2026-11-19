import {readFileSync} from 'node:fs';
import {dirname,join} from 'node:path';
import {pathToFileURL} from 'node:url';
import {componentAudit} from './component-audit.mjs';

const [shell,contracts]=process.argv.slice(2);
if(!shell||!contracts)throw Error('Expected the prepared table module and extracted shared test JAR');
const {verifyTrickPlayback,verifyCollectionPoses}=await import(pathToFileURL(join(contracts,'trick-playback.mjs')));
const turns=await verifyTrickPlayback(readFileSync(shell,'utf8'));
const poses=await verifyCollectionPoses(readFileSync(join(dirname(shell),'collection.mjs'),'utf8'));
const check=componentAudit('trick-playback');
for(const row of turns.observations)check(row.name,row.expected,row.actual);
for(const row of poses.observations)check(`Measured collection ${row.players}/${row.scale}/${row.reduced}`,
  {animatedFaces:row.reduced?0:row.players,hold:1500,sourceFacesUnchanged:true},
  {animatedFaces:row.animatedFaces,hold:row.hold,sourceFacesUnchanged:row.sourceFacesUnchanged});
console.log(`PASS shared trick exports: ${turns.scenarios} playback scenarios, ${poses.scenarios} measured collection scenarios`);
