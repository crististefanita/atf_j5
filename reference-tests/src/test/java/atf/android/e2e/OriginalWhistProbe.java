package atf.android.e2e;

import atf.android.AdbClient;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.*;
import javax.imageio.ImageIO;

/** Backs up only the tested package; never restores or modifies its private files. */
final class OriginalWhistProbe {
    private final AdbClient adb;
    private final AndroidUiDriver ui;
    private final Path output;
    OriginalWhistProbe(AdbClient adb, AndroidUiDriver ui, Path output) {
        this.adb=adb;this.ui=ui;this.output=output;
    }
    OriginalWhistState observe(String label) throws Exception {
        return observe(label,true);
    }
    OriginalWhistState observe(String label, boolean resume) throws Exception {
        if(label.contains("bidding") || label.startsWith("probe"))ui.capture(label);
        else ui.captureImage(label);
        var image=ImageIO.read(output.resolve(label+".png").toFile());
        ui.back(); // The game saves its state when returning to its menu.
        Acquisition acquisition=backupState(label,image.getWidth(),image.getHeight());
        Files.write(output.resolve(label+".save2"),acquisition.save());
        OriginalWhistState state=acquisition.state();
        Files.writeString(output.resolve(label+"-state.txt"),state.summary()+"\n");
        if(resume) {
            // BackupManager kills the backup agent asynchronously after ADB returns.
            // Let that lifecycle complete before launching the saved original game.
            Thread.sleep(1800);
            Exception last=null;
            for(int attempt=0;attempt<3;attempt++) {
                try {
                    adb.run("shell","am","force-stop","com.qsoft.android.whist").requireSuccess();
                    adb.run("shell","am","start","-W","-n","com.qsoft.android.whist/.Whist").requireSuccess();
                    Thread.sleep(900);
                    String menu=ui.awaitText("Resume Game",Duration.ofSeconds(12));
                    ui.tapTextIn(menu,"Resume Game");
                    Thread.sleep(700);
                    if(state.bidding) {
                        String resumed=ui.awaitText("Bid 0",Duration.ofSeconds(15));
                        if(resumed.contains("package=\"com.qsoft.android.whist\""))return state;
                    } else {
                        // A SurfaceView game can have no accessibility root. Verify the resumed
                        // hand/score pixels directly, instead of waiting on nonexistent text nodes.
                        for(int check=0;check<4;check++) {
                            Path resumed=ui.captureImage(label+"-resumed-check");
                            double difference=handRegionDifference(image,ImageIO.read(resumed.toFile()));
                            String focus=adb.run("shell","dumpsys","window","windows").output();
                            if(difference<0.05&&focus.lines().anyMatch(l->l.contains("mCurrentFocus")&&l.contains("com.qsoft.android.whist"))) {
                                Files.writeString(output.resolve(label+"-state.txt"),"\nResumed lower-region changed fraction="+difference+"\n",StandardOpenOption.APPEND);
                                return state;
                            }
                            Thread.sleep(800);
                        }
                    }
                    throw new IllegalStateException("Original process exited during post-backup resume");
                } catch(Exception retryable){last=retryable;Thread.sleep(1800);}
            }
            throw new IllegalStateException("Cannot resume original after package backup",last);
        }
        return state;
    }
    private Acquisition backupState(String label,int width,int height)throws Exception {
        Exception last=null;
        for(int attempt=1;attempt<=3;attempt++) {
            Path backup=output.resolve(label+"-backup-attempt-"+attempt+".ab");
            var future=CompletableFuture.supplyAsync(()->{
                try{return adb.run("backup","-f",backup.toString(),"-noapk","-noshared","com.qsoft.android.whist");}
                catch(Exception e){throw new CompletionException(e);}
            });
            try {
                boolean confirmation=false;
                for(int i=0;i<20;i++) {
                    Thread.sleep(300);
                    String focus=adb.run("shell","dumpsys","window","windows").output();
                    if(focus.lines().anyMatch(l->l.contains("mCurrentFocus")&&l.contains("backupconfirm"))) {
                        confirmation=true;break;
                    }
                    if(future.isDone())break;
                }
                if(!confirmation)throw new IllegalStateException("Android package backup confirmation did not receive focus");
                adb.run("shell","input","tap",Integer.toString(width*3/4),Integer.toString(height*898/1000)).requireSuccess();
                future.get(95,TimeUnit.SECONDS).requireSuccess();
                byte[] save=OriginalWhistState.saveFileFromBackup(backup);
                OriginalWhistState state=OriginalWhistState.read(save);
                Files.copy(backup,output.resolve(label+".ab"),StandardCopyOption.REPLACE_EXISTING);
                return new Acquisition(save,state);
            }catch(InterruptedException interrupted){future.cancel(true);Thread.currentThread().interrupt();throw interrupted;}
            catch(Exception failure) {
                last=failure;future.cancel(true);
                Files.writeString(output.resolve(label+"-backup-errors.txt"),"attempt="+attempt+"; "+failure+"\n",
                        StandardOpenOption.CREATE,StandardOpenOption.APPEND);
                String focus=adb.run("shell","dumpsys","window","windows").output();
                if(focus.lines().anyMatch(l->l.contains("mCurrentFocus")&&l.contains("backupconfirm")))
                    adb.run("shell","input","keyevent","4").requireSuccess();
                Thread.sleep(1800);
            }
        }
        throw new java.io.IOException("Cannot acquire a valid original package backup after three attempts",last);
    }
    private record Acquisition(byte[] save,OriginalWhistState state) { }
    private static double handRegionDifference(java.awt.image.BufferedImage a,java.awt.image.BufferedImage b) {
        if(a.getWidth()!=b.getWidth()||a.getHeight()!=b.getHeight())return 1;
        long changed=0,total=0;
        for(int y=a.getHeight()*70/100;y<a.getHeight()*93/100;y+=3)for(int x=0;x<a.getWidth();x+=3) {
            int p=a.getRGB(x,y),q=b.getRGB(x,y);
            int delta=Math.abs(((p>>16)&255)-((q>>16)&255))+Math.abs(((p>>8)&255)-((q>>8)&255))+Math.abs((p&255)-(q&255));
            if(delta>48)changed++;total++;
        }
        return (double)changed/total;
    }
}
