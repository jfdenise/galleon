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
import org.jboss.galleon.BaseErrors;

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
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.util.IoUtils;
import org.jboss.galleon.core.builder.ProvisioningContextBuilder;
import org.jboss.galleon.impl.GalleonClassLoaderHandler;
import org.jboss.galleon.impl.ProvisioningUtil;
import org.jboss.galleon.universe.FeaturePackLocation.FPID;
import org.jboss.galleon.universe.UniverseSpec;
import org.jboss.galleon.util.PathsUtils;

class ProvisioningImpl implements Provisioning, GalleonClassLoaderHandler {

    private static class ClassLoaderUsage {

        int num = 1;
        URLClassLoader loader;
    }

    private final Path home;
    private final MessageWriter log;
    private boolean logTime;

    private final UniverseResolver universeResolver;
    private boolean recordState;
    private final Map<String, ProgressTracker<?>> progressTrackers = new HashMap<>();

    private final Path tmp;
    private Map<FPID, LocalFP> locals = new HashMap<>();

    private static Map<String, ClassLoaderUsage> classLoaders = new HashMap<>();

    ProvisioningImpl(ProvisioningBuilder builder) throws ProvisioningException {
        this.home = builder.getInstallationHome();
        this.log = builder.getMessageWriter() == null ? DefaultMessageWriter.getDefaultInstance() : builder.getMessageWriter();

        universeResolver = builder.getUniverseResolver();
        this.logTime = builder.isLogTime();
        this.recordState = builder.isRecordState();
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
    @Override
    public Path getInstallationHome() {
        return home;
    }

    /**
     * Whether to log provisioning time
     *
     * @return Whether provisioning time should be logged at the end
     */
    @Override
    public boolean isLogTime() {
        return logTime;
    }

    /**
     * Whether provisioning state will be recorded after (re-)provisioning.
     *
     * @return true if the provisioning state is recorded after provisioning,
     * otherwise false
     */
    @Override
    public boolean isRecordState() {
        return recordState;
    }

    @Override
    public void setProgressCallback(String id, ProgressCallback<?> callback) {
        if (callback == null) {
            progressTrackers.remove(id);
        } else {
            progressTrackers.put(id, new DefaultProgressTracker<>(callback));
        }
    }

    @Override
    public void setProgressTracker(String id, ProgressTracker<?> tracker) {
        if (tracker == null) {
            progressTrackers.remove(id);
        } else {
            progressTrackers.put(id, tracker);
        }
    }

    @Override
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
                        locals, this);
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

    @Override
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

    @Override
    public ProvisioningContext buildProvisioningContext(GalleonProvisioningConfig config) throws ProvisioningException {
        return buildProvisioningContext(config, Collections.emptyList());
    }

    @Override
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
                        locals, this);
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

    @Override
    public void release(String version) throws ProvisioningException {
        releaseUsage(version);
    }

    private static synchronized void releaseUsage(String version) throws ProvisioningException {
        ClassLoaderUsage usage = classLoaders.get(version);
        if(usage == null) {
            throw new ProvisioningException("Releasing usage of core " + version + " although no usage");
        }
        if (usage.num <= 0) {
             throw new ProvisioningException("Releasing usage of core " + version + " although all usages released");
        }
        usage.num-=1;
        if (usage.num == 0) {
            try {
                //System.out.println("CLEANUP " + version);
                usage.loader.close();
            } catch (IOException ex) {
                throw new ProvisioningException(ex);
            }
            classLoaders.remove(version);
        }
    }

    private static synchronized URLClassLoader getCallerClassLoader(String version, MavenRepoManager repoManager) throws ProvisioningException {
        ClassLoaderUsage usage = classLoaders.get(version);
        if (usage == null) {
            //System.out.println("NEW USAGE OF " + version);
            usage = new ClassLoaderUsage();
            classLoaders.put(version, usage);
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
                usage.loader = new URLClassLoader(cp, Thread.currentThread().getContextClassLoader());
            } catch (Exception ex) {
                throw new ProvisioningException(ex);
            }
        } else {
            //System.out.println("REUSE OF " + version);
            usage.num += 1;
        }
        return usage.loader;
    }

    @Override
    public void close() {
        IoUtils.recursiveDelete(tmp);
    }

    // Required by CLI
    /**
     * Add named universe spec to the provisioning configuration
     *
     * @param name universe name
     * @param universeSpec universe spec
     * @throws ProvisioningException in case of an error
     */
    @Override
    public void addUniverse(String name, UniverseSpec universeSpec) throws ProvisioningException {
        final GalleonProvisioningConfig config = GalleonProvisioningConfig.builder(getProvisioningConfig()).addUniverse(name, universeSpec).build();
        try {
            buildProvisioningContext(config).storeProvisioningConfig(PathsUtils.getProvisioningXml(home));
        } catch (Exception e) {
            throw new ProvisioningException(BaseErrors.writeFile(PathsUtils.getProvisioningXml(home)), e);
        }
    }

    /**
     * Removes universe spec associated with the name from the provisioning
     * configuration
     *
     * @param name name of the universe spec or null for the default universe
     * spec
     * @throws ProvisioningException in case of an error
     */
    @Override
    public void removeUniverse(String name) throws ProvisioningException {
        GalleonProvisioningConfig config = getProvisioningConfig();
        if (config == null || !config.hasUniverse(name)) {
            return;
        }
        config = GalleonProvisioningConfig.builder(config).removeUniverse(name).build();
        try {
            buildProvisioningContext(config).storeProvisioningConfig(PathsUtils.getProvisioningXml(home));
        } catch (Exception e) {
            throw new ProvisioningException(BaseErrors.writeFile(PathsUtils.getProvisioningXml(home)), e);
        }
    }

    /**
     * Set the default universe spec for the installation
     *
     * @param universeSpec universe spec
     * @throws ProvisioningException in case of an error
     */
    @Override
    public void setDefaultUniverse(UniverseSpec universeSpec) throws ProvisioningException {
        addUniverse(null, universeSpec);
    }

    @Override
    public GalleonProvisioningConfig getProvisioningConfig() throws ProvisioningException {
        Path provisioning = PathsUtils.getProvisioningXml(home);
        return buildProvisioningContext(provisioning).getConfig();
    }

}
