package ro.rentzlab.core;
import java.util.*;
final class AiFixtures {
    static Card c(String token){var suit=switch(token.charAt(0)){case 'C'->Card.Suit.CLUBS;case 'D'->Card.Suit.DIAMONDS;case 'H'->Card.Suit.HEARTS;case 'S'->Card.Suit.SPADES;default->throw new IllegalArgumentException();};int rank=switch(token.substring(1)){case "J"->11;case "Q"->12;case "K"->13;case "A"->14;default->Integer.parseInt(token.substring(1));};return new Card(suit,rank);}
    static List<Card> cards(String value){return Arrays.stream(value.split(" ")).map(AiFixtures::c).toList();}
    static OpeningDeal deal(Map<Integer,List<Card>> fixed){var remaining=new ArrayList<>(Card.deck(4));for(var hand:fixed.values())for(Card card:hand)if(!remaining.remove(card))throw new IllegalArgumentException("Duplicate fixture card");var hands=new ArrayList<List<Card>>();for(int seat=0;seat<4;seat++){var hand=new ArrayList<>(fixed.getOrDefault(seat,List.of()));while(hand.size()<8)hand.add(remaining.remove(0));hands.add(hand);}return new OpeningDeal(hands);}
    static OpeningDeal blockedAce(){return new OpeningDeal(List.of(cards("CJ CQ CK CA D7 H7 S7 S8"),cards("DJ DQ DK DA D8 D9 D10 C8"),cards("HJ HQ HK HA H8 H9 H10 C9"),cards("SJ SQ SK SA S9 S10 C10 C7")));}
    static RentzMatch game(OpeningDeal opening,Contract contract){var game=RentzMatch.fromOpeningDeal(opening,9,ScoreProfile.laboratory(),0);game.choose(0,contract,false);return game;}
    static RentzMatch replay(RentzMatch.Observation view,OpeningDeal opening){var game=RentzMatch.fromOpeningDeal(opening,0,ScoreProfile.laboratory(),view.chooser());game.choose(view.chooser(),view.contract(),view.blind());for(var event:view.events()){if(event.action()==RentzMatch.Action.PLAY)game.play(event.seat(),event.card());else if(event.action()==RentzMatch.Action.PASS)game.pass(event.seat());}return game;}
}
