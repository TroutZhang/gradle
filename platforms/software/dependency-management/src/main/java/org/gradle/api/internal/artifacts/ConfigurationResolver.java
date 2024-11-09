/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.artifacts.transform.DefaultTransformUpstreamDependenciesResolver;
import org.gradle.internal.model.CalculatedValue;

import java.util.List;

/**
 * Resolves {@link ResolveContext}s and produces {@link ResolverResults}.
 * <p>
 * This resolution is lenient, except for some fatal failure cases,
 * in the sense that resolution failures in most cases will not cause exceptions
 * to be thrown. Instead, recoverable failures are packaged in the result type.
 */
public interface ConfigurationResolver {
    /**
     * Traverses enough of the graph to calculate the build dependencies of the given resolve context. All failures are packaged in the result.
     *
     * @param resolveContext The resolve context to resolve.
     * @param futureCompleteResults The future value of the output of {@link #resolveGraph(ResolveContext)}. See
     * {@link DefaultTransformUpstreamDependenciesResolver} for why this is needed.
     */
    ResolverResults resolveBuildDependencies(ResolveContext resolveContext, CalculatedValue<ResolverResults> futureCompleteResults);

    /**
     * Traverses the full dependency graph of the given resolve context. All failures are packaged in the result.
     */
    ResolverResults resolveGraph(ResolveContext resolveContext) throws ResolveException;

    /**
     * Returns the list of repositories available to resolve a given resolve context.
     */
    List<ResolutionAwareRepository> getAllRepositories();
}
