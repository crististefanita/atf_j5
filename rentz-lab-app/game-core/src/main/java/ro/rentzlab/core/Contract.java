package ro.rentzlab.core;

public enum Contract {
    TRICKS("Levate"), QUEENS("Dame"), DIAMONDS("Carouri"), KING_HEARTS("Regele de cupă"),
    TOTALS("Totale"), WHIST("Whist"), TEN_CLUBS("Zece de treflă"), RENTZ("Rentz");
    private final String label;
    Contract(String label) { this.label = label; }
    public String label() { return label; }
}
