export function rentzSequence(rows, element, play, language) {
  const panel = element("div", "", "rentz-sequence");
  panel.setAttribute("role", "group");
  panel.setAttribute("aria-label", language === "en" ? "Rentz sequence board" : "Șirurile Rentz");
  for (const [suitIndex, rowData] of rows.entries()) {
    const { suit, cells } = rowData;
    const row = element("div", "", "rentz-sequence-row");
    row.dataset.suitRow = String(suitIndex);
    row.style.setProperty("--rentz-ranks", String(cells.length));
    const badge = element("span", suit, "rentz-suit-symbol" + (suit === "♥" || suit === "♦" ? " red" : " black"));
    badge.setAttribute("aria-hidden", "true");
    row.append(badge);
    for (const cell of cells) {
      const color = (cell.played || cell.legal || cell.opening)
        ? (/[♥♦]$/.test(cell.label) ? " red" : " black") : "";
      const style = "rentz-sequence-cell" + color + (cell.played ? " played" : "")
        + (cell.legal ? " legal" : "") + (cell.opening ? " opening" : "");
      if (cell.legal && cell.action) {
        const button = element("button", cell.label, style);
        button.type = "button";
        button.dataset.action = cell.action;
        button.setAttribute("aria-label", (language === "en" ? "Play " : "Joacă ") + cell.label);
        button.addEventListener("click", () => play(cell.action));
        row.append(button);
      } else {
        const tile = element("span", cell.played ? cell.label : (cell.opening ? cell.label : "·"), style);
        tile.setAttribute("aria-label", cell.played ? cell.label : (cell.opening ? cell.label : ""));
        row.append(tile);
      }
    }
    panel.append(row);
  }
  return panel;
}
