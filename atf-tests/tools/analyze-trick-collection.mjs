import { spawn, spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const [ffmpeg, root] = process.argv.slice(2);
if (!ffmpeg || !root) throw new Error('Usage: node analyze-trick-collection.mjs <ffmpeg.exe> <capture directory>');
const receipt = JSON.parse(readFileSync(join(root, 'capture.json'), 'utf8'));
if (!receipt.leaseReleased || !receipt.checkpointUnchanged || receipt.captures.length !== 2)
    throw new Error('Two settled recordings with identical game checkpoints are required.');
const width = 360;
const height = 640;
const firstRow = 86;
const lastRow = 520;
const frameBytes = width * height * 3;
const [left, top, right, bottom] = receipt.winnerBounds;
const target = {
    x: (left + right) * width / (2 * receipt.display[0]),
    y: (top + bottom) * height / (2 * receipt.display[1])
};
const sha = path => createHash('sha256').update(readFileSync(path)).digest('hex');
const distance = (a, b) => Math.hypot(a.x - b.x, a.y - b.y);

function inspectFrame(rgb) {
    const pixels = new Uint8Array(width * height);
    const pending = new Int32Array(pixels.length);
    const shapes = [];
    const samples = [];
    const winnerPatch = [];
    for (let y = firstRow; y < lastRow; y++) {
        for (let x = 0; x < width; x++) {
            const index = y * width + x;
            const p = index * 3;
            if (rgb[p] >= 204 && rgb[p + 1] >= 204 && rgb[p + 2] >= 192
                    && Math.max(rgb[p], rgb[p + 1], rgb[p + 2]) - Math.min(rgb[p], rgb[p + 1], rgb[p + 2]) < 28) pixels[index] = 1;
            if (x % 5 === 2 && y % 5 === 2) samples.push(rgb[p], rgb[p + 1], rgb[p + 2]);
            if (Math.abs(x - target.x) <= 20 && Math.abs(y - target.y) <= 20)
                winnerPatch.push((rgb[p] + rgb[p + 1] + rgb[p + 2]) / 3);
        }
    }
    for (let index = firstRow * width; index < lastRow * width; index++) {
        if (pixels[index] !== 1) continue;
        pixels[index] = 0;
        pending[0] = index;
        let count = 1;
        let minX = width, minY = height, maxX = 0, maxY = 0;
        for (let k = 0; k < count; k++) {
            const current = pending[k];
            const x = current % width;
            const y = Math.floor(current / width);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            for (const neighbor of [x > 0 ? current - 1 : -1, x < width - 1 ? current + 1 : -1,
                current - width, current + width]) {
                if (pixels[neighbor] !== 1) continue;
                pixels[neighbor] = 0;
                pending[count++] = neighbor;
            }
        }
        const w = maxX - minX + 1;
        const h = maxY - minY + 1;
        if (w >= 5 && w <= 65 && h >= 7 && h <= 90 && w / h >= 0.53 && w / h <= 0.89 && count >= w * h * 0.66)
            shapes.push({ x: (minX + maxX) / 2, y: (minY + maxY) / 2, w, h });
    }
    const felt = [120, 190, 370, 480].filter(y => {
        const pixel = (y * width + 10) * 3;
        return rgb[pixel + 1] - rgb[pixel] > 25 && rgb[pixel + 1] - rgb[pixel + 2] > 9;
    }).length >= 3;
    return { shapes, samples: Uint8Array.from(samples), winnerPatch: Uint8Array.from(winnerPatch), felt };
}

async function decode(file) {
    const child = spawn(ffmpeg, ['-hide_banner', '-nostdin', '-threads', '2', '-i', file, '-vf', 'showinfo',
        '-vsync', '0', '-an', '-pix_fmt', 'rgb24', '-f', 'rawvideo', 'pipe:1'], { windowsHide: true });
    const frames = [];
    const timestamps = [];
    let errorTail = '';
    let remainder = Buffer.alloc(0);
    child.stderr.setEncoding('utf8');
    child.stderr.on('data', part => {
        const lines = (errorTail + part).split('\n');
        errorTail = lines.pop();
        for (const line of lines) {
            const match = /\bn:\s*(\d+)\s+pts:\s*\d+\s+pts_time:([-\d.]+).*\bs:(\d+)x(\d+)/.exec(line);
            if (match) timestamps[Number(match[1])] = { time: Number(match[2]), width: Number(match[3]), height: Number(match[4]) };
        }
    });
    const ended = new Promise((resolve, reject) => {
        child.on('error', reject);
        child.on('close', code => code === 0 ? resolve() : reject(new Error('FFmpeg exit ' + code + ' ' + errorTail)));
    });
    for await (const chunk of child.stdout) {
        const data = remainder.length ? Buffer.concat([remainder, chunk]) : chunk;
        let offset = 0;
        for (; offset + frameBytes <= data.length; offset += frameBytes)
            frames.push(inspectFrame(data.subarray(offset, offset + frameBytes)));
        remainder = data.subarray(offset);
    }
    await ended;
    if (remainder.length || frames.length < 12 || timestamps.length !== frames.length
            || timestamps.some((entry, index) => !entry || entry.width !== width || entry.height !== height
                || (index && entry.time <= timestamps[index - 1].time)))
        throw new Error('Missing or invalid decoded frame timestamps.');
    return frames.map((frame, index) => ({ ...frame, index, time: timestamps[index].time }));
}

function fullFaces(frame) {
    return frame.shapes.filter(box => box.w >= 34 && box.h >= 46 && box.y > 165 && box.y < 420)
        .sort((a, b) => a.x - b.x || a.y - b.y);
}
function nearFinal(frame, last) {
    let changed = 0;
    for (let index = 0; index < frame.samples.length; index++)
        if (Math.abs(frame.samples[index] - last.samples[index]) > 18) changed++;
    return changed / frame.samples.length < 0.006;
}
function winnerInk(frame, baseline) {
    return frame.winnerPatch.reduce((count, shade, index) => count + Number(shade - baseline.winnerPatch[index] > 24), 0);
}
function projection(shape, origin) {
    const dx = target.x - origin.x;
    const dy = target.y - origin.y;
    const length = Math.hypot(dx, dy);
    return {
        progress: ((shape.x - origin.x) * dx + (shape.y - origin.y) * dy) / (length * length),
        error: Math.abs((shape.x - origin.x) * dy - (shape.y - origin.y) * dx) / length
    };
}
function check(name, expected, actual, passed) {
    return { name, expected: String(expected), actual: String(actual), passed };
}
function writeFrame(video, name, index) {
    const file = join(root, name + '.png');
    const output = spawnSync(ffmpeg, ['-hide_banner', '-nostdin', '-loglevel', 'error', '-y', '-i', video,
        '-vf', `select=eq(n\\,${index})`, '-frames:v', '1', file], { windowsHide: true, encoding: 'utf8' });
    if (output.status !== 0) throw new Error(output.stderr || 'Frame export failed');
    return file;
}

const runs = [];
for (const capture of receipt.captures) {
    if (sha(capture.video).toLowerCase() !== capture.sha256.toLowerCase()) throw new Error('Video bytes changed before decoding');
    const frames = await decode(capture.video);
    const originsIndex = frames.findIndex((frame, index) => {
        const origins = fullFaces(frame);
        if (origins.length !== 4 || !frames[index + 1]) return false;
        const subsequent = fullFaces(frames[index + 1]);
        return subsequent.length === 4 && origins.every(origin => subsequent.some(box => distance(box, origin) <= 2));
    });
    const origins = originsIndex < 0 ? [] : fullFaces(frames[originsIndex]);
    const tracks = origins.map(origin => frames.slice(originsIndex).flatMap(frame => {
        const candidates = frame.shapes.map(shape => ({ ...shape, ...projection(shape, origin) }))
            .filter(point => point.progress >= -0.02 && point.progress <= 1.07 && point.error <= 11
                && point.w <= origin.w + 2 && point.h <= origin.h + 2
                && Math.abs(point.w / origin.w - (1 - 0.82 * point.progress)) <= 0.23);
        candidates.sort((a, b) => a.error - b.error);
        return candidates.length ? [{ frame: frame.index, ...candidates[0] }] : [];
    }));
    const firstMove = frames.find(frame => frame.index > originsIndex && tracks.filter(track =>
        track.some(point => point.frame === frame.index && point.progress > 0.035)).length >= 2);
    const settled = firstMove && frames.find((frame, index) => frame.index > firstMove.index
        && frames[index + 1] && nearFinal(frame, frames.at(-1)) && nearFinal(frames[index + 1], frames.at(-1)));
    const start = frames[originsIndex];
    const emptyTable = settled && frames.slice(firstMove.index, settled.index)
        .findLast(frame => frame.felt && frame.shapes.length === 0);
    const winnerFrames = settled ? frames.slice(firstMove.index, settled.index)
        .filter(frame => frame.felt && winnerInk(frame, emptyTable || start) >= 8) : [];
    const lastVisible = settled && frames.slice(firstMove.index, settled.index).findLast(frame =>
        frame.felt && (winnerFrames.includes(frame)
            || tracks.some(track => track.some(point => point.frame === frame.index && point.progress > 0.035))));
    const end = lastVisible && frames[lastVisible.index + 1];
    const holdMs = firstMove && start ? Math.round((firstMove.time - start.time) * 1000) : -1;
    const lastVisibleMs = lastVisible && start ? Math.round((lastVisible.time - start.time) * 1000) : -1;
    const firstHiddenMs = end && start ? Math.round((end.time - start.time) * 1000) : -1;
    const durationMs = firstHiddenMs >= 0 ? Math.round((lastVisibleMs + firstHiddenMs) / 2) : -1;
    const movementMs = durationMs >= 0 && holdMs >= 0 ? durationMs - holdMs : -1;
    const progressiveTracks = tracks.filter((track, origin) => {
        const length = distance(target, origins[origin]);
        return track.some(point => point.frame >= (firstMove?.index ?? Infinity)
            && point.frame < (end?.index ?? Infinity) && point.progress * length >= 6);
    }).length;
    const checks = [
        check('Four stationary public faces at the start', 4, origins.length, origins.length === 4),
        check('Static exposure around 1000 ms', '850–1150 ms', holdMs + ' ms', holdMs >= 850 && holdMs <= 1150),
        check('Collection around 500 ms', '350–650 ms', movementMs + ' ms', movementMs >= 350 && movementMs <= 650),
        check('Total visible interval around 1500 ms', '1350–1650 ms estimated from adjacent frames',
            durationMs + ' ms (' + lastVisibleMs + '–' + firstHiddenMs + ' ms)',
            durationMs >= 1350 && durationMs <= 1650),
        check('Four separately detected faces move toward the independently identified winner', '4 faces',
            progressiveTracks + ' faces', progressiveTracks === 4),
        check('The overlapping faces reach the winning frame before fading', 'visible at the winning seat',
            winnerFrames.length ? `${winnerFrames.length} frame(s) at the winning seat` : 'no frame near winner',
            winnerFrames.length > 0),
        check('Final game checkpoint is byte-for-byte unchanged after restart', true,
            receipt.checkpointUnchanged, receipt.checkpointUnchanged)
    ];
    const trackFile = join(root, capture.name + '-frames.json');
    writeFileSync(trackFile, JSON.stringify({ target, origins, tracks, frames: frames.map(({ index, time, shapes }) =>
        ({ index, time, shapes })), start: start?.index, motion: firstMove?.index, lastVisible: lastVisible?.index,
        end: end?.index, settled: settled?.index, winnerFrames: winnerFrames.map(frame => frame.index) }, null, 2));
    const evidence = start && firstMove && end ? [
        writeFrame(capture.video, capture.name + '-hold', start.index),
        writeFrame(capture.video, capture.name + '-motion', winnerFrames[0]?.index ?? Math.min(firstMove.index + 3, end.index - 1))
    ] : [];
    const frameGapAtEndMs = lastVisible && end ? Math.round((end.time - lastVisible.time) * 1000) : -1;
    runs.push({ ...capture, checks, frameCount: frames.length,
        timing: { holdMs, movementMs, durationMs, lastVisibleMs, firstHiddenMs, frameGapAtEndMs },
        trace: trackFile, traceSha256: sha(trackFile), frameImages: evidence });
}
const passed = runs.every(run => run.checks.every(item => item.passed));
const report = { mode: receipt.mode, api: receipt.api, apkSha256: receipt.apkSha256,
    winner: receipt.winner, winnerName: receipt.winnerName, target, passed, runs,
    features: receipt.features ?? [], nativeFailure: receipt.failure };
writeFileSync(join(root, 'measurements.json'), JSON.stringify(report, null, 2));
for (const run of runs) {
    console.log(receipt.mode, run.name, run.timing, run.checks.filter(item => !item.passed));
}
if (!passed) process.exitCode = 1;
