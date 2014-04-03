/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental;

import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependencyInfo;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependencyInfoExtractor;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependencyInfoWriter;
import org.gradle.api.internal.tasks.compile.incremental.jar.ClasspathJarFinder;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarSnapshotFeeder;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.util.Clock;

class IncrementalCompilationFinalizer implements Compiler<JavaCompileSpec> {

    private static final Logger LOG = Logging.getLogger(IncrementalCompilationFinalizer.class);

    private final Compiler<JavaCompileSpec> delegate;
    private final ClassDependenciesAnalyzer analyzer;
    private final ClassDependencyInfoWriter dependencyInfoWriter;
    private final JarSnapshotFeeder jarSnapshotFeeder;
    private final ClasspathJarFinder classpathJarFinder;
    private final FileOperations fileOperations;

    public IncrementalCompilationFinalizer(Compiler<JavaCompileSpec> delegate, ClassDependenciesAnalyzer analyzer, ClassDependencyInfoWriter dependencyInfoWriter,
                                           JarSnapshotFeeder jarSnapshotFeeder, ClasspathJarFinder classpathJarFinder, FileOperations fileOperations) {
        this.delegate = delegate;
        this.analyzer = analyzer;
        this.dependencyInfoWriter = dependencyInfoWriter;
        this.jarSnapshotFeeder = jarSnapshotFeeder;
        this.classpathJarFinder = classpathJarFinder;
        this.fileOperations = fileOperations;
    }

    public WorkResult execute(JavaCompileSpec spec) {
        WorkResult out = delegate.execute(spec);

        Clock clock = new Clock();
        FileTree tree = fileOperations.fileTree(spec.getDestinationDir());
        ClassDependencyInfoExtractor extractor = new ClassDependencyInfoExtractor(analyzer);
        tree.visit(extractor);
        ClassDependencyInfo info = extractor.getDependencyInfo();
        dependencyInfoWriter.writeInfo(info);
        LOG.lifecycle("Performed class dependency analysis in {}, wrote results into {}", clock.getTime(), dependencyInfoWriter);

        clock = new Clock();
        jarSnapshotFeeder.storeJarSnapshots(classpathJarFinder.findJarArchives(spec.getClasspath()), info);
        LOG.lifecycle("Created and wrote jar snapshots in {}.", clock.getTime());

        return out;
    }
}