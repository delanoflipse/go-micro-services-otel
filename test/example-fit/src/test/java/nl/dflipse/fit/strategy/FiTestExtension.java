package nl.dflipse.fit.strategy;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

public class FiTestExtension implements TestTemplateInvocationContextProvider, AfterTestExecutionCallback {
    private final Queue<Faultload> queue = new LinkedList<>();

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        // Check if the annotation is present
        return context.getTestMethod().isPresent() &&
                context.getTestMethod().get().isAnnotationPresent(FiTest.class);
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
        // Retrieve the annotation and its parameters
        // var annotation = context.getTestMethod()
        // .orElseThrow()
        // .getAnnotation(FiTest.class);

        // Empty set of faults
        queue.offer(new Faultload());

        return Stream
                .<TestTemplateInvocationContext>generate(() -> new QueueBasedInvocationContext(queue.poll()))
                .takeWhile(ctx -> ((QueueBasedInvocationContext) ctx).getFaultload() != null);
    }

    // Inner class to represent each invocation context
    private static class QueueBasedInvocationContext implements TestTemplateInvocationContext {
        private final Faultload faultload;

        QueueBasedInvocationContext(Faultload faultload) {
            this.faultload = faultload;
        }

        public Faultload getFaultload() {
            return faultload;
        }

        @Override
        public String getDisplayName(int invocationIndex) {
            return "Invocation with parameter: " + faultload;
        }

        @Override
        public List<Extension> getAdditionalExtensions() {
            return List.of(new QueueParameterResolver(faultload));
        }
    }

    // Parameter resolver to inject the current parameter into the test
    private static class QueueParameterResolver implements ParameterResolver {
        private final Faultload faultload;

        QueueParameterResolver(Faultload faultload) {
            this.faultload = faultload;
        }

        @Override
        public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
            return parameterContext.getParameter().getType().equals(Faultload.class);
        }

        @Override
        public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
            return faultload;
        }
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        // Access the queue and test result
        // var testMethod = context.getTestMethod().orElseThrow();
        // var annotation = testMethod.getAnnotation(FiTest.class);
        var displayName = context.getDisplayName();

        // TODO: access faultload and test result
        // TODO: add new faultload to the queue

        System.out.println("After execution of test: " + displayName);
    }
}
