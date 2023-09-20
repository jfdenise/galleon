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
package org.jboss.galleon.tooling.api;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jboss.galleon.Constants;

import org.jboss.galleon.DefaultMessageWriter;
import org.jboss.galleon.MessageWriter;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.impl.FeaturePackLightXmlParser;
import org.jboss.galleon.impl.ProvisioningLightXmlParser;
import org.jboss.galleon.progresstracking.DefaultProgressTracker;
import org.jboss.galleon.progresstracking.ProgressCallback;
import org.jboss.galleon.progresstracking.ProgressTracker;
import org.jboss.galleon.universe.BaseUniverseResolver;
import org.jboss.galleon.universe.BaseUniverseResolverBuilder;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.FeaturePackLocation.FPID;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.util.IoUtils;
import org.jboss.galleon.util.ZipUtils;
import org.jboss.galleon.tooling.spi.ProvisioningContextBuilder;

public class Provisioning implements AutoCloseable {

    public static class Builder extends BaseUniverseResolverBuilder<Builder> {

        private Path installationHome;
        private MessageWriter messageWriter;
        private BaseUniverseResolver resolver;
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

        protected BaseUniverseResolver getUniverseResolver() throws ProvisioningException {
            return resolver == null ? buildUniverseResolver() : resolver;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final Path home;
    private final MessageWriter log;
    private boolean logTime;

    private final BaseUniverseResolver universeResolver;
    private boolean recordState;
    private final Map<String, ProgressTracker<?>> progressTrackers = new HashMap<>();

    private final Path tmp;

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

    public ProvisioningContext buildProvisioningContext(Path provisioning, Map<String, String> options) throws ProvisioningException {
        MavenRepoManager repoManager = (MavenRepoManager) universeResolver.getArtifactResolver(MavenRepoManager.REPOSITORY_ID);
        try {
            List<FPID> featurePacks = ProvisioningLightXmlParser.parse(provisioning);
            String coreVersion = getCoreVersion(featurePacks, APIVersion.getVersion());
            //System.out.println("REQUIRED CORE VERSION is " + coreVersion);

            URLClassLoader loader = getCallerClassLoader(coreVersion, repoManager);
            Class<?> callerClass = getCallerClass(loader);


            try {
                ProvisioningContextBuilder provisioner = (ProvisioningContextBuilder) callerClass.getConstructor().newInstance();
                ProvisioningContext ctx = provisioner.buildProvisioningContext(loader, home, provisioning, options, log, logTime, recordState, repoManager, progressTrackers);
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

    public ProvisioningContext buildProvisioningContext(ProvisioningDescription config) throws ProvisioningException {
        MavenRepoManager repoManager = (MavenRepoManager) universeResolver.getArtifactResolver(MavenRepoManager.REPOSITORY_ID);
        String coreVersion = APIVersion.getVersion();
        try {
            for (GalleonFeaturePack fp : config.getFeaturePacks()) {
                if (fp.getNormalizedPath() != null) {
                    coreVersion = getCoreVersion(fp.getNormalizedPath(), coreVersion);
                } else if (fp.getGroupId() != null && fp.getArtifactId() != null) {
                    String coords = getMavenCoords(fp);
                    FeaturePackLocation fpl = FeaturePackLocation.fromString(coords);
                    Path resolvedFP = universeResolver.resolve(fpl);
                    coreVersion = getCoreVersion(resolvedFP, coreVersion);
                } else {
                    // Special case for G:A that conflicts with producer:channel that we can't have in the plugin.
                    String location = fp.getLocation();
                    if (!FeaturePackLocation.fromString(location).hasUniverse()) {
                        long numSeparators = location.chars().filter(ch -> ch == ':').count();
                        if (numSeparators <= 1) {
                            location += ":";
                        }
                    }
                    FeaturePackLocation fpl = FeaturePackLocation.fromString(location);
                    Path resolvedFP = universeResolver.resolve(fpl);
                    coreVersion = getCoreVersion(resolvedFP, coreVersion);
                }
            }
            //System.out.println("REQUIRED CORE VERSION is " + coreVersion);
            URLClassLoader loader = getCallerClassLoader(coreVersion, repoManager);
            Class<?> callerClass = getCallerClass(loader);

            try {
                ProvisioningContextBuilder provisioner = (ProvisioningContextBuilder) callerClass.getConstructor().newInstance();
                ProvisioningContext ctx = provisioner.buildProvisioningContext(loader, home, config, log, logTime, recordState, repoManager, progressTrackers);
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

    public static boolean isFeaturePack(Path path) {
        Path tmp = null;
        try {
            tmp = Files.createTempDirectory("galleon-tmp");
            Path spec = getFeaturePackSpec(path, tmp);
            return Files.exists(spec);
        } catch (Exception ex) {
            return false;
        } finally {
            IoUtils.recursiveDelete(tmp);
        }
    }

    private String getCoreVersion(Path resolvedFP, String currentVersion) throws Exception {
        Path spec = getFeaturePackSpec(resolvedFP, tmp);
        String fpVersion = FeaturePackLightXmlParser.parseVersion(spec);
        //System.out.println("Found a version in FP " + resolvedFP + " version is " + fpVersion);
        if (fpVersion != null) {
            if (fpVersion.compareTo(currentVersion) > 0) {
                currentVersion = fpVersion;
            }
        }
        FeaturePackDependencies deps = FeaturePackLightXmlParser.parseDependencies(spec);
        return getCoreVersion(deps.getDependencies(), currentVersion);
    }

    private static String getMavenCoords(GalleonFeaturePack fp) {
        return fp.getMavenCoords();
    }

    private Class<?> getCallerClass(URLClassLoader loader) throws ProvisioningException {
        try {
            return Class.forName("org.jboss.galleon.caller.ProvisioningContextBuilderImpl", true, loader);
        } catch (Exception ex) {
            throw new ProvisioningException(ex);
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

    private String getCoreVersion(List<FPID> featurePacks, String currentMax) throws ProvisioningException {
        try {
            String version = currentMax;
            for (FPID fpid : featurePacks) {
                Path resolvedFP = universeResolver.resolve(fpid.getLocation());
                Path spec = getFeaturePackSpec(resolvedFP, tmp);
                String fpVersion = FeaturePackLightXmlParser.parseVersion(spec);
                //System.out.println("Found a version in FP " + fpid + " version is " + fpVersion);
                if (fpVersion != null) {
                    if (fpVersion.compareTo(version) > 0) {
                        version = fpVersion;
                    }
                }
                FeaturePackDependencies deps = FeaturePackLightXmlParser.parseDependencies(spec);
                version = getCoreVersion(deps.getDependencies(), version);
            }
            return version;
        } catch (Exception ex) {
            throw new ProvisioningException(ex);
        }
    }

    public FeaturePackDependencies getFeaturePackDependencies(Path fp) throws ProvisioningException {
        try {
            Path spec = getFeaturePackSpec(fp, tmp);
            return FeaturePackLightXmlParser.parseDependencies(spec);
        } catch (Exception ex) {
            throw new ProvisioningException(ex);
        }
    }

    private static Path getFeaturePackSpec(Path resolvedFP, Path tmp) throws Exception {
        Path fpDir = tmp.resolve(resolvedFP.getFileName());
        Files.createDirectories(fpDir);
        Path target = fpDir.resolve("fp-spec.xml");
        if (!Files.exists(target)) {
            try (FileSystem fs = ZipUtils.newFileSystem(resolvedFP)) {
                Path spec = fs.getPath(Constants.FEATURE_PACK_XML);
                ZipUtils.copyFromZip(spec, target);
            }
        }
        return target;
    }

    @Override
    public void close() {
        IoUtils.recursiveDelete(tmp);
    }

}
