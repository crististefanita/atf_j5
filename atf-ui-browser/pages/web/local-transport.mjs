const profileKey = "rentzlab:pages:profile:v1";

export function localTransport() {
  const worker = new Worker(new URL("./engine-worker.mjs", import.meta.url), { type: "module" });
  const pending = new Map();
  let sequence = 0;
  let workerFailure;
  const keys = ["name", "progress", "checkpoint", "rankings"];
  let profile = Object.fromEntries(keys.map(key => [key, null]));
  let storageAvailable = true;

  worker.addEventListener("message", ({ data }) => {
    const command = pending.get(data.id);
    if (!command) return;
    pending.delete(data.id);
    if (data.status) command.reject(Object.assign(new Error(data.error), { status: data.status }));
    else command.resolve(data);
  });
  worker.addEventListener("error", () => {
    workerFailure = new Error("Motorul Rentz nu s-a încărcat. Reîncarcă pagina pentru a încerca din nou.");
    for (const command of pending.values()) command.reject(workerFailure);
    pending.clear();
  });

  function load() {
    if (!storageAvailable) return profile;
    try {
      const stored = JSON.parse(localStorage.getItem(profileKey));
      profile = Object.fromEntries(keys.map(key => [key, typeof stored?.[key] === "string" ? stored[key] : null]));
    } catch {
      storageAvailable = false;
    }
    return profile;
  }

  async function request(path, values = {}) {
    if (workerFailure) throw workerFailure;
    const previous = load();
    const fields = Object.fromEntries(Object.entries(values).map(([key, value]) => [key, String(value)]));
    const reply = await new Promise((resolve, reject) => {
      const id = ++sequence;
      pending.set(id, { resolve, reject });
      worker.postMessage({ id, path, values: fields, profile: previous });
    });
    const next = Object.fromEntries(keys.map(key => [key, reply[key] ?? null]));
    if (keys.some(key => previous[key] !== next[key])) {
      profile = next;
      if (storageAvailable) {
        try { localStorage.setItem(profileKey, JSON.stringify(next)); }
        catch { storageAvailable = false; }
      }
    }
    if (reply.result?.view !== undefined) {
      if (reply.rejectedCheckpoint) reply.result.storageIssue = "savedGameError";
      else if (!storageAvailable) reply.result.storageIssue = "storageError";
      if (reply.rentzArrival) reply.result.rentzArrival = reply.rentzArrival;
      if (reply.rentzPlayback) reply.result.rentzPlayback = reply.rentzPlayback;
    }
    return reply.result;
  }

  return {
    resumeOnHome: true,
    request: (path, values) => navigator.locks
      ? navigator.locks.request(profileKey, () => request(path, values)) : request(path, values)
  };
}
