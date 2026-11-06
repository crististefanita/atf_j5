package atf.rentz.evaluation;

import java.util.*;

/** Paired bootstrap over independent seed clusters; rotations remain inside their cluster. */
public final class ClusteredEvaluation {
    private ClusteredEvaluation(){}
    public enum Metric { MARGIN, POINTS, WIN_SHARE }
    public record Sample(long seed,int seat,double marginDelta,double pointsDelta,double winDelta) {
        public Sample {if(seat<0||!Double.isFinite(marginDelta)||!Double.isFinite(pointsDelta)||!Double.isFinite(winDelta)||Math.abs(winDelta)>1)throw new IllegalArgumentException("Paired sample");}
    }
    public record Cluster(long seed,int rotations,double marginDelta,double pointsDelta,double winDelta){}
    public record Assessment(Metric metric,int seedClusters,double mean,Double lowerBound,double familyAdjustedConfidence,boolean sufficient,boolean promotes,List<Cluster> clusters){public Assessment{clusters=List.copyOf(clusters);}}
    public static Assessment assess(List<Sample> samples,int players,Metric metric,int families){
        if(samples.isEmpty()||players<3||players>6||metric==null||families<1||families>3)throw new IllegalArgumentException("Cluster protocol");
        var grouped=new TreeMap<Long,List<Sample>>();for(var sample:samples){if(sample.seat()>=players)throw new IllegalArgumentException("Seat outside table");grouped.computeIfAbsent(sample.seed(),s->new ArrayList<>()).add(sample);}
        var clusters=new ArrayList<Cluster>();
        for(var entry:grouped.entrySet()){
            var rows=entry.getValue();if(rows.size()!=players||rows.stream().map(Sample::seat).distinct().count()!=players)throw new IllegalArgumentException("Exactly one result for every rotated seat required");
            clusters.add(new Cluster(entry.getKey(),players,rows.stream().mapToDouble(Sample::marginDelta).average().orElseThrow(),rows.stream().mapToDouble(Sample::pointsDelta).average().orElseThrow(),rows.stream().mapToDouble(Sample::winDelta).average().orElseThrow()));
        }
        double[] values=clusters.stream().mapToDouble(c->value(c,metric)).toArray();double mean=Arrays.stream(values).average().orElseThrow();
        double confidence=1-0.05/families;boolean sufficient=values.length>=8;Double lower=null;
        if(sufficient){var rng=new Random(0x4d415443485632L);var bootstrap=new double[4096];for(int n=0;n<bootstrap.length;n++){double sum=0;for(int i=0;i<values.length;i++)sum+=values[rng.nextInt(values.length)];bootstrap[n]=sum/values.length;}Arrays.sort(bootstrap);lower=bootstrap[Math.max(0,(int)Math.floor((1-confidence)*bootstrap.length))];}
        return new Assessment(metric,values.length,mean,lower,confidence,sufficient,sufficient&&lower>0,clusters);
    }
    private static double value(Cluster cluster,Metric metric){return switch(metric){case MARGIN->cluster.marginDelta();case POINTS->cluster.pointsDelta();case WIN_SHARE->cluster.winDelta();};}
}
