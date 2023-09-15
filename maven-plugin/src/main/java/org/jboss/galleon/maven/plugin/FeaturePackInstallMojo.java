/*
 * Copyright 2016-2023 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.galleon.maven.plugin;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.artifact.ArtifactCoordinate;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.jboss.galleon.maven.plugin.util.ArtifactItem;
import org.jboss.galleon.maven.plugin.util.MavenArtifactRepositoryManager;
import org.jboss.galleon.tooling.api.ConfigurationId;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;

/**
 * This maven plugin  installs a feature-pack into an empty directory or a
 * directory that already contains an installation, in which case the product
 * the feature-pack represents will be integrated into an existing installation.
 *
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 * @author Alexey Loubyansky (c) 2017 Red Hat, inc.
 */
@Mojo(name = "install-feature-pack", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PROCESS_TEST_RESOURCES)
public class FeaturePackInstallMojo extends AbstractMojo {

    // These WildFly specific props should be cleaned up
    private static final String MAVEN_REPO_LOCAL = "maven.repo.local";

    @Component
    protected RepositorySystem repoSystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    protected RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    protected MavenSession session;

    /**
    * The target installation directory.
    */
    @Parameter(alias = "install-dir", required = true)
    private File installDir;

    /**
     * Path to a file containing `config` that should be installed.
    */
    @Parameter(alias = "custom-config", required = false)
    private File customConfig;

    /**
     * Whether to install the default package set.
     */
    @Parameter(alias = "inherit-packages", required = false, defaultValue = "true")
    private boolean inheritPackages;

    /**
     * Whether to inherit the default feature-pack configs.
     */
    @Parameter(alias = "inherit-configs", required = false, defaultValue = "true")
    private boolean inheritConfigs;

    /**
     * Legacy Galleon 1.x feature-pack artifact coordinates as (groupId - artifactId - version)
     * if not specified the feature-pack must be a transitive dependency of a feature-pack with
     * the version specified.
     *
     * NOTE: either this parameter or 'location' must be configured.
     */
    @Parameter(alias = "feature-pack", required = false)
    private ArtifactItem featurePack;

    /**
     * Galleon2 feature-pack location
     *
     * NOTE: either this parameter or 'feature-pack' must be configured.
     */
    @Parameter(required = false)
    private String location;

    /**
     * Default feature-pack configs that should be included.
     */
    @Parameter(alias = "included-configs", required = false)
    private List<ConfigurationId> includedConfigs = Collections.emptyList();;

    /**
     * Explicitly excluded packages from the installation.
    */
    @Parameter(alias = "excluded-packages", required = false)
    private List<String> excludedPackages = Collections.emptyList();;

    /**
     * Explicitly included packages to install.
    */
    @Parameter(alias = "included-packages", required = false)
    private List<String> includedPackages = Collections.emptyList();

    /**
     * Arbitrary plugin options recognized by the plugins attached to the feature-pack being installed.
    */
    @Parameter(alias = "plugin-options", required = false)
    private Map<String, String> pluginOptions = Collections.emptyMap();

    /**
     * Whether to record provisioned state in .galleon directory.
     */
    @Parameter(alias = "record-state", defaultValue = "true")
    private boolean recordState = true;

    /**
     * Specifies whether installing the feature pack should be skipped.
     *
     * @since 4.2.6
     */
    @Parameter(defaultValue = "false", property = PropertyNames.SKIP)
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping the install-feature-pack goal.");
            return;
        }

        FeaturePackLocation fpl = null;
        Path localPath = null;
        if (featurePack != null) {
            localPath = resolveMaven(featurePack, new MavenArtifactRepositoryManager(repoSystem, repoSession));
        } else if(location != null) {
            fpl = FeaturePackLocation.fromString(location);
        } else {
            throw new MojoExecutionException("Either 'location' or 'feature-pack' must be configured");
        }

//        final FeaturePackInstaller fpInstaller = FeaturePackInstaller.newInstance(
//                repoSession.getLocalRepository().getBasedir().toPath(),
//                installDir.toPath())
//                .setFpl(fpl)
//                .setLocalArtifact(localPath)
//                .setInheritConfigs(inheritConfigs)
//                .includeConfigs(includedConfigs)
//                .setInheritPackages(inheritPackages)
//                .includePackages(includedPackages)
//                .excludePackages(excludedPackages)
//                .setPluginOptions(pluginOptions);
//        if(customConfig != null) {
//            //fpInstaller.setCustomConfig(customConfig.toPath().toAbsolutePath());
//        }
//
//        final String originalMavenRepoLocal = System.getProperty(MAVEN_REPO_LOCAL);
//        System.setProperty(MAVEN_REPO_LOCAL, session.getSettings().getLocalRepository());
//        try {
//            fpInstaller.install();
//        } finally {
//            if(originalMavenRepoLocal == null) {
//                System.clearProperty(MAVEN_REPO_LOCAL);
//            } else {
//                System.setProperty(MAVEN_REPO_LOCAL, originalMavenRepoLocal);
//            }
//        }
    }

    private Path resolveMaven(ArtifactCoordinate coordinate, MavenRepoManager resolver) throws MojoExecutionException {
        final MavenArtifact artifact = new MavenArtifact();
        artifact.setGroupId(coordinate.getGroupId());
        artifact.setArtifactId(coordinate.getArtifactId());
        artifact.setVersion(coordinate.getVersion());
        artifact.setExtension(coordinate.getExtension());
        artifact.setClassifier(coordinate.getClassifier());
        try {
            resolver.resolve(artifact);
        } catch (MavenUniverseException e) {
            throw new MojoExecutionException("Failed to resolve artifact " + artifact, e);
        }
        return artifact.getPath();
    }
}
