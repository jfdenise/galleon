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
package org.jboss.galleon.api;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.galleon.DefaultMessageWriter;
import org.jboss.galleon.MessageWriter;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.api.config.GalleonFeaturePackConfig;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.core.builder.LocalFP;
import org.jboss.galleon.progresstracking.DefaultProgressTracker;
import org.jboss.galleon.progresstracking.ProgressCallback;
import org.jboss.galleon.progresstracking.ProgressTracker;
import org.jboss.galleon.universe.UniverseResolver;
import org.jboss.galleon.universe.UniverseResolverBuilder;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.util.IoUtils;
import org.jboss.galleon.core.builder.ProvisioningContextBuilder;
import org.jboss.galleon.impl.ProvisioningUtil;
import org.jboss.galleon.universe.FeaturePackLocation.FPID;

public class Provisioning implements AutoCloseable {

    public static class Builder extends UniverseResolverBuilder<Builder> {

        private Path installationHome;
        private MessageWriter messageWriter;
        private UniverseResolver resolver;
        private boolean logTime;
        private boolean recordState = true;

        private Builder() {
        }

        public Builder setInstallationHome(Path installationHome) {
            this.installationHome = installationHome;
            return this;
        }

        public Builder setMessageWriter(MessageWriter messageWriter) {
            this.messageWriter = messageWriter;
            return this;
        }

        public Builder setLogTime(boolean logTime) {
            this.logTime = logTime;
            return this;
        }

        public Builder setRecordState(boolean recordState) {
            this.recordState = recordState;
            return this;
        }

        public Provisioning build() throws ProvisioningException {
            return new Provisioning(this);
        }

