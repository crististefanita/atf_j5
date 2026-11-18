import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import assert from 'node:assert/strict';

/** Records evaluated comparisons before assertions can throw; never reconstructs a verdict from logs. */
export function componentAudit(name) {
  const checks=[];
  process.on('exit',()=>{
    if(!process.env.RENTZ_BROWSER_AUDIT) return;
    mkdirSync(process.env.RENTZ_BROWSER_AUDIT,{recursive:true});
    writeFileSync(join(process.env.RENTZ_BROWSER_AUDIT,`${name}.json`),JSON.stringify({name,checks},null,2));
  });
  return (name,expected,actual)=> {
    let passed=true;
    try { assert.deepEqual(actual,expected); } catch { passed=false; }
    checks.push({name,expected:JSON.stringify(expected)??String(expected),actual:JSON.stringify(actual)??String(actual),passed});
    assert.deepEqual(actual,expected,name);
  };
}
