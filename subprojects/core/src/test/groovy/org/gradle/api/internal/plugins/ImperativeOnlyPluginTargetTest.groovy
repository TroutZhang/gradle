/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.problems.internal.DefaultProblems
import org.gradle.api.problems.internal.ExceptionProblemContainer
import org.gradle.api.problems.internal.ProblemSummarizer
import org.gradle.internal.operations.CurrentBuildOperationRef
import org.gradle.internal.operations.OperationIdentifier
import spock.lang.Specification

class ImperativeOnlyPluginTargetTest extends Specification {

    def problemEmitter = Mock(ProblemSummarizer)
    def currentBuildOperationRef = Mock(CurrentBuildOperationRef) {
        getId() >> new OperationIdentifier(42)
    }
    def problems = new DefaultProblems(
        problemEmitter,
        null,
        currentBuildOperationRef,
        new ExceptionProblemContainer(),
        null
    )

    def "mismatched plugin application target is detected"() {
        def pluginTarget = new ImperativeOnlyPluginTarget(PluginTargetType.PROJECT, Mock(ProjectInternal), problems)

        when:
        pluginTarget.applyImperative("someId", new Plugin<Settings>() {
            @Override
            void apply(Settings target) {
                throw new IllegalStateException("unreachable")
            }
        })

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "The plugin must be applied in a settings script (or to the Settings object), but was applied in a build script (or to the Project object)"

        1 * problemEmitter.emit(_, _)
    }

    def "custom ClassCastExceptions are not replaced on apply"() {
        def pluginTarget = new ImperativeOnlyPluginTarget(PluginTargetType.PROJECT, Mock(ProjectInternal), problems)

        when:
        pluginTarget.applyImperative("someId", new Plugin<Project>() {
            @Override
            void apply(Project target) {
                throw new ClassCastException("custom error")
            }
        })

        then:
        def e = thrown(ClassCastException)
        e.message == "custom error"

        0 * problemEmitter.emit(_, _)
    }

}
