package ro.rentzlab.core;

/** Search budget and tactical coefficients; rules and score magnitudes are separate inputs. */
public record PolicySettings(int worlds,double riskWeight,double ownChainWeight,double releaseWeight,double skipThreatWeight) {
    public PolicySettings {
        if(worlds<1||worlds>128||!valid(riskWeight)||riskWeight>1||!valid(ownChainWeight)||!valid(releaseWeight)||!valid(skipThreatWeight))
            throw new IllegalArgumentException("Finite nonnegative policy settings and 1..128 worlds required");
    }
    private static boolean valid(double value){return Double.isFinite(value)&&value>=0&&value<=100;}
    public static PolicySettings defaults(){return new PolicySettings(8,0.05,3.0,0.3,2.0);}
}
