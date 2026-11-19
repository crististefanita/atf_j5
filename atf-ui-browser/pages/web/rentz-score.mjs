/** Rentz projection over the shared history renderer; no scoring is recalculated here. */
export function rentzHistoryGrid(data, element, historyGrid) {
  if (!data || data.players.length < 3 || data.players.length > 6
      || data.rows.some(row => row.cells.length !== data.players.length)) {
    throw new TypeError("Rentz history needs one Δ/Σ pair per seat.");
  }
  const grid = historyGrid(data, element);
  grid.classList.add("rentz-history-grid");
  const body = grid.children[1];
  data.rows.forEach((record, index) => {
    const label = body.children[index].children[0];
    label.textContent = `${record.cards} · ${record.status}`;
  });
  return grid;
}

/** A settlement shows one row per seat; the complete history keeps its Δ/Σ pairs. */
export function rentzScoreTable(data, element, scoreGrid) {
  const grid = data?.grid;
  if (!grid || !Array.isArray(grid.players) || grid.players.length < 3 || grid.players.length > 6
      || grid.rows?.length !== 1 || grid.rows[0].cells?.length !== grid.players.length) {
    throw new TypeError("Rentz settlement needs one confirmed hand and one Δ/Σ pair per seat.");
  }
  const content = element("div", "", "rentz-score-table");
  if (data.headings?.length !== 3 || data.rows?.length !== grid.players.length
      || data.headings[1] !== "Δ:" || data.headings[2] !== "Σ:"
      || data.rows.some((row, seat) => row.length !== 3 || row[0] !== grid.players[seat]
        || row[1] !== grid.rows[0].cells[seat].bid || row[2] !== grid.rows[0].cells[seat].total)) {
    throw new TypeError("Rentz settlement needs the canonical native three-column projection.");
  }
  content.append(scoreGrid(data, true),
    element("p", grid.legend, "history-legend"));
  return content;
}

/** Keep the exact contributions available without duplicating the primary score table. */
export function rentzScoreRules(details) {
  return [...(details.awards ?? "").split("\n"), ...details.rules].filter(Boolean);
}
