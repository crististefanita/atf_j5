import org.teavm.backend.javascript.JSModuleType;
import org.teavm.tooling.ConsoleTeaVMToolLog;
import org.teavm.tooling.TeaVMProblemRenderer;
import org.teavm.tooling.TeaVMTool;
import java.io.File;

final class CompileRentzPage {
    private CompileRentzPage() { }

    public static void main(String[] args) throws Exception {
        var compiler = new TeaVMTool();
        compiler.setMainClass("ro.cardtable.browser.RentzPage");
        compiler.setTargetDirectory(new File(args[0]));
        compiler.setTargetFileName("rentz-engine.mjs");
        compiler.setJsModuleType(JSModuleType.ES2015);
        compiler.setObfuscated(true);
        var log = new ConsoleTeaVMToolLog(false);
        compiler.setLog(log);
        compiler.generate();
        var problems = compiler.getProblemProvider();
        if (!problems.getProblems().isEmpty()) {
            TeaVMProblemRenderer.describeProblems(compiler.getDependencyInfo().getCallGraph(), problems, log);
        }
        if (compiler.wasCancelled() || !problems.getSevereProblems().isEmpty()) {
            throw new IllegalStateException("Rentz engine compilation for the browser failed");
        }
    }
}
