package atf.testkit.reporting;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Test fixture policy, independent of the executable owner's layer declaration. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@AtfReportPath
@interface TestReportPathOwner { }
