'use strict';

const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

const [run, rulesFile] = process.argv.slice(2);
if (!run || !rulesFile) throw new Error('Usage: node analyze-achievement-calibration.cjs RUN RULES');
const read = file => JSON.parse(fs.readFileSync(file, 'utf8'));
const sha = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const summaryFile = path.join(run, 'summary.json');
const receipt = read(path.join(run, 'result.json'));
const summary = read(summaryFile);
const component = read(receipt.androidComponentReceipt);
const catalog = read(rulesFile);
const componentTest = component.tests.find(test => test.name.endsWith('.GameplayAchievementComponentTest'));
if (receipt.status !== 'PASS' || component.status !== 'PASS' || !componentTest || componentTest.tests < 5 ||
    componentTest.failures || componentTest.errors || componentTest.skipped ||
    sha(summaryFile) !== receipt.summarySha256 || sha(rulesFile) !== receipt.catalogSha256) {
    throw new Error('Calibration, component receipt or catalog identity failed verification');
}

const modes = ['PRODUCT', 'MIXED'];
const tiers = { EASY: 'Ușor', MEDIUM: 'Mediu', HARD: 'Greu' };
const rules = Object.entries(catalog.tiers).flatMap(([level, rows]) => rows.map(rule => ({ ...rule, level })));
const seeds = new Set();
const stageCounts = { calibration: 0, holdout: 0 };
for (const cell of summary.cells) {
    if (!Object.hasOwn(stageCounts, cell.stage) || !modes.includes(cell.mode) || cell.games !== cell.matches.length)
        throw new Error('Malformed calibration cell');
    for (const match of cell.matches) {
        if (!Number.isSafeInteger(match.seed) || seeds.has(match.seed) ||
            match.hands !== 8 * cell.players || match.totals.length !== cell.players ||
            match.rank !== 1 + match.totals.filter(score => score > match.totals[0]).length) {
            throw new Error('Incomplete, duplicate or incorrectly ranked match');
        }
        seeds.add(match.seed);
    }
    stageCounts[cell.stage] += cell.games;
}
if (stageCounts.calibration < 200 || stageCounts.holdout < 300 || seeds.size !== summary.games ||
    summary.games !== receipt.games || summary.achievements.length !== rules.length || rules.length === 0) {
    throw new Error('The complete catalog and the independent cohorts must be measured');
}

function aggregate(rates, mode, window) {
    const cells = rates.filter(row => row.mode === mode && row.window === window);
    const total = cells.reduce((sum, row) => sum + row.opportunities, 0);
    const hits = cells.reduce((sum, row) => sum + row.successes, 0);
    const medians = cells.map(row => row.medianUnlockHand).filter(Boolean).sort((a, b) => a - b);
    if (!total || hits > total) throw new Error('Invalid attainment denominator');
    return { hits, total, fraction: hits / total, median: medians[Math.floor(medians.length / 2)] || 0 };
}

