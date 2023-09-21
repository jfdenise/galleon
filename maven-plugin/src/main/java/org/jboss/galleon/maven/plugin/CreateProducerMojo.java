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

import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.galleon.maven.plugin.util.MavenArtifactRepositoryManager;
import org.jboss.galleon.maven.plugin.util.MvnMessageWriter;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenProducerInstaller;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.universe.maven.repo.SimplisticMavenRepoManager;

/**
 * Creates a new Maven artifact containing a producer spec.
 *
 * @author Alexey Loubyansky
 */
@Mojo(name = "create-producer")
public class CreateProducerMojo extends AbstractMojo {

    @Component
    protected RepositorySystem repoSystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    protected RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    protected MavenSession session;

    @Parameter( defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true )
    private List<RemoteRepository> repositories;

    @Component
    private MavenProjectHelper projectHelper;

    /**
     * Producer name
     */
    @Parameter(required = true)
    private String name;

    /**
     * Producer groupId
     */
    @Parameter(required = true, defaultValue="${project.groupId}")
    private String groupId;

    /**
     * Producer artifactId
     */
    @Parameter(required = true, defaultValue="${project.artifactId}")
    private String artifactId;

    /**
     * Producer version
     */
    @Parameter(required = true, defaultValue="${project.version}")
    private String version;

    /**
     * Feature-pack groupId
     */
    @Parameter(required = true, alias="feature-pack-groupId")
    private String featurePackGroupId;

    /**
     * Feature-pack artifactId
     */
    @Parameter(required = true, alias="feature-pack-artifactId")
    private String featurePackArtifactId;

    /**
     * Channel frequencies
     */
    @Parameter(required = true)
    private List<String> frequencies = Collections.emptyList();

    /**
     * Channels
     */
    @Parameter(required = true)
    private List<ChannelDescription> channels = Collections.emptyList();

    /**
     * Specifies whether creating the producer should be skipped.
     *
     * @since 4.2.6
     */
    @Parameter(defaultValue = "false", property = PropertyNames.SKIP)
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping the create-producer goal.");
            return;
        }
        try {
            createProducer();
        } catch (MavenUniverseException e) {
            throw new MojoExecutionException("Failed to create producer artifact", e);
        }
    }

    private void createProducer() throws MavenUniverseException, MojoExecutionException {
        final MavenArtifact producerArtifact = new MavenArtifact()
                .setGroupId(groupId)
                .setArtifactId(artifactId)
                .setVersion(version);
        final MavenProducerInstaller installer = new MavenProducerInstaller(
                name,
                SimplisticMavenRepoManager.getInstance(
                        Paths.get(project.getBuild().getDirectory()).resolve("local-repo"),
                        new MavenArtifactRepositoryManager(repoSystem, repoSession, repositories)),
                producerArtifact,
                featurePackGroupId, featurePackArtifactId, new MvnMessageWriter(getLog()));

        for(String frequency : frequencies) {
            installer.addFrequency(frequency);
        }

        final Set<String> names = new HashSet<>(channels.size());
        for(ChannelDescription channel : channels) {
            if(!names.add(channel.name)) {
                throw new MojoExecutionException("Duplicate channel " + channel.name);
            }
            try {
                installer.addChannel(channel.name, channel.versionRange);
            } catch (MavenUniverseException e) {
                throw new MojoExecutionException("Failed to add channel " + channel.name, e);
            }
        }
        try {
            installer.install();
        } catch (MavenUniverseException e) {
            throw new MojoExecutionException("Failed to create producer", e);
        }
        projectHelper.attachArtifact(project, "jar", producerArtifact.getPath().toFile());
    }
}
