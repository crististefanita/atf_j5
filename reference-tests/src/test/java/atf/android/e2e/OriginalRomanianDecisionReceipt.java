package atf.android.e2e;

import atf.reporting.contract.AtfAssertion;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Verifies observed input acceptance using only public actions and the observer's remaining count. */
final class OriginalRomanianDecisionReceipt {
    private OriginalRomanianDecisionReceipt() { }

    static List<AtfAssertion> bid(int selected,int accepted){
        return List.of(new AtfAssertion("Selected own bid was actually accepted",Integer.toString(selected),
                Integer.toString(accepted),selected>=0&&selected==accepted));
    }

    static List<AtfAssertion> card(int selected,int remainingBefore,
            List<OriginalRomanianAudit.PublicPlay> before,List<OriginalRomanianAudit.PublicPlay> after,int remainingAfter){
        boolean retained=after.size()>=before.size()&&after.subList(0,Math.min(before.size(),after.size())).equals(before);
        var observedPrefix=after.subList(0,Math.min(before.size(),after.size()));
        var additions=after.subList(Math.min(before.size(),after.size()),after.size());
        var own=additions.stream().filter(p->p.seat()==0).toList();
        return List.of(
                new AtfAssertion("Input receipt retains the complete prior public prefix",prefixReceipt(before),
                        prefixReceipt(observedPrefix)+"; total observed="+after.size(),retained),
                new AtfAssertion("Input produced exactly one new own public play","1",Integer.toString(own.size()),own.size()==1),
                new AtfAssertion("Selected own card equals the actual new public card",Integer.toString(selected),
                        own.stream().map(OriginalRomanianAudit.PublicPlay::card).toList().toString(),own.size()==1&&own.getFirst().card()==selected),
                new AtfAssertion("Accepted own card consumes exactly one own slot",Integer.toString(remainingBefore-1),
                        Integer.toString(remainingAfter),remainingBefore>0&&remainingAfter==remainingBefore-1));
    }

    /** Display is bounded; the acceptance boolean above still compares every actual prefix entry exactly. */
    private static String prefixReceipt(List<OriginalRomanianAudit.PublicPlay> prefix){
        var canonical=new StringBuilder();for(var play:prefix)canonical.append(play.seat()).append(':').append(play.card()).append(';');
        try{return "prefix length="+prefix.size()+"; SHA-256="+HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException unavailable){throw new IllegalStateException("Required SHA-256 digest is unavailable",unavailable);}
    }

    /** Same serial precedence as AdbClient.fromSystemProperties/setting; no device command is issued. */
    static String selectedAdbSerial(String property,String environment){
        String value=property;if(value==null||value.isBlank())value=environment;
        return value==null||value.isBlank()?"emulator-5554":value.trim();
    }
}
