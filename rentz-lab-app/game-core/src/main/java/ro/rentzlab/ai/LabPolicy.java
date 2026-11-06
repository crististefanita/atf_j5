package ro.rentzlab.ai;

import ro.rentzlab.core.*;

import java.util.Comparator;

/** Transparent baseline; takes only the actor observation, never the match or opposing hands. */
public final class LabPolicy {
    private LabPolicy() { }
    public static Contract choose(RentzMatch.Observation view) {
        if(view.phase()!=RentzMatch.Phase.CHOOSE||view.turn()!=view.seat()||!view.revealed())throw new IllegalArgumentException("Choice observation");
        double power=view.hand().stream().mapToInt(Card::rank).average().orElse(0);
        Contract preferred=power>=11?Contract.WHIST:Contract.TRICKS;
        return view.available().contains(preferred)?preferred:view.available().iterator().next();
    }
    public static Card card(RentzMatch.Observation view) {
        if(view.phase()!=RentzMatch.Phase.PLAY||view.turn()!=view.seat()||view.legal().isEmpty())throw new IllegalArgumentException("Card observation");
        if(view.contract()==Contract.RENTZ)return view.legal().stream().max(Comparator.comparingInt(Card::rank)).orElseThrow(java.util.NoSuchElementException::new);
        boolean positive=view.contract()==Contract.WHIST||view.contract()==Contract.TEN_CLUBS;
        Comparator<Card> order=Comparator.comparingInt(Card::rank).thenComparing(Card::suit);
        return positive?view.legal().stream().max(order).orElseThrow(java.util.NoSuchElementException::new):view.legal().stream().min(order).orElseThrow(java.util.NoSuchElementException::new);
    }
}
