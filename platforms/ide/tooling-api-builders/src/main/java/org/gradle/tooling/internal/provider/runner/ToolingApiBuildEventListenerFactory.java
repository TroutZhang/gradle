/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.tooling.internal.provider.runner;

import com.google.common.collect.ImmutableList;
import org.gradle.api.NonNullApi;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.internal.build.event.BuildEventListenerFactory;
import org.gradle.internal.build.event.BuildEventSubscriptions;
import org.gradle.internal.build.event.OperationResultPostProcessor;
import org.gradle.internal.build.event.OperationResultPostProcessorFactory;
import org.gradle.internal.operations.BuildOperationAncestryTracker;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.events.OperationType;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.function.Supplier;

import static com.google.common.collect.ImmutableList.toImmutableList;

@NonNullApi
public class ToolingApiBuildEventListenerFactory implements BuildEventListenerFactory {
    private final BuildOperationAncestryTracker ancestryTracker;
    private final BuildOperationIdFactory idFactory;
    private final List<OperationResultPostProcessorFactory> postProcessorFactories;

    ToolingApiBuildEventListenerFactory(BuildOperationAncestryTracker ancestryTracker, BuildOperationIdFactory idFactory, List<OperationResultPostProcessorFactory> postProcessorFactories) {
        this.ancestryTracker = ancestryTracker;
        this.idFactory = idFactory;
        this.postProcessorFactories = postProcessorFactories;
    }

    @Override
    public Iterable<Object> createListeners(BuildEventSubscriptions subscriptions, BuildEventConsumer consumer) {
        if (!subscriptions.isAnyOperationTypeRequested()) {
            return ImmutableList.of();
        }

        ProgressEventConsumer progressEventConsumer = new ProgressEventConsumer(consumer, ancestryTracker);

        ImmutableList.Builder<Object> listeners = ImmutableList.builder();

        if (subscriptions.isRequested(OperationType.TEST) && subscriptions.isRequested(OperationType.TEST_OUTPUT)) {
            listeners.add(new ClientForwardingTestOutputOperationListener(progressEventConsumer, idFactory));
        }

        if (subscriptions.isRequested(OperationType.BUILD_PHASE)) {
            listeners.add(new BuildPhaseOperationListener(progressEventConsumer, idFactory));
        }

        listeners.add(createClientBuildEventGenerator(subscriptions, consumer, progressEventConsumer));
        return listeners.build();
    }

    private ClientBuildEventGenerator createClientBuildEventGenerator(BuildEventSubscriptions subscriptions, BuildEventConsumer consumer, ProgressEventConsumer progressEventConsumer) {
        BuildOperationListener buildListener = createBuildOperationListener(subscriptions, progressEventConsumer);

        OperationDependenciesResolver operationDependenciesResolver = new OperationDependenciesResolver();

        PluginApplicationTracker pluginApplicationTracker = new PluginApplicationTracker(ancestryTracker);
        TestTaskExecutionTracker testTaskTracker = new TestTaskExecutionTracker(ancestryTracker);
        ProjectConfigurationTracker projectConfigurationTracker = new ProjectConfigurationTracker(ancestryTracker, pluginApplicationTracker);
        TaskOriginTracker taskOriginTracker = new TaskOriginTracker(pluginApplicationTracker);

        TransformOperationMapper transformOperationMapper = new TransformOperationMapper(operationDependenciesResolver);
        operationDependenciesResolver.addLookup(transformOperationMapper);

        List<OperationResultPostProcessor> postProcessors = createPostProcessors(subscriptions, consumer);

        TaskOperationMapper taskOperationMapper = new TaskOperationMapper(postProcessors, taskOriginTracker, operationDependenciesResolver);
        operationDependenciesResolver.addLookup(taskOperationMapper);

        List<BuildOperationMapper<?, ?>> mappers = ImmutableList.of(
            new FileDownloadOperationMapper(),
            new TestOperationMapper(testTaskTracker),
            new ProjectConfigurationOperationMapper(projectConfigurationTracker),
            taskOperationMapper,
            transformOperationMapper,
            new WorkItemOperationMapper(),
            new RootBuildOperationMapper()
        );
        return new ClientBuildEventGenerator(progressEventConsumer, subscriptions, mappers, buildListener);
    }

    private List<OperationResultPostProcessor> createPostProcessors(BuildEventSubscriptions subscriptions, BuildEventConsumer consumer) {
        return postProcessorFactories.stream()
            .map(factory -> factory.createProcessors(subscriptions, consumer))
            .flatMap(List::stream)
            .collect(toImmutableList());
    }

    private BuildOperationListener createBuildOperationListener(BuildEventSubscriptions subscriptions, ProgressEventConsumer progressEventConsumer) {
        if (subscriptions.isRequested(OperationType.PROBLEMS)) {
            return createProblemsProgressConsumer(progressEventConsumer);
        }
        if (subscriptions.isRequested(OperationType.GENERIC)) {
            return new ClientForwardingBuildOperationListener(progressEventConsumer);
        }
        return NO_OP;
    }

    @Nonnull
    private ProblemsProgressEventConsumer createProblemsProgressConsumer(ProgressEventConsumer progressEventConsumer) {
        Supplier<OperationIdentifier> operationIdentifierSupplier = () -> new OperationIdentifier(idFactory.nextId());
        return new ProblemsProgressEventConsumer(progressEventConsumer, operationIdentifierSupplier);
    }

    private static final BuildOperationListener NO_OP = new BuildOperationListener() {
        @Override
        public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        }

        @Override
        public void progress(OperationIdentifier buildOperationId, OperationProgressEvent progressEvent) {
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
        }
    };
}
