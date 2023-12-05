/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling;

import com.google.common.collect.Streams;
import org.gradle.api.NonNullApi;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleProject;
import org.gradle.plugins.ide.internal.tooling.model.IsolatedGradleProjectInternal;
import org.gradle.plugins.ide.internal.tooling.model.LaunchableGradleProjectTask;
import org.gradle.plugins.ide.internal.tooling.model.LaunchableGradleTask;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.internal.IntermediateToolingModelProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.gradle.api.internal.project.ProjectHierarchyUtils.getChildProjectsForInternalUse;

/**
 * Builds the {@link GradleProject} model that contains the project hierarchy and task information.
 */
@NonNullApi
public class IsolatedProjectsSafeGradleProjectBuilder implements ToolingModelBuilder, GradleProjectBuilderInternal {

    private final IntermediateToolingModelProvider intermediateToolingModelProvider;

    public IsolatedProjectsSafeGradleProjectBuilder(IntermediateToolingModelProvider intermediateToolingModelProvider) {
        this.intermediateToolingModelProvider = intermediateToolingModelProvider;
    }

    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals(GradleProject.class.getName());
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        return buildForRoot(project);
    }

    @Override
    public DefaultGradleProject buildForRoot(Project project) {
        requireRootProject(project);
        IsolatedGradleProjectParameter parameter = createParameter(GradleProjectBuilderOptions.shouldRealizeTasks());
        IsolatedGradleProjectInternal rootIsolatedModel = getRootIsolatedModel(project, parameter);
        return build((ProjectInternal) project, rootIsolatedModel, parameter);
    }

    private static void requireRootProject(Project project) {
        if (project != project.getRootProject()) {
            throw new IllegalArgumentException(String.format("%s can only be requested on the root project, got '%s'", GradleProject.class.getName(), project));
        }
    }

    private IsolatedGradleProjectInternal getRootIsolatedModel(Project rootProject, IsolatedGradleProjectParameter parameter) {
        return getIsolatedModels(singletonList(rootProject), parameter).get(0);
    }

    private DefaultGradleProject build(ProjectInternal project, IsolatedGradleProjectInternal isolatedModel, IsolatedGradleProjectParameter parameter) {
        DefaultGradleProject model = buildWithoutChildren(project, isolatedModel);
        Collection<Project> children = getChildProjectsForInternalUse(project);
        List<IsolatedGradleProjectInternal> isolatedChildrenModels = getIsolatedModels(children, parameter);
        model.setChildren(buildChildren(model, parameter, children, isolatedChildrenModels));
        return model;
    }

    private List<DefaultGradleProject> buildChildren(
        DefaultGradleProject parent,
        IsolatedGradleProjectParameter parameter,
        Collection<Project> children,
        List<IsolatedGradleProjectInternal> isolatedChildrenModels
    ) {
        return Streams.zip(children.stream(), isolatedChildrenModels.stream(), (c, ic) -> build((ProjectInternal) c, ic, parameter))
            .map(it -> it.setParent(parent))
            .collect(toList());
    }

    private List<IsolatedGradleProjectInternal> getIsolatedModels(Collection<Project> projects, IsolatedGradleProjectParameter parameter) {
        return intermediateToolingModelProvider
            .getModels(new ArrayList<>(projects), IsolatedGradleProjectInternal.class, parameter);
    }

    private static DefaultGradleProject buildWithoutChildren(ProjectInternal project, IsolatedGradleProjectInternal isolatedModel) {
        DefaultGradleProject model = new DefaultGradleProject();

        model.setProjectIdentifier(isolatedModel.getProjectIdentifier())
            .setName(isolatedModel.getName())
            .setDescription(isolatedModel.getDescription())
            .setBuildDirectory(isolatedModel.getBuildDirectory())
            .setProjectDirectory(isolatedModel.getProjectDirectory())
            .setBuildTreePath(project.getIdentityPath().getPath());

        model.getBuildScript().setSourceFile(isolatedModel.getBuildScript().getSourceFile());

        Collection<LaunchableGradleTask> isolatedTasks = isolatedModel.getTasks();
        if (!isolatedTasks.isEmpty()) {
            model.setTasks(isolatedTasks.stream().map(it -> buildProjectTask(model, it)).collect(toList()));
        }

        return model;
    }

    private static LaunchableGradleProjectTask buildProjectTask(DefaultGradleProject owner, LaunchableGradleTask model) {
        LaunchableGradleProjectTask target = new LaunchableGradleProjectTask();
        target.setPath(model.getPath())
            .setName(model.getName())
            .setGroup(model.getGroup())
            .setDisplayName(model.getDisplayName())
            .setDescription(model.getDescription())
            .setPublic(model.isPublic())
            .setProjectIdentifier(model.getProjectIdentifier())
            .setBuildTreePath(model.getBuildTreePath());
        target.setProject(owner);
        return target;
    }

    private static IsolatedGradleProjectParameter createParameter(boolean realizeTasks) {
        return () -> realizeTasks;
    }

}
