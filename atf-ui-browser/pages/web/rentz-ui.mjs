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
export function rentzChoiceDropdown(actions, element, play, language) {
  const choices = actions.filter(move => move.key.startsWith("choose:") || move.key.startsWith("blind:"));
  if (!choices.length) return null;

  const prompt = language === "en" ? "Choose a sub-game" : "Alege sub-jocul";
  const label = element("label", "", "rentz-contract-picker");
  label.append(element("span", language === "en" ? "Game" : "Sub-joc"));

  const select = element("select", "", "rentz-contract-select");
  select.setAttribute("aria-label", prompt);
  const placeholder = element("option", prompt);
  placeholder.value = "";
  placeholder.disabled = true;
  placeholder.selected = true;
  select.append(placeholder);

  for (const move of choices) {
    const option = element("option", move.label);
    option.value = move.key;
    select.append(option);
  }
  select.addEventListener("change", () => {
    if (choices.some(move => move.key === select.value)) play(select.value);
  });

  label.append(select);
  return label;
}
