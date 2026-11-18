/** Rentz projection over the shared history renderer; no scoring is recalculated here. */
export function rentzHistoryGrid(data, element, historyGrid) {
  if (!data || data.players.length < 3 || data.players.length > 6
      || data.rows.some(row => row.cells.length !== data.players.length)) {
    throw new TypeError("Rentz history needs one P/T pair per seat.");
  }
  const grid = historyGrid(data, element);
  const body = grid.children[1];
  data.rows.forEach((record, index) => {
    const label = body.children[index].children[0];
    label.textContent = `${record.cards} · ${record.status}`;
  });
  return grid;
}

/** A settlement shows one row per seat; the complete history keeps its P/T pairs. */
export function rentzScoreTable(data, element, scoreGrid, language) {
  const grid = data?.grid;
  if (!grid || !Array.isArray(grid.players) || grid.players.length < 3 || grid.players.length > 6
      || grid.rows?.length !== 1 || grid.rows[0].cells?.length !== grid.players.length) {
    throw new TypeError("Rentz settlement needs one confirmed hand and one P/T pair per seat.");
  }
  const content = element("div", "", "rentz-score-table");
  const rows = grid.players.map((name, seat) => [name, grid.rows[0].cells[seat].bid, grid.rows[0].cells[seat].total]);
  content.append(scoreGrid({headings: [language === "en" ? "Player" : "Jucător", "P", "T"], rows}, true),
    element("p", grid.legend, "history-legend"));
  return content;
}

/** Keep the exact contributions available without duplicating the primary score table. */
export function rentzScoreRules(details) {
  return [details.awards, ...details.rules].filter(Boolean);
}
