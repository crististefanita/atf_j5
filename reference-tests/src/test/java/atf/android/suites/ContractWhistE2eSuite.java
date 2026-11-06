package atf.android.suites;

import atf.android.e2e.ContractWhistGameplayE2eTest;
import atf.testkit.AtfE2eTest;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/** Serialized live gameplay acceptance against the installed Contract Whist app. */
@Suite
@SelectClasses(ContractWhistGameplayE2eTest.class)
@AtfE2eTest
@Execution(ExecutionMode.SAME_THREAD)
public class ContractWhistE2eSuite { }
