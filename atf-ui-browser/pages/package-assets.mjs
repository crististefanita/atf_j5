import {createHash} from "node:crypto";
import {readFileSync, readdirSync, statSync, writeFileSync} from "node:fs";
import {join, resolve} from "node:path";
import {pathToFileURL} from "node:url";

const sha = bytes => createHash("sha256").update(bytes).digest("hex");

/** Version the entire local module graph, including the worker, after compilation. */
export function packageAssets(inputs) {
  if (inputs.has("asset-revision.json")) throw new Error("A fresh compiled site is required");
  const names = [...inputs.keys()].sort();
  if (names.some(name => !/^[\w.-]+$/.test(name))) throw new Error("Flat asset names required");
  const digest = createHash("sha256");
  for (const name of names) digest.update(name + "\0" + sha(inputs.get(name)) + "\n");
  const revision = digest.digest("hex").slice(0, 20);
  const files = new Map();
  for (const name of names) {
    let bytes = inputs.get(name);
    if (/\.(html|css|mjs)$/.test(name)) {
      const text = bytes.toString().replace(/(["'])(\.\/[^"'\s?#]+\.(?:mjs|css|woff2))(?:\?[^"'\s]*)?\1/g,
        (_match, quote, relative) => {
          if (!inputs.has(relative.slice(2))) throw new Error("Missing local asset: " + relative);
          return quote + relative + "?build=" + revision + quote;
        });
      bytes = Buffer.from(text);
    }
    files.set(name, bytes);
  }
  const manifest = {schemaVersion:1, revision,
    files:[...files].map(([path, bytes]) => ({path, sha256:sha(bytes)}))};
  files.set("asset-revision.json", Buffer.from(JSON.stringify(manifest, null, 2) + "\n"));
  return {revision, files};
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const root = resolve(process.argv[2]);
  const inputs = new Map(readdirSync(root).filter(name => statSync(join(root, name)).isFile())
    .map(name => [name, readFileSync(join(root, name))]));
  const result = packageAssets(inputs);
  for (const [name, bytes] of result.files) writeFileSync(join(root, name), bytes);
  console.log("Versioned browser asset graph: " + result.revision);
}
