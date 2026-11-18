import {readFileSync} from "node:fs";
import {join} from "node:path";
import {createHash} from "node:crypto";
import {packageAssets} from "./package-assets.mjs";
import {componentAudit} from "./component-audit.mjs";

const check = componentAudit("browser-asset-updates");
const graph = new Map(Object.entries({
  "index.html":'<link href="./table.css?v=0.2.13"><script src="./table.mjs?v=0.2.13"></script>',
  "table.mjs":'import {score} from "./score.mjs"; import("./transport.mjs");',
  "score.mjs":'export const score = "first";',
  "transport.mjs":'new Worker(new URL("./worker.mjs", import.meta.url));',
  "worker.mjs":'import "./engine.mjs";',
  "engine.mjs":'export const initialize = 1;',
  "table.css":'@font-face {src:url("./suits.woff2");}',
  "suits.woff2":"fixture font"
}).map(([name, text]) => [name, Buffer.from(text)]));
const first = packageAssets(graph);
const reordered = packageAssets(new Map([...graph].reverse()));
check("Input enumeration cannot change an identical bundle revision", first.revision, reordered.revision);
const changed = new Map(graph); changed.set("score.mjs", Buffer.from('export const score = "second";'));
const second = packageAssets(changed);
check("Changing only the score adapter invalidates the whole graph", false, first.revision === second.revision);
for (const name of ["index.html", "table.mjs", "transport.mjs", "worker.mjs", "table.css"]) {
  const text = second.files.get(name).toString();
  const refs = [...text.matchAll(/["'](\.\/[^"']+)["']/g)].map(match => match[1]);
  check(name+": local references keep the new build identity", true,
    refs.length > 0 && refs.every(ref=>new URL(ref, "https://example.test/").searchParams.get("build") === second.revision));
  check(name+": previous library-only URLs cannot remain cached", false, text.includes("?v=0.2.13"));
}
const cache = new Map();
for (const bundle of [first, second]) for (const [name, bytes] of bundle.files) cache.set(name+"?build="+bundle.revision, bytes.toString());
check("An already populated browser cache can resolve the new score module", 'export const score = "second";',
  cache.get("score.mjs?build="+second.revision));
for (const [label, input] of [["missing dependency",new Map([...graph].filter(([name])=>name!=="engine.mjs"))],
  ["already packaged output",second.files],["path traversal",new Map([["../outside.mjs",Buffer.from("")]])]]) {
  let failed = false; try {packageAssets(input);} catch {failed = true;}
  check("Reject "+label, true, failed);
}

// Independent read-back of the actual publication package, after its assembly.
if (process.argv[2]) {
  const root = process.argv[2];
  const manifest = JSON.parse(readFileSync(join(root, "asset-revision.json")));
  for (const file of manifest.files) {
    const bytes = readFileSync(join(root, file.path));
    const hash = createHash("sha256").update(bytes).digest("hex");
    check("Published bytes: "+file.path, file.sha256, hash);
    if (!/\.(html|mjs|css)$/.test(file.path)) continue;
    for (const match of bytes.toString().matchAll(/["'](\.\/[^"'\s]+\.(?:mjs|css|woff2)(?:\?[^"'\s]*)?)["']/g)) {
      const url = new URL(match[1], "https://example.test/");
      check(file.path+": dependency version for "+url.pathname, manifest.revision, url.searchParams.get("build"));
      check(file.path+": dependency exists "+url.pathname, true, manifest.files.some(file=>"/"+file.path===url.pathname));
    }
  }
}
console.log("PASS browser cache-coherent asset graph");
