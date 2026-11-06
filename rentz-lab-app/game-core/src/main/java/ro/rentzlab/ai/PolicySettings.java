package ro.rentzlab.ai;

/** Search budget and tactical coefficients; rules and score magnitudes are separate inputs. */
public record PolicySettings(int worlds,double riskWeight,double ownChainWeight,double releaseWeight,double skipThreatWeight,Objective objective,Tactic tactic,int maxTransitions) {
    public enum Objective { HAND_MARGIN, MATCH_POINTS, MATCH_WIN_SHARE }
    public enum Tactic { LEGACY, PUBLIC_TARGET_AWARE, PUBLIC_TEN_PROOF_ONLY }
    public static final int DEFAULT_MAX_TRANSITIONS=65536;
    public PolicySettings(int worlds,double riskWeight,double ownChainWeight,double releaseWeight,double skipThreatWeight){this(worlds,riskWeight,ownChainWeight,releaseWeight,skipThreatWeight,Objective.HAND_MARGIN);}
    public PolicySettings(int worlds,double riskWeight,double ownChainWeight,double releaseWeight,double skipThreatWeight,Objective objective){this(worlds,riskWeight,ownChainWeight,releaseWeight,skipThreatWeight,objective,Tactic.LEGACY,DEFAULT_MAX_TRANSITIONS);}
    public PolicySettings {
        if(objective==null||tactic==null||maxTransitions<1||maxTransitions>1_000_000||worlds<1||worlds>128||!valid(riskWeight)||riskWeight>1||!valid(ownChainWeight)||!valid(releaseWeight)||!valid(skipThreatWeight))
            throw new IllegalArgumentException("Finite nonnegative weights, objective/tactic, 1..128 worlds and 1..1000000 transitions required");
    }
    private static boolean valid(double value){return Double.isFinite(value)&&value>=0&&value<=100;}
    public static PolicySettings handMarginReference(){return new PolicySettings(8,0.05,3.0,0.3,2.0,Objective.HAND_MARGIN);}
    public static PolicySettings defaults(){return handMarginReference();}
}
