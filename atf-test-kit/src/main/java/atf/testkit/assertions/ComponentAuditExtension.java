package atf.testkit.assertions;

import atf.testkit.lifecycle.FailureAwareCleanup;
import atf.testkit.scope.TestRunScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.*;
import org.junit.platform.commons.support.AnnotationSupport;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.LongSupplier;
import atf.testkit.timing.InvocationTimer;

/** Composable JUnit adapter; consumers supply publication without exposing report types to Kit. */
public final class ComponentAuditExtension implements BeforeEachCallback, AfterEachCallback,
        ParameterResolver, InvocationInterceptor, LifecycleMethodExecutionExceptionHandler {
    public interface Publication extends AutoCloseable {
        AssertionValueDisplay display();
        default ComponentAssertionAudit.AssertionRequirement assertionRequirement() {
            return ComponentAssertionAudit.AssertionRequirement.ANY_PHASE;
        }
        void publish(ComponentAssertionAudit.Result result) throws Exception;
        @Override void close() throws Exception;
    }
    @FunctionalInterface public interface Factory { Publication open(ExtensionContext context) throws Exception; }
    @FunctionalInterface public interface ScopedPublication<T> {
        Publication open(T resource, ExtensionContext context) throws Exception;
    }
    private record State(ComponentAssertionAudit audit, Publication publication) { }
    private record ClassTimingKey(ComponentAssertionAudit.Phase phase) { }
    private final ExtensionContext.Namespace namespace;
    private final Factory factory;
    private final LongSupplier ticks;

    public ComponentAuditExtension(Object owner, Factory factory) {
        this(owner, factory, System::nanoTime);
    }

    public ComponentAuditExtension(Object owner, Factory factory, LongSupplier ticks) {
        namespace = ExtensionContext.Namespace.create(Objects.requireNonNull(owner));
        this.factory = Objects.requireNonNull(factory);
        this.ticks = Objects.requireNonNull(ticks, "ticks");
    }

    /** Shares only the acquired resource; each invocation still gets an independent audit and publication. */
    public static <T extends AutoCloseable> ComponentAuditExtension scoped(Class<?> owner, Class<T> resourceType,
            Function<ExtensionContext, T> factory, ScopedPublication<T> publication) {
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(publication, "publication");
        return new ComponentAuditExtension(owner, context -> {
            T resource = TestRunScope.selected(context).resource(context, owner, "component-audit", resourceType)
                    .getOrCreate(() -> factory.apply(context)).value();
            return publication.open(resource, context);
        });
    }

    @Override public void handleBeforeAllMethodExecutionException(ExtensionContext context, Throwable failure) throws Throwable {
        containerFailure(context, ComponentAssertionAudit.Phase.SETUP, failure);
    }

    @Override public void handleAfterAllMethodExecutionException(ExtensionContext context, Throwable failure) throws Throwable {
        containerFailure(context, ComponentAssertionAudit.Phase.TEARDOWN, failure);
    }

    private void containerFailure(ExtensionContext context, ComponentAssertionAudit.Phase phase, Throwable failure) throws Throwable {
        try (Publication publication = factory.open(context)) {
            var audit = new ComponentAssertionAudit(publication.display(), ticks, publication.assertionRequirement());
            audit.start(phase);
            classTimer(context, phase).snapshot().ifPresent(value -> audit.timing(phase, value));
            audit.failed(phase, failure);
            publication.publish(audit.finish(failure));
        } catch (Throwable secondary) {
            if (failure instanceof org.opentest4j.TestAbortedException) {
                throw FailureAwareCleanup.combine(secondary, failure);
            }
            FailureAwareCleanup.combine(failure, secondary);
        }
        throw failure;
    }

    @Override public void beforeEach(ExtensionContext context) throws Exception {
        Publication publication = factory.open(context);
        try {
            var audit = new ComponentAssertionAudit(publication.display(), ticks, publication.assertionRequirement());
            context.getStore(namespace).put(State.class, new State(audit, publication));
        } catch (RuntimeException | Error failure) {
            FailureAwareCleanup.closeAfterFailure(failure, publication);
            throw failure;
        }
    }

    @Override public void afterEach(ExtensionContext context) throws Exception {
        State state = context.getStore(namespace).remove(State.class, State.class);
        if (state == null) return;
        Throwable primary = context.getExecutionException().orElse(null);
        var result = state.audit().finish(primary);
        Throwable publicationFailure = FailureAwareCleanup.runAll(null,
                () -> state.publication().publish(result), state.publication()::close);
        if (publicationFailure != null) {
            if (result.outcome() == ComponentAssertionAudit.Outcome.ABORTED) {
                throwFailure(FailureAwareCleanup.combine(publicationFailure, primary));
            }
            Throwable failure = FailureAwareCleanup.combine(result.failure(), publicationFailure);
            if (primary == null || result.propagate()) throwFailure(failure);
        }
        if (result.propagate()) throwFailure(result.failure());
    }

    @Override public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        return parameter.getParameter().getType() == RecordedAssertions.class;
    }

    @Override public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        var executable = parameter.getDeclaringExecutable();
        var phase = AnnotationSupport.isAnnotated(executable, BeforeEach.class)
                ? ComponentAssertionAudit.Phase.SETUP : AnnotationSupport.isAnnotated(executable, AfterEach.class)
                ? ComponentAssertionAudit.Phase.TEARDOWN : ComponentAssertionAudit.Phase.TEST_BODY;
        return state(context).audit().recorder(phase);
    }

    @Override public void interceptBeforeEachMethod(Invocation<Void> call, ReflectiveInvocationContext<Method> method,
                                                   ExtensionContext context) throws Throwable {
        invoke(call, method, context, ComponentAssertionAudit.Phase.SETUP);
    }
    @Override public void interceptTestMethod(Invocation<Void> call, ReflectiveInvocationContext<Method> method,
                                             ExtensionContext context) throws Throwable {
        invoke(call, method, context, ComponentAssertionAudit.Phase.TEST_BODY);
    }
    @Override public void interceptTestTemplateMethod(Invocation<Void> call, ReflectiveInvocationContext<Method> method,
                                                     ExtensionContext context) throws Throwable {
        invoke(call, method, context, ComponentAssertionAudit.Phase.TEST_BODY);
    }
    @Override public void interceptAfterEachMethod(Invocation<Void> call, ReflectiveInvocationContext<Method> method,
                                                  ExtensionContext context) throws Throwable {
        invoke(call, method, context, ComponentAssertionAudit.Phase.TEARDOWN);
    }

    private void invoke(Invocation<Void> call, ReflectiveInvocationContext<Method> method,
                        ExtensionContext context, ComponentAssertionAudit.Phase phase) throws Throwable {
        var audit = state(context).audit();
        try (var scope = AuditedAssertionScope.open(method.getExecutable().getDeclaringClass().getName(),
                method.getExecutable().getName(), audit.recorder(phase))) {
            try (var interval = audit.measure(phase)) { call.proceed(); }
        } catch (Throwable failure) {
            audit.failed(phase, failure);
            throw failure;
        }
    }

    @Override public void interceptBeforeAllMethod(Invocation<Void> call, ReflectiveInvocationContext<Method> method,
                                                   ExtensionContext context) throws Throwable {
        try (var interval = classTimer(context, ComponentAssertionAudit.Phase.SETUP).start()) { call.proceed(); }
    }
    @Override public void interceptAfterAllMethod(Invocation<Void> call, ReflectiveInvocationContext<Method> method,
                                                  ExtensionContext context) throws Throwable {
        try (var interval = classTimer(context, ComponentAssertionAudit.Phase.TEARDOWN).start()) { call.proceed(); }
    }
    private InvocationTimer classTimer(ExtensionContext context, ComponentAssertionAudit.Phase phase) {
        return context.getStore(namespace).getOrComputeIfAbsent(new ClassTimingKey(phase),
                ignored -> new InvocationTimer(ticks), InvocationTimer.class);
    }

    private State state(ExtensionContext context) {
        var state = context.getStore(namespace).get(State.class, State.class);
        if (state == null) throw new IllegalStateException("Component audit requires a per-method JUnit invocation");
        return state;
    }
    private static void throwFailure(Throwable failure) throws Exception {
        if (failure instanceof Exception exception) throw exception;
        throw (Error) failure;
    }
}
