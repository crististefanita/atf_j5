package atf.testkit.assertions;

import org.junit.jupiter.api.extension.*;
import java.lang.reflect.Method;

/** Explicit composition of the shared JUnit protocol with a consumer-owned publication factory. */
public interface ComponentAuditAdapter extends BeforeEachCallback, AfterEachCallback,
        ParameterResolver, InvocationInterceptor, LifecycleMethodExecutionExceptionHandler {
    ComponentAuditExtension auditExtension();
    @Override default void interceptBeforeAllMethod(Invocation<Void> call, ReflectiveInvocationContext<Method> method,
                                                   ExtensionContext context) throws Throwable {
        auditExtension().interceptBeforeAllMethod(call, method, context);
    }
    @Override default void interceptAfterAllMethod(Invocation<Void> call, ReflectiveInvocationContext<Method> method,
                                                  ExtensionContext context) throws Throwable {
        auditExtension().interceptAfterAllMethod(call, method, context);
    }
    @Override default void handleBeforeAllMethodExecutionException(ExtensionContext context, Throwable failure) throws Throwable {
        auditExtension().handleBeforeAllMethodExecutionException(context, failure);
    }
    @Override default void handleAfterAllMethodExecutionException(ExtensionContext context, Throwable failure) throws Throwable {
        auditExtension().handleAfterAllMethodExecutionException(context, failure);
    }
    @Override default void beforeEach(ExtensionContext context) throws Exception { auditExtension().beforeEach(context); }
    @Override default void afterEach(ExtensionContext context) throws Exception { auditExtension().afterEach(context); }
    @Override default boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        return auditExtension().supportsParameter(parameter, context);
    }
    @Override default Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        return auditExtension().resolveParameter(parameter, context);
    }
    @Override default void interceptBeforeEachMethod(Invocation<Void> call, ReflectiveInvocationContext<Method> method,
                                                    ExtensionContext context) throws Throwable {
        auditExtension().interceptBeforeEachMethod(call, method, context);
    }
    @Override default void interceptTestMethod(Invocation<Void> call, ReflectiveInvocationContext<Method> method,
                                              ExtensionContext context) throws Throwable {
        auditExtension().interceptTestMethod(call, method, context);
    }
    @Override default void interceptTestTemplateMethod(Invocation<Void> call, ReflectiveInvocationContext<Method> method,
                                                      ExtensionContext context) throws Throwable {
        auditExtension().interceptTestTemplateMethod(call, method, context);
    }
    @Override default void interceptAfterEachMethod(Invocation<Void> call, ReflectiveInvocationContext<Method> method,
                                                   ExtensionContext context) throws Throwable {
        auditExtension().interceptAfterEachMethod(call, method, context);
    }
}