function inspectOrder(cells, rules, achievements) {
    if (cells.every(cell => !cell.campaigns && cell.matches.every(match => !match.unlocks))) return null;
    const context = row => [row.stage, row.mode, row.difficulty, row.players].join('/');
    const eligibleContexts = new Map(achievements.map(row => [
        row.id, new Set(row.rates.filter(rate => rate.window === 1).map(context))
    ]));
    const applies = (rule, profile) => eligibleContexts.get(rule.id).has(context(profile));
    const profiles = [];
    for (const cell of cells) {
        if (!cell.campaigns || cell.matches.some(match => !match.unlocks))
            throw new Error('Missing unlock timelines in a campaign');
        const handsPerMatch = 8 * cell.players;
        const windows = [
            ...cell.matches.map(match => ({ firstSeed: match.seed, length: 1, unlocks: match.unlocks })),
            ...cell.campaigns
        ];
        const seen = new Set();
        for (const window of windows) {
            const start = cell.matches.findIndex(match => match.seed === window.firstSeed);
            const key = window.length + '/' + start;
            if (![1, 3, 8].includes(window.length) || start < 0 || start % window.length ||
                start + window.length > cell.matches.length || seen.has(key))
                throw new Error('Invalid or overlapping campaign ' + key);
            seen.add(key);
            for (const [id, event] of Object.entries(window.unlocks)) {
                if (!rules.some(rule => rule.id === id) ||
                    !Number.isInteger(event.hand) || event.hand < 1 || event.hand > handsPerMatch ||
                    !Number.isInteger(event.match) || event.match < 1 || event.match > window.length ||
                    event.totalHand !== (event.match - 1) * handsPerMatch + event.hand)
                    throw new Error('Invalid first unlock for ' + id);
            }
            if (window.length === 1) {
                const match = cell.matches[start];
                for (const rule of rules) {
                    if ((match.counts[rule.id] >= rule.target) !== Object.hasOwn(window.unlocks, rule.id))
                        throw new Error('Unlock timeline differs from the final count for ' + rule.id);
                }
            }
            profiles.push({ ...window, stage: cell.stage, mode: cell.mode, difficulty: cell.difficulty, players: cell.players });
        }
        const expected = [1, 3, 8].reduce((count, length) => count + Math.floor(cell.matches.length / length), 0);
        if (seen.size !== expected) throw new Error('Incomplete campaign timelines');
        for (const row of achievements) {
            const rates = row.rates.filter(rate => context(rate) === context(cell));
            for (const rate of rates) {
                const comparable = windows.filter(window => window.length === rate.window);
                const hits = comparable.filter(window => Object.hasOwn(window.unlocks, row.id));
                if (rate.opportunities !== comparable.length || rate.successes !== hits.length)
                    throw new Error('Campaign timeline and rate disagree for ' + row.id);
            }
        }
    }
    const holdout = profiles.filter(profile => profile.stage === 'holdout');
    const long = holdout.filter(profile => profile.length === 8);
    const short = holdout.filter(profile => profile.length === 1);
    const quantile = (values, p) => values.length ? values[Math.floor((values.length - 1) * p)] : null;
    const objectives = rules.map(rule => {
        const events = long.flatMap(profile => profile.unlocks[rule.id] ? [profile.unlocks[rule.id]] : []);
        const hands = events.map(event => event.totalHand).sort((a, b) => a - b);
        const matches = events.map(event => event.match).sort((a, b) => a - b);
        const eligible = long.filter(profile => applies(rule, profile));
        const single = short.filter(profile => applies(rule, profile));
        if (long.some(profile => profile.unlocks[rule.id] && !applies(rule, profile)))
            throw new Error('Unlock in an ineligible context for ' + rule.id);
        return {
            id: rule.id, level: rule.level, title: rule.titleRo, successes: events.length, eligible: eligible.length,
            p10: quantile(hands, .1), median: quantile(hands, .5), p90: quantile(hands, .9),
            medianMatch: quantile(matches, .5), firstMatch: single.filter(profile => profile.unlocks[rule.id]).length,
            eligibleFirstMatch: single.length
        };
    });
    const pairs = [];
    for (const [i, earlier] of rules.entries()) {
        for (const later of rules.slice(i + 1)) {
            if (earlier.level !== later.level) continue;
            const counts = { eligible: 0, earlierFirst: 0, simultaneous: 0, laterFirst: 0, earlierOnly: 0, laterOnly: 0 };
            for (const profile of long) {
                if (!applies(earlier, profile) || !applies(later, profile)) continue;
                counts.eligible++;
                const a = profile.unlocks[earlier.id]?.totalHand;
                const b = profile.unlocks[later.id]?.totalHand;
                if (a && b) counts[a < b ? 'earlierFirst' : a === b ? 'simultaneous' : 'laterFirst']++;
                else if (a || b) counts[a ? 'earlierOnly' : 'laterOnly']++;
            }
            pairs.push({ earlier: earlier.id, later: later.id, ...counts });
        }
    }
    return { oneMatchProfiles: short.length, eightMatchProfiles: long.length, objectives, pairs };
}

