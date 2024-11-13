/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.NonNullApi;
import org.gradle.api.problems.internal.DefaultProblemsSummaryProgressDetails;
import org.gradle.api.problems.internal.Problem;
import org.gradle.internal.build.event.BuildEventSubscriptions;
import org.gradle.internal.build.event.types.DefaultOperationFinishedProgressEvent;
import org.gradle.internal.build.event.types.DefaultOperationStartedProgressEvent;
import org.gradle.internal.build.event.types.DefaultProblemDescriptor;
import org.gradle.internal.build.event.types.DefaultProblemEvent;
import org.gradle.internal.build.event.types.DefaultProblemSummary;
import org.gradle.internal.build.event.types.DefaultProblemsSummariesDetails;
import org.gradle.internal.build.event.types.DefaultRootOperationDescriptor;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.launcher.exec.RunBuildBuildOperationType;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.protocol.InternalProblemSummary;
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalProgressEvent;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static org.gradle.tooling.internal.provider.runner.ClientForwardingBuildOperationListener.toOperationResult;
import static org.gradle.tooling.internal.provider.runner.ProblemsProgressEventConsumer.toInternalId;

@NonNullApi
class RootBuildOperationMapper implements BuildOperationMapper<RunBuildBuildOperationType.Details, DefaultRootOperationDescriptor> {

    private boolean rootRequested;
    private boolean problemsRequested;

    @Override
    public boolean isEnabled(BuildEventSubscriptions subscriptions) {
        // TODO (donat) the operation mapper infrastructure cannot handle mapping multiple operation types with a same parent id
        this.rootRequested = subscriptions.isRequested(OperationType.ROOT);
        this.problemsRequested = subscriptions.isRequested(OperationType.PROBLEMS);
        return rootRequested || problemsRequested;
    }

    @Override
    public Class<RunBuildBuildOperationType.Details> getDetailsType() {
        return RunBuildBuildOperationType.Details.class;
    }

    @Nullable
    @Override
    public InternalProgressEvent createProgressEvent(DefaultRootOperationDescriptor descriptor, OperationProgressEvent progressEvent) {
        if (problemsRequested && progressEvent.getDetails() instanceof DefaultProblemsSummaryProgressDetails) {
            DefaultProblemsSummaryProgressDetails summariesDetails = (DefaultProblemsSummaryProgressDetails) progressEvent.getDetails();
            List<InternalProblemSummary> internalIdCounts = summariesDetails.getProblemIdCounts().stream()
                .map(it -> new DefaultProblemSummary(toInternalId(it.left), it.right))
                .collect(toImmutableList());
            return new DefaultProblemEvent(new DefaultProblemDescriptor(descriptor.getId(), descriptor.getParentId()), new DefaultProblemsSummariesDetails(internalIdCounts)
            );
        } else {
            return null;
        }
    }


    @Override
    public DefaultRootOperationDescriptor createDescriptor(RunBuildBuildOperationType.Details details, BuildOperationDescriptor buildOperation, @Nullable OperationIdentifier parent) {
        OperationIdentifier id = buildOperation.getId();
        String displayName = buildOperation.getDisplayName();
        return new DefaultRootOperationDescriptor(id, displayName, displayName, parent);
    }

    @Override
    public InternalOperationStartedProgressEvent createStartedEvent(DefaultRootOperationDescriptor descriptor, RunBuildBuildOperationType.Details details, OperationStartEvent startEvent) {
        return new DefaultOperationStartedProgressEvent(startEvent.getStartTime(), descriptor);
    }

    @Override
    public InternalOperationFinishedProgressEvent createFinishedEvent(DefaultRootOperationDescriptor descriptor, RunBuildBuildOperationType.Details details, OperationFinishEvent finishEvent) {
        Map<Throwable, Collection<Problem>> problemsForThrowables = details.getProblemsForThrowables();
        if (rootRequested) {
            return new DefaultOperationFinishedProgressEvent(finishEvent.getEndTime(), descriptor, ClientForwardingBuildOperationListener.toOperationResult(finishEvent, problemsForThrowables));
        } else {
            return new DefaultOperationFinishedProgressEvent(finishEvent.getEndTime(), descriptor, toOperationResult(finishEvent));
        }
    }
}
