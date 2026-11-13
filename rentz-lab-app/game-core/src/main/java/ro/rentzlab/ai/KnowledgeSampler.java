package ro.rentzlab.ai;

import ro.rentzlab.core.*;

import java.util.*;

/** Feasible worlds, not a calibrated probability distribution. Input excludes real opposing hands. */
public final class KnowledgeSampler {
    private final RentzMatch.Observation view;
    private final List<List<Card>> played=new ArrayList<>();
    private final List<Set<Card>> forbidden=new ArrayList<>();
    private final List<Card> unknown;

    public KnowledgeSampler(RentzMatch.Observation view) {
        this.view=view;
        if(!view.revealed()||view.hand().size()!=view.handSizes().get(view.seat()))throw new IllegalArgumentException("Visible own hand required");
        var deck=Card.deck(view.players());
        for(int seat=0;seat<view.players();seat++){played.add(new ArrayList<>());forbidden.add(new HashSet<>());}
        var publicTrick=new ArrayList<Card>();var board=new RentzBoard(view.players(), view.sequenceOpening());var seen=new ArrayList<Card>();
        int sequence=0;
        for(var event:view.events()) {
            if(event.sequence()!=++sequence||event.seat()<0||event.seat()>=view.players())throw new IllegalArgumentException("Public event sequence");
            if(event.action()==RentzMatch.Action.PLAY) {
                Card card=Objects.requireNonNull(event.card());
                if(!deck.contains(card)||seen.contains(card))throw new IllegalArgumentException("Public card duplicate or outside deck");
                if(view.contract()==Contract.RENTZ)board.play(card);
                else {
                    if(!publicTrick.isEmpty()&&card.suit()!=publicTrick.get(0).suit())
                        for(Card possible:deck)if(possible.suit()==publicTrick.get(0).suit())forbidden.get(event.seat()).add(possible);
                    publicTrick.add(card);if(publicTrick.size()==view.players())publicTrick.clear();
                }
                played.get(event.seat()).add(card);seen.add(card);
            } else if(event.action()==RentzMatch.Action.PASS) {
                if(view.contract()!=Contract.RENTZ||event.boardCardCount()!=board.played().size())throw new IllegalArgumentException("Pass frontier");
                for(Card possible:deck)if(board.fits(possible))forbidden.get(event.seat()).add(possible);
            }
        }
        if(!seen.equals(view.seen())||!board.played().equals(view.board())||!publicTrick.equals(view.trick().stream().map(TrickRules.Play::card).collect(java.util.stream.Collectors.toList())))throw new IllegalArgumentException("Public memory mismatch");
        var own=new HashSet<>(view.hand());
        if(own.size()!=view.hand().size()||!deck.containsAll(own)||own.stream().anyMatch(seen::contains))throw new IllegalArgumentException("Own card conservation");
        for(int seat=0;seat<view.players();seat++)if(played.get(seat).size()+view.handSizes().get(seat)!=8)throw new IllegalArgumentException("Seat card conservation");
        if(view.hand().stream().anyMatch(forbidden.get(view.seat())::contains))throw new IllegalArgumentException("Own hand contradicts public constraints");
        unknown=new ArrayList<>(deck);unknown.removeAll(own);unknown.removeAll(seen);
    }
    public boolean allows(int seat,Card card){return !forbidden.get(seat).contains(card);}
    public List<Card> unknownCards(){return Card.immutable(unknown);}

    /** Returns all distinct small feasible assignments, or an empty list when the search would exceed the cap. */
    public List<OpeningDeal> exactSmallWorlds(int limit) {
        if (limit < 1) throw new IllegalArgumentException("Positive world cap required");
        if (unknown.size() > 6) return Collections.emptyList();
        var hands = new ArrayList<List<Card>>();
        for (var cards : played) hands.add(new ArrayList<>(cards));
        hands.get(view.seat()).addAll(view.hand());
        var worlds = new ArrayList<OpeningDeal>();
        if (!enumerate(0, hands, worlds, limit)) return Collections.emptyList();
        return Card.immutable(worlds);
    }

    private boolean enumerate(int index, List<List<Card>> hands, List<OpeningDeal> worlds, int limit) {
        if (index == unknown.size()) {
            if (worlds.size() == limit) return false;
            var ordered = new ArrayList<List<Card>>();
            for (var hand : hands) {
                var cards = new ArrayList<>(hand);
                cards.sort(TacticalPolicy.order());
                ordered.add(cards);
            }
            worlds.add(new OpeningDeal(ordered));
            return true;
        }
        Card card = unknown.get(index);
        for (int seat = 0; seat < hands.size(); seat++) {
            var hand = hands.get(seat);
            if (seat == view.seat() || hand.size() == 8 || !allows(seat, card)) continue;
            hand.add(card);
            boolean withinLimit = enumerate(index + 1, hands, worlds, limit);
            hand.remove(hand.size() - 1);
            if (!withinLimit) return false;
        }
        return true;
    }

    public OpeningDeal sample(long samplingSeed) {
        var random=new Random(samplingSeed);var cards=new ArrayList<>(unknown);Collections.shuffle(cards,random);
        var slots=new ArrayList<Integer>();
        for(int seat=0;seat<view.players();seat++)if(seat!=view.seat())for(int n=0;n<view.handSizes().get(seat);n++)slots.add(seat);
        Collections.shuffle(slots,random);
        if(slots.size()!=cards.size())throw new IllegalArgumentException("Unknown capacity mismatch");
        int[] assigned=new int[slots.size()];Arrays.fill(assigned,-1);
        for(int card=0;card<cards.size();card++)if(!augment(card,cards,slots,assigned,new boolean[slots.size()]))throw new IllegalArgumentException("No feasible hidden-card assignment");
        var hands=new ArrayList<List<Card>>();for(int seat=0;seat<view.players();seat++)hands.add(new ArrayList<>(played.get(seat)));
        hands.get(view.seat()).addAll(view.hand());
        for(int slot=0;slot<slots.size();slot++)hands.get(slots.get(slot)).add(cards.get(assigned[slot]));
        for(var hand:hands)hand.sort(TacticalPolicy.order());
        return new OpeningDeal(hands);
    }
    private boolean augment(int card,List<Card> cards,List<Integer> slots,int[] assigned,boolean[] visited) {
        for(int slot=0;slot<slots.size();slot++)if(!visited[slot]&&allows(slots.get(slot),cards.get(card))) {
            visited[slot]=true;
            if(assigned[slot]<0||augment(assigned[slot],cards,slots,assigned,visited)){assigned[slot]=card;return true;}
        }
        return false;
    }
    public static long fingerprint(RentzMatch.Observation view) {
        long hash=0xcbf29ce484222325L;
        var data=new ArrayList<Integer>(Arrays.asList(view.players(),view.seat(),view.turn(),view.chooser(),view.handNumber(),view.phase().ordinal(),view.contract()==null?-1:view.contract().ordinal(),view.blind()?1:0));
        var own=new ArrayList<>(view.hand());own.sort(TacticalPolicy.order());for(Card card:own){data.add(card.suit().ordinal());data.add(card.rank());}
        data.addAll(view.handSizes());
        for(var event:view.events()){data.add(event.sequence());data.add(event.seat());data.add(event.action().ordinal());data.add(event.card()==null?-1:event.card().suit().ordinal()*16+event.card().rank());data.add(event.boardCardCount());}
        for(int value:data){hash^=value;hash*=0x100000001b3L;}
        return hash;
    }
}
