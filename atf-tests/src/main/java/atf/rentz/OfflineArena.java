package atf.rentz;

import ro.rentzlab.ai.*;

import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import ro.rentzlab.core.*;

/** Complete CPU matches, paired distributions, rotated seats, two opponent families and curated receipts. */
public final class OfflineArena {
    private static final ObjectMapper JSON=new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public enum Kind { BASELINE, NORMAL, HARD }
    public record Protocol(String id,String phase,int players,List<Long> seeds,List<Kind> opponents,Kind candidate,int worlds,int retainPerFamily) {
        public Protocol {
            seeds=Card.immutable(seeds);opponents=Card.immutable(opponents);
            if(id==null||!id.matches("[a-zA-Z0-9_-]+")||!Set.of("DEVELOPMENT","FRESH_EVALUATION").contains(phase)||players<3||players>6||seeds.isEmpty()||seeds.size()>64
                    ||new HashSet<>(seeds).size()!=seeds.size()||opponents.isEmpty()||new HashSet<>(opponents).size()!=opponents.size()||candidate==null||worlds<1||worlds>128||retainPerFamily<0||retainPerFamily>6)throw new IllegalArgumentException("Arena protocol");
        }
    }
    public record Move(int number,int hand,int actor,String action,Object value,RentzMatch.Observation actorInput,SearchPolicy.Decision<?> decision) { }
    public record Episode(long seed,int candidateSeat,Kind family,Kind candidate,List<Integer> totals,List<RentzMatch.HandResult> history,List<Move> moves,long elapsedMillis,long maxDecisionNanos,int searchedDecisions,int exactShortcuts) { }
    public record Paired(long seed,int seat,Kind family,Kind candidate,double controlMargin,double candidateMargin,double delta,double winCredit,int rootScore,long elapsedMillis,long maxDecisionNanos,int searchedDecisions,int exactShortcuts) { }
    public record FamilySummary(Kind family,int pairedGames,int seedClusters,double meanDelta,double winCredit,double minimumSeedMean,double maximumSeedMean) { }
    public record Summary(String protocol,String phase,String sourceIdentity,String referenceParity,String state,List<FamilySummary> families,List<Paired> games,List<String> retained) { }