const assessments = rules.map((rule, index) => {
    const row = summary.achievements[index];
    if (row.id !== rule.id || row.goal !== rule.target || row.level !== rule.level)
        throw new Error('Compiled catalog and YAML order or thresholds differ');
    const heldout = row.rates.filter(rate => rate.stage === 'holdout');
    const slices = Object.fromEntries(modes.map(mode => [mode, {
        one: aggregate(heldout, mode, 1), three: aggregate(heldout, mode, 3), eight: aggregate(heldout, mode, 8)
    }]));
    const values = Object.values(slices);
    const reasons = [];
    let placement = 25;
    if (rule.level === 'EASY') {
        if (values.some(value => value.one.fraction < 0.8)) reasons.push('sub 80% într-o partidă într-o cohortă');
        if (values.some(value => value.one.median > 12)) placement -= 2;
    } else if (rule.level === 'MEDIUM') {
        if (values.some(value => value.three.fraction < 0.25)) reasons.push('sub 25% în trei partide într-o cohortă');
        if (values.some(value => value.one.median > 0 && value.one.median < 6)) placement -= 2;
    } else {
        if (values.some(value => value.one.fraction > 0.7)) reasons.push('peste 70% într-o partidă într-o cohortă');
        if (values.some(value => value.three.fraction > 0.9)) placement -= 2;
    }
    const eligible = heldout.filter(rate => rate.window === 1);
    const uncovered = eligible.filter(rate => rate.eligibleMatches === 0);
    const floor = Math.min(...values.map(value => value.eight.fraction));
    const accessibility = floor >= 0.5 && uncovered.length === 0 ? 20 : floor >= 0.2 ? 18 : 15;
    if (values.some(value => value.eight.hits === 0)) reasons.push('zero reușite în opt partide într-o cohortă eligibilă');
    if (uncovered.length) reasons.push('celulă eligibilă fără dovadă pe semințele rezervate');
    const rawScore = 40 + placement + accessibility + 15;
    return { id: rule.id, title: rule.titleRo, level: rule.level, target: rule.target, slices,
        scores: [40, placement, accessibility, 15], score: reasons.length ? Math.min(90, rawScore) : rawScore, reasons };
});
const ready = assessments.every(row => row.score > 90 && row.reasons.length === 0);
const order = inspectOrder(summary.cells, rules, summary.achievements);
if (order) {
    const timeline = [
        '# Ordinea de deblocare a realizărilor Rentz', '',
        `Lotul rezervat conține ${order.oneMatchProfiles} de partide și ${order.eightMatchProfiles} de parcursuri a câte opt partide. Partidele din fiecare parcurs sunt distincte. Parcursurile sunt extrase din același lot.`, '',
        'Poziția este mâna la care pragul a fost confirmat. Percentilele folosesc numai parcursurile cu deblocare. Numitorul include configurațiile de masă și dificultate eligibile.', '',
        '| Nivel | Realizare | În prima partidă | În opt partide | Mâna P10 / mediană / P90 | Partida mediană |',
        '|---|---|---:|---:|---:|---:|'
    ];
    for (const row of order.objectives)
        timeline.push(`| ${tiers[row.level]} | ${row.title} | ${row.firstMatch}/${row.eligibleFirstMatch} | ${row.successes}/${row.eligible} | ${row.p10} / ${row.median} / ${row.p90} | ${row.medianMatch} |`);
    timeline.push('', '| Pereche din același nivel, în ordinea recomandată | Eligibile pentru ambele | Prima înainte | Simultan | A doua înainte | Doar prima | Doar a doua |',
        '|---|---:|---:|---:|---:|---:|---:|');
    const titles = new Map(rules.map(rule => [rule.id, rule.titleRo]));
    for (const row of order.pairs)
        timeline.push(`| ${titles.get(row.earlier)} → ${titles.get(row.later)} | ${row.eligible} | ${row.earlierFirst} | ${row.simultaneous} | ${row.laterFirst} | ${row.earlierOnly} | ${row.laterOnly} |`);
    fs.writeFileSync(path.join(run, 'audit-ordine.md'), timeline.join('\n') + '\n', 'utf8');
    fs.writeFileSync(path.join(run, 'achievement-order.json'), JSON.stringify(order, null, 2) + '\n', 'utf8');
}
const fmt = stat => `${Math.round(stat.fraction * 100)}% (${stat.hits}/${stat.total})`;
const lines = [
    '# Auditul realizărilor de joc Rentz', '',
    `Au fost încheiate **${summary.games} de partide** și **${summary.hands} de mâini**. Lotul de calibrare cuprinde ${stageCounts.calibration} de partide, iar lotul rezervat verificării cuprinde ${stageCounts.holdout} de partide cu semințe distincte.`, '',
    'Ratele provin din lotul rezervat, pe grupe fără suprapunere de una, trei sau opt partide la aceeași mărime de masă, dificultate și politică. Fiecare rând folosește numai configurațiile eligibile pentru acel obiectiv.', '',
    'Nota tehnică alocă până la 40 de puncte pentru corectitudine și dovezi, 25 pentru plasarea în nivel, 20 pentru accesibilitate și 15 pentru claritate și diferențiere. Un criteriu de admitere ratat plafonează nota la 90.', '',
    '| Nivel | Realizare | Produs 1 | Mixt 1 | Produs 3 | Mixt 3 | Produs 8 | Mixt 8 | Puncte | Notă |',
    '|---|---|---:|---:|---:|---:|---:|---:|---|---:|'
];
for (const row of assessments) {
    const { PRODUCT: product, MIXED: mixed } = row.slices;
    lines.push(`| ${tiers[row.level]} | ${row.title} | ${fmt(product.one)} | ${fmt(mixed.one)} | ${fmt(product.three)} | ${fmt(mixed.three)} | ${fmt(product.eight)} | ${fmt(mixed.eight)} | ${row.scores.join('/')} | ${row.score} |`);
}
lines.push('', '## Calificare', '');
lines.push(ready ? `Toate cele ${rules.length} obiective au trecut criteriile și au note peste 90.` : 'Catalogul cere ajustări înainte de calificare.');
for (const row of assessments.filter(row => row.reasons.length))
    lines.push(`- **${row.title}** — ${row.reasons.join(', ')}.`);
lines.push('', 'Normal folosește politica aplicației. În cohortele Greu, căutarea folosește bugetul declarat în plan. Mixul introduce și o politică aleatorie legală. Ratele nu estimează direct dificultatea resimțită de jucători.', '');
fs.writeFileSync(path.join(run, 'audit-realizari.md'), lines.join('\n'), 'utf8');
fs.writeFileSync(path.join(run, 'achievement-audit.json'),
    JSON.stringify({ ready, stageCounts, games: summary.games, hands: summary.hands, assessments }, null, 2) + '\n', 'utf8');
console.log(`Gameplay achievement audit ${ready ? 'PASS' : 'NEEDS ADJUSTMENT'}`);
if (!ready) process.exitCode = 1;
