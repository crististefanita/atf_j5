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

export function rentzScoreTable(data, element, historyGrid) {
  const content = element("div", "", "rentz-score-table");
  const scroll = element("div", "", "rentz-score-scroll");
  scroll.append(rentzHistoryGrid(data.grid, element, historyGrid));
  content.append(scroll, element("p", data.grid.legend, "history-legend"));
  return content;
}