    public static Summary run(Protocol protocol,ScoreProfile scoring,PolicySettings configured,Path output,Path registry,String sourceIdentity) throws IOException {
        if(Files.exists(output))throw new IllegalArgumentException("Fresh arena directory required");
        Files.createDirectories(output);JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("protocol.json").toFile(),protocol);
        EvaluationRegistry.expose(registry,protocol.players(),protocol.seeds(),protocol.phase(),protocol.id(),sourceIdentity);
        var settings=new PolicySettings(protocol.worlds(),configured.riskWeight(),configured.ownChainWeight(),configured.releaseWeight(),configured.skipThreatWeight());
        var pairs=new ArrayList<Paired>();var summaries=new ArrayList<FamilySummary>();var retained=new ArrayList<String>();
        for(Kind family:protocol.opponents()) {
            var candidates=new ArrayList<Episode>();var familyPairs=new ArrayList<Paired>();
            for(long seed:protocol.seeds()) {
                var control=play(protocol.players(),seed,-1,family,family,scoring,settings,false);
                for(int seat=0;seat<protocol.players();seat++) {
                    var episode=play(protocol.players(),seed,seat,protocol.candidate(),family,scoring,settings,true);
                    double controlMargin=margin(control.totals(),seat),candidateMargin=margin(episode.totals(),seat);
                    var paired=new Paired(seed,seat,family,protocol.candidate(),controlMargin,candidateMargin,candidateMargin-controlMargin,winCredit(episode.totals(),seat),episode.totals().get(seat),episode.elapsedMillis(),episode.maxDecisionNanos(),episode.searchedDecisions(),episode.exactShortcuts());
                    pairs.add(paired);familyPairs.add(paired);candidates.add(episode);
                    System.out.println("arena="+protocol.id()+" family="+family+" seed="+seed+" seat="+seat+" delta="+paired.delta());
                }
            }
            var seedMeans=new ArrayList<Double>();for(long seed:protocol.seeds())seedMeans.add(familyPairs.stream().filter(p->p.seed()==seed).mapToDouble(Paired::delta).average().orElseThrow());
            summaries.add(new FamilySummary(family,familyPairs.size(),protocol.seeds().size(),familyPairs.stream().mapToDouble(Paired::delta).average().orElseThrow(),familyPairs.stream().mapToDouble(Paired::winCredit).average().orElseThrow(),Collections.min(seedMeans),Collections.max(seedMeans)));
            var ranked=new ArrayList<>(familyPairs);ranked.sort(Comparator.comparingDouble(Paired::delta));
            var selected=new ArrayList<Paired>();for(int n=0;n<Math.min(protocol.retainPerFamily(),ranked.size());n++)selected.add(ranked.get(n));
            if(protocol.retainPerFamily()>0&&!ranked.isEmpty()&&!selected.contains(ranked.get(ranked.size()-1)))selected.add(ranked.get(ranked.size()-1));
            for(var pair:selected) {
                var episode=candidates.stream().filter(e->e.seed()==pair.seed()&&e.candidateSeat()==pair.seat()).findFirst().orElseThrow();
                String name="game-"+family.name().toLowerCase(Locale.ROOT)+"-"+pair.seed()+"-seat"+pair.seat()+".json";
                JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve(name).toFile(),Map.of("sourceIdentity",sourceIdentity,"kind","OFFLINE_LABORATORY_GAME_NOT_UI","paired",pair,"episode",episode));retained.add(name);
            }
        }
        var summary=new Summary(protocol.id(),protocol.phase(),sourceIdentity,"UNCONFIRMED_LABORATORY_SCORING","MEASURED_NOT_GLOBAL_OPTIMALITY",Card.immutable(summaries),Card.immutable(pairs),Card.immutable(retained));
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("summary.json").toFile(),summary);return summary;
    }
    public static Episode play(int players,long seed,int candidateSeat,Kind candidate,Kind family,ScoreProfile scoring,PolicySettings settings,boolean retainMoves) {
        long started=System.nanoTime(),maxNanos=0;int searches=0,shortcuts=0,actions=0;
        var game=new RentzMatch(players,seed,scoring);var hard=new RentzPlayer(RentzPlayer.Difficulty.HARD,scoring,settings);var normal=new RentzPlayer(RentzPlayer.Difficulty.NORMAL,scoring,settings);
        var moves=new ArrayList<Move>();
        while(game.observe(0).phase()!=RentzMatch.Phase.MATCH_END) {
            var publicView=game.observe(0);int actor=publicView.turn();Kind kind=actor==candidateSeat?candidate:family;
            if(publicView.phase()==RentzMatch.Phase.DEAL_END){game.nextHand();continue;}
            if(publicView.phase()==RentzMatch.Phase.CHOOSE)game.reveal(actor);
            var own=game.observe(actor);Object selected=null;SearchPolicy.Decision<?> receipt=null;String action;long t=System.nanoTime(),elapsed=0;
            if(own.phase()==RentzMatch.Phase.CHOOSE) {
                var decision=kind==Kind.BASELINE?new SearchPolicy.Decision<>(LabPolicy.choose(own),0,"BASELINE",List.of()):(kind==Kind.NORMAL?normal:hard).choose(own);
                elapsed=System.nanoTime()-t;receipt=decision;selected=decision.selected();action="CHOOSE";game.choose(actor,decision.selected(),false);
            } else if(own.legal().isEmpty()) {action="PASS";game.pass(actor);OfflineGameAudit.pass(own,game.observe(actor));}
            else {
                var decision=kind==Kind.BASELINE?new SearchPolicy.Decision<>(LabPolicy.card(own),0,"BASELINE",List.of()):(kind==Kind.NORMAL?normal:hard).card(own);
                elapsed=System.nanoTime()-t;receipt=decision;selected=decision.selected();action="PLAY";game.play(actor,decision.selected());OfflineGameAudit.card(own,decision.selected(),game.observe(actor),scoring);
            }
            if(actor==candidateSeat){maxNanos=Math.max(maxNanos,elapsed);if(receipt!=null&&receipt.worlds()>0)searches++;else if(receipt!=null&&receipt.method().startsWith("PUBLIC_")||receipt!=null&&receipt.method().startsWith("FORCED_"))shortcuts++;}
            if(retainMoves)moves.add(new Move(actions+1,own.handNumber(),actor,action,selected,actor==candidateSeat?own:null,actor==candidateSeat?receipt:null));
            if(++actions>20_000)throw new IllegalStateException("Offline match did not terminate");
        }
        validateHistory(game,players);
        return new Episode(seed,candidateSeat,family,candidate,game.observe(0).totals(),game.history(),Card.immutable(moves),(System.nanoTime()-started)/1_000_000,maxNanos,searches,shortcuts);
    }
    private static void validateHistory(RentzMatch game,int players) {
        if(game.history().size()!=players*8)throw new IllegalStateException("Offline schedule size");
        var selected=new ArrayList<Set<Contract>>();for(int seat=0;seat<players;seat++)selected.add(EnumSet.noneOf(Contract.class));int[] totals=new int[players];
        for(var hand:game.history()) {
            if(hand.chooser()!=(hand.number()-1)%players||!selected.get(hand.chooser()).add(hand.contract()))throw new IllegalStateException("Chooser schedule");
            for(int seat=0;seat<players;seat++){totals[seat]+=hand.points().get(seat);if(totals[seat]!=hand.totals().get(seat))throw new IllegalStateException("Cumulative score");}
        }
        for(int seat=0;seat<players;seat++)if(!selected.get(seat).equals(EnumSet.allOf(Contract.class))||totals[seat]!=game.observe(0).totals().get(seat))throw new IllegalStateException("Complete match audit");
    }
    private static double margin(List<Integer> totals,int seat){return totals.get(seat)-(totals.stream().mapToDouble(Integer::doubleValue).sum()-totals.get(seat))/(totals.size()-1);}
    private static double winCredit(List<Integer> totals,int seat){int max=Collections.max(totals);return totals.get(seat)==max?1.0/totals.stream().filter(n->n==max).count():0;}
    public static void main(String[] args) throws Exception {
        if(args.length!=5)throw new IllegalArgumentException("project protocol output registry source-manifest");
        Path project=Path.of(args[0]).toRealPath(),output=Path.of(args[2]).toAbsolutePath().normalize();
        if(output.startsWith(project))throw new IllegalArgumentException("Arena artifacts belong outside the project");
        var protocol=JSON.readValue(Path.of(args[1]).toFile(),Protocol.class);var identity=JSON.readTree(Path.of(args[4]).toFile());
        if(!identity.isArray()||identity.isEmpty())throw new IllegalArgumentException("Frozen input manifest required");
        var digest=MessageDigest.getInstance("SHA-256");
        for(var row:identity){Path file=project.resolve(row.required("path").textValue()).normalize();if(!file.startsWith(project)||!Files.isRegularFile(file))throw new IllegalArgumentException("Frozen input path");String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));if(!actual.equalsIgnoreCase(row.required("sha256").textValue()))throw new IllegalStateException("Frozen source drift: "+file);digest.update(row.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        var result=run(protocol,RuleProfiles.read(project.resolve("config/rentz-score-profiles.yaml")),PolicyInputs.read(project.resolve("config/rentz-policy.yaml"),project),output,Path.of(args[3]),HexFormat.of().formatHex(digest.digest()));
        OfflineArenaReport.publish(result,protocol,output.resolve("arena-extent.html"));
        System.out.println(JSON.writeValueAsString(result.families()));
        if(protocol.phase().equals("FRESH_EVALUATION")&&result.families().stream().anyMatch(f->f.meanDelta()<=0))throw new IllegalStateException("Fresh evaluation did not pass the predeclared positive-mean gate for every opponent family");
    }
}
