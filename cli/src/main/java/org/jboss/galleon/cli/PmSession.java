/*
 * Copyright 2016-2018 Red Hat, Inc. and/or its affiliates
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
package org.jboss.galleon.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.xml.stream.XMLStreamException;
import org.aesh.command.activator.CommandActivator;
import org.aesh.command.activator.CommandActivatorProvider;
import org.aesh.command.activator.OptionActivator;
import org.aesh.command.activator.OptionActivatorProvider;
import org.aesh.command.completer.CompleterInvocation;
import org.aesh.command.completer.CompleterInvocationProvider;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.invocation.CommandInvocationProvider;
import org.aesh.readline.AeshContext;
import org.aesh.readline.Prompt;
import org.aesh.utils.Config;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositoryListener;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.jboss.galleon.ArtifactRepositoryManager;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.cli.config.Configuration;
import org.jboss.galleon.cli.model.FeatureContainer;
import org.jboss.galleon.cli.model.state.State;
import org.jboss.galleon.config.UniverseConfig;
import org.jboss.galleon.universe.FeaturePackLocation.FPID;
import org.jboss.galleon.universe.UniverseResolver;
import org.jboss.galleon.universe.UniverseSpec;

/**
 *
 * @author Alexey Loubyansky
 */
public class PmSession implements CommandInvocationProvider<PmCommandInvocation>, CompleterInvocationProvider<PmCompleterInvocation>,
        CommandActivatorProvider, OptionActivatorProvider<OptionActivator> {

    private class MavenListener implements RepositoryListener {

        private static final String MAVEN = "[MAVEN] ";

        private boolean active;
        void setActive(boolean active) {
            this.active = active;
        }

        @Override
        public void artifactDownloaded(RepositoryEvent re) {
            if (active && re != null) {
                String artifact = re.getArtifact().getGroupId() + ":"
                        + re.getArtifact().getArtifactId() + ":"
                        + re.getArtifact().getVersion() + ":"
                        + re.getArtifact().getExtension();
                if (re.getException() == null) {
                    println(MAVEN + "downloaded " + artifact
                            + " from " + re.getRepository().getId());
                } else if (re.getException() instanceof ArtifactNotFoundException) {
                    println(MAVEN + "artifact " + artifact + " not found in " + re.getRepository().getId());
                } else {
                    println(MAVEN + re.getException().getLocalizedMessage() + " while downloading artifact " + artifact);
                }
            }
        }

        @Override
        public void artifactDownloading(RepositoryEvent re) {
            if (active && re != null) {
                println(MAVEN + "attempting to download " + re.getArtifact().getGroupId() + ":"
                        + re.getArtifact().getArtifactId() + ":"
                        + re.getArtifact().getVersion() + ":"
                        + re.getArtifact().getExtension()
                        + (re.getRepository() != null ? " from " + re.getRepository().getId() : ""));
            }
        }

        @Override
        public void artifactDescriptorInvalid(RepositoryEvent re) {
            //session.println("artifactDescriptorInvalid " + re);
        }

        @Override
        public void artifactDescriptorMissing(RepositoryEvent re) {
            //session.println("artifactDescriptorMissing " + re);
        }

        @Override
        public void metadataInvalid(RepositoryEvent re) {
            //session.println("metadataInvalid " + re);
        }

        @Override
        public void artifactResolving(RepositoryEvent re) {
            //session.println("artifactResolving " + re);
        }

        @Override
        public void artifactResolved(RepositoryEvent re) {
            //session.println("artifactResolved " + re);
        }

        @Override
        public void metadataResolving(RepositoryEvent re) {
            //session.println("metadataResolving " + re);

        }

        @Override
        public void metadataResolved(RepositoryEvent re) {
            //session.println("metadataResolved " + re);

        }

        @Override
        public void metadataDownloading(RepositoryEvent re) {
            //session.println("metadataDownloading " + re);
        }

        @Override
        public void metadataDownloaded(RepositoryEvent re) {
            //session.println("metadataDownloaded " + re);
        }

        @Override
        public void artifactInstalling(RepositoryEvent re) {
            //session.println("artifactInstalling " + re);
        }

        @Override
        public void artifactInstalled(RepositoryEvent re) {
            //session.println("artifactInstalled " + re);
        }

        @Override
        public void metadataInstalling(RepositoryEvent re) {
            //session.println("metadataInstalling " + re);
        }

        @Override
        public void metadataInstalled(RepositoryEvent re) {
            //session.println("metadataInstalled " + re);
        }

        @Override
        public void artifactDeploying(RepositoryEvent re) {
            //session.println("artifactDeploying " + re);
        }

        @Override
        public void artifactDeployed(RepositoryEvent re) {
            //session.println("artifactDeployed " + re);
        }

        @Override
        public void metadataDeploying(RepositoryEvent re) {
            //session.println("metadataDeploying " + re);
        }

        @Override
        public void metadataDeployed(RepositoryEvent re) {
            //session.println("metadataDeployed " + re);
        }
    }
    private PrintStream out;
    private PrintStream err;
    private final Configuration config;
    private final Universes universes;

    private State state;
    private FeatureContainer exploredContainer;
    private String currentPath;
    private final MavenArtifactRepositoryManager maven;
    private final UniverseResolver universeResolver;
    private final MavenListener mavenListener;
    private final Map<String, UniverseConfig> universeAliases = new HashMap<>();

    public PmSession(Configuration config) throws Exception {
        this.config = config;
        this.mavenListener = new MavenListener();
        this.maven = new MavenArtifactRepositoryManager(config.getMavenConfig(),
                mavenListener);

        universeResolver = UniverseResolver.builder().addArtifactResolver(maven).build();
        for (UniverseConfig uc : config.getUniverseConfig().getUniverses()) {
            universeResolver.getUniverse(uc.getSpec());
        }
        //Build the universes
        this.universes = Universes.buildUniverses(config, maven);
    }

    public void addUniverse(String name, String factory, String location) throws ProvisioningException, XMLStreamException, IOException {
        if (universeAliases.containsKey(name)) {
            throw new ProvisioningException("Universe already registered: " + name);
        }
        // side effect is to add a universe
        universeResolver.getUniverse(new UniverseSpec(factory, location));
        UniverseConfig uConfig = new UniverseConfig(name, factory, location);
        config.getUniverseConfig().addUniverse(uConfig);
        universeAliases.put(name, uConfig);
    }

    public void removeUniverse(String name) throws ProvisioningException, XMLStreamException, IOException {
        if (!universeAliases.containsKey(name)) {
            throw new ProvisioningException("Universe not registered: " + name);
        }
        config.getUniverseConfig().removeUniverse(name);
        universeAliases.remove(name);
    }

    public Set<String> getUniverseNames() {
        return universeAliases.keySet();
    }

    public void commandStart() {
        maven.commandStart();
    }

    public void commandEnd() {
        maven.commandEnd();
    }

    public void setState(State session) {
        this.state = session;
    }

    public State getState() {
        return state;
    }

    public void setExploredContainer(FeatureContainer exploredContainer) {
        this.exploredContainer = exploredContainer;
    }

    public FeatureContainer getExploredContainer() {
        return exploredContainer;
    }

    public FeatureContainer getContainer() {
        if (state != null) {
            return state.getContainer();
        }
        if (exploredContainer != null) {
            return exploredContainer;
        }
        return null;
    }

    public String getCurrentPath() {
        if (state != null) {
            return state.getPath();
        }
        if (currentPath != null) {
            return currentPath;
        }
        return null;
    }

    public void setCurrentPath(String currentPath) {
        if (state != null) {
            state.setPath(currentPath);
        }
        if (currentPath != null) {
            this.currentPath = currentPath;
        }
    }

    public void println(String txt) {
        out.print(txt + Config.getLineSeparator());
    }

    public void print(String txt) {
        out.print(txt);
    }

    public Configuration getPmConfiguration() {
        return config;
    }

    public Universes getUniverses() {
        return universes;
    }

    public ArtifactRepositoryManager getArtifactResolver() {
        return maven;
    }

    public UniverseResolver getUniverseResolver() {
        return universeResolver;
    }

    // TO REMOVE when we have an universe for sure.
    public boolean hasPopulatedUniverse() {
        for (Universe u : universes.getUniverses()) {
            if (!u.getStreamLocations().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static Prompt buildPrompt(AeshContext aeshCtx) {
        return buildPrompt(aeshCtx.getCurrentWorkingDirectory().getName());
    }

    public static Prompt buildPrompt(String name) {
        return new Prompt(new StringBuilder().append('[')
                .append(name)
                .append("]$ ").toString());
    }

    public static Path getWorkDir(AeshContext aeshCtx) {
        return Paths.get(aeshCtx.getCurrentWorkingDirectory().getAbsolutePath());
    }

    @Override
    public PmCommandInvocation enhanceCommandInvocation(CommandInvocation commandInvocation) {
        return new PmCommandInvocation(this, out, err, commandInvocation);
    }

    void setOut(PrintStream out) {
        this.out = out;
    }

    void setErr(PrintStream err) {
        this.err = err;
    }

    @Override
    public PmCompleterInvocation enhanceCompleterInvocation(CompleterInvocation completerInvocation) {
        return new PmCompleterInvocation(completerInvocation, this);
    }

    @Override
    public CommandActivator enhanceCommandActivator(CommandActivator ca) {
        if (ca instanceof PmCommandActivator) {
            ((PmCommandActivator) ca).setPmSession(this);
        }
        return ca;
    }

    @Override
    public OptionActivator enhanceOptionActivator(OptionActivator oa) {
        if (oa instanceof PmOptionActivator) {
            ((PmOptionActivator) oa).setPmSession(this);
        }
        return oa;
    }

    public boolean existsInLocalRepository(FPID fpid) {
        Path local = getPmConfiguration().getMavenConfig().getLocalRepository();
        String grp = fpid.getChannel().getProducer().replaceAll("\\.", "/");
        grp = grp.replaceAll(":", "/");
        String vers = fpid.getBuild();
        return Files.exists(Paths.get(local.toString(), grp, vers));
    }

    public void downloadFp(FPID fpid) throws ProvisioningException {
        getUniverseResolver().resolve(fpid.getLocation());
    }

    public void enableMavenTrace(boolean b) {
        mavenListener.setActive(b);
    }
}