        protected UniverseResolver getUniverseResolver() throws ProvisioningException {
            return resolver == null ? buildUniverseResolver() : resolver;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final Path home;
    private final MessageWriter log;
    private boolean logTime;

    private final UniverseResolver universeResolver;
    private boolean recordState;
    private final Map<String, ProgressTracker<?>> progressTrackers = new HashMap<>();

    private final Path tmp;
    private Map<FPID, LocalFP> locals = new HashMap<>();

    private Provisioning(Builder builder) throws ProvisioningException {
        this.home = builder.installationHome;
        this.log = builder.messageWriter == null ? DefaultMessageWriter.getDefaultInstance() : builder.messageWriter;

        universeResolver = builder.getUniverseResolver();
        this.logTime = builder.logTime;
        this.recordState = builder.recordState;
        try {
            tmp = Files.createTempDirectory("galleon-tmp");
        } catch (IOException ex) {
            throw new ProvisioningException(ex);
        }
    }

    /**
     * Location of the installation.
     *
     * @return location of the installation
     */
    public Path getInstallationHome() {
        return home;
    }

    /**
     * Whether to log provisioning time
     *
     * @return Whether provisioning time should be logged at the end
     */
    public boolean isLogTime() {
        return logTime;
    }

    /**
     * Whether to log provisioning time
     *
     * @return Whether provisioning time should be logged at the end
     */
    public void setLogTime(boolean logTime) {
        this.logTime = logTime;
    }

    /**
     * Whether provisioning state will be recorded after (re-)provisioning.
     *
     * @return true if the provisioning state is recorded after provisioning,
     * otherwise false
     */
    public boolean isRecordState() {
        return recordState;
    }

    public void setRecordState(boolean recordState) {
        this.recordState = recordState;
    }

    public void setProgressCallback(String id, ProgressCallback<?> callback) {
        if (callback == null) {
            progressTrackers.remove(id);
        } else {
            progressTrackers.put(id, new DefaultProgressTracker<>(callback));
        }
    }

    public void setProgressTracker(String id, ProgressTracker<?> tracker) {
        if (tracker == null) {
            progressTrackers.remove(id);
        } else {
            progressTrackers.put(id, tracker);
        }
    }

    public static boolean isFeaturePack(Path path) {
        return ProvisioningUtil.isFeaturePack(path);
    }

    public static GalleonFeaturePackDescription getFeaturePackDescription(Path path) throws ProvisioningException {
        return ProvisioningUtil.getFeaturePackDescription(path);
    }

    public ProvisioningContext buildProvisioningContext(Path provisioning) throws ProvisioningException {
        MavenRepoManager repoManager = (MavenRepoManager) universeResolver.getArtifactResolver(MavenRepoManager.REPOSITORY_ID);
        try {
            String coreVersion = ProvisioningUtil.getCoreVersion(provisioning, universeResolver, tmp);
            //System.out.println("REQUIRED CORE VERSION is " + coreVersion);

            URLClassLoader loader = getCallerClassLoader(coreVersion, repoManager);
            Class<?> callerClass = ProvisioningUtil.getCallerClass(loader);

            try {
                ProvisioningContextBuilder provisioner = (ProvisioningContextBuilder) callerClass.getConstructor().newInstance();
                ProvisioningContext ctx = provisioner.buildProvisioningContext(loader,
                        home,
                        provisioning,
                        log,
                        logTime,
                        recordState,
                        repoManager,
                        progressTrackers,
                        locals);
                return ctx;
            } catch (Exception ex) {
                throw new ProvisioningException(ex);
            }

        } catch (Exception ex) {
            throw new ProvisioningException(ex);
        } finally {
            IoUtils.recursiveDelete(tmp);
        }
    }

    public FeaturePackLocation addLocal(Path path, boolean installInUniverse) throws ProvisioningException {
        final FeaturePackLocation.FPID fpid;
        try {
            fpid = ProvisioningUtil.getFeaturePackProducer(path);
        } catch (Exception ex) {
            throw new ProvisioningException(ex);
        }
        locals.put(fpid, new LocalFP(fpid, path, installInUniverse));
        return fpid.getLocation();
    }

    public ProvisioningContext buildProvisioningContext(GalleonProvisioningConfig config) throws ProvisioningException {
        return buildProvisioningContext(config, Collections.emptyList());
    }

    public ProvisioningContext buildProvisioningContext(GalleonProvisioningConfig config, List<Path> customConfigs) throws ProvisioningException {
        MavenRepoManager repoManager = (MavenRepoManager) universeResolver.getArtifactResolver(MavenRepoManager.REPOSITORY_ID);
        String coreVersion = APIVersion.getVersion();
        try {
            for (GalleonFeaturePackConfig fp : config.getFeaturePackDeps()) {
                LocalFP local = locals.get(fp.getLocation().getFPID());
                Path resolvedFP = null;
                if (local == null) {
                    resolvedFP = universeResolver.resolve(fp.getLocation());
                } else {
                    resolvedFP = local.getPath();
                }
                coreVersion = ProvisioningUtil.getCoreVersion(resolvedFP, coreVersion, tmp, universeResolver);
            }
            //System.out.println("REQUIRED CORE VERSION is " + coreVersion);
            URLClassLoader loader = getCallerClassLoader(coreVersion, repoManager);
            Class<?> callerClass = ProvisioningUtil.getCallerClass(loader);

            try {
                ProvisioningContextBuilder provisioner = (ProvisioningContextBuilder) callerClass.getConstructor().newInstance();
                ProvisioningContext ctx = provisioner.buildProvisioningContext(loader,
                        home,
                        config,
                        customConfigs,
                        log,
                        logTime,
                        recordState,
                        repoManager,
                        progressTrackers,
                        locals);
                return ctx;
            } catch (Exception ex) {
                throw new ProvisioningException(ex);
            }

        } catch (Exception ex) {
            throw new ProvisioningException(ex);
        } finally {
            IoUtils.recursiveDelete(tmp);
        }
    }

    private URLClassLoader getCallerClassLoader(String version, MavenRepoManager repoManager) throws ProvisioningException {
        MavenArtifact coreArtifact = new MavenArtifact();
        coreArtifact.setGroupId("org.jboss.galleon");
        coreArtifact.setArtifactId("galleon-core");
        coreArtifact.setVersion(version);
        coreArtifact.setExtension("jar");
        try {
            repoManager.resolve(coreArtifact);
        } catch (MavenUniverseException ex) {
            throw new ProvisioningException(ex);
        }
        MavenArtifact callerArtifact = new MavenArtifact();
        callerArtifact.setGroupId("org.jboss.galleon");
        callerArtifact.setArtifactId("galleon-core-caller");
        // This one is always the one from the tooling.
        callerArtifact.setVersion(APIVersion.getVersion());
        callerArtifact.setExtension("jar");
        repoManager.resolve(callerArtifact);

        URL[] cp = new URL[2];
        try {
            cp[0] = coreArtifact.getPath().toFile().toURI().toURL();
            cp[1] = callerArtifact.getPath().toFile().toURI().toURL();
            return new URLClassLoader(cp, Thread.currentThread().getContextClassLoader());
        } catch (Exception ex) {
            throw new ProvisioningException(ex);
        }
    }

    @Override
    public void close() {
        IoUtils.recursiveDelete(tmp);
    }

}
