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
package org.jboss.galleon.bootstrap;

import org.jboss.galleon.tooling.ProvisioningDescription;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jboss.galleon.Constants;

import org.jboss.galleon.util.PathsUtils;
import org.jboss.galleon.DefaultMessageWriter;
import org.jboss.galleon.MessageWriter;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.Version;
import org.jboss.galleon.impl.FeaturePackLightXmlParser;
import org.jboss.galleon.impl.ProvisioningLightXmlParser;
import org.jboss.galleon.progresstracking.DefaultProgressTracker;
import org.jboss.galleon.progresstracking.ProgressCallback;
import org.jboss.galleon.progresstracking.ProgressTracker;
import org.jboss.galleon.repo.RepositoryArtifactResolver;
import org.jboss.galleon.tooling.GalleonFeaturePack;
import org.jboss.galleon.universe.BaseUniverseResolver;
import org.jboss.galleon.universe.BaseUniverseResolverBuilder;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.FeaturePackLocation.FPID;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.util.IoUtils;
import org.jboss.galleon.util.ZipUtils;

/**
 *
 * @author Alexey Loubyansky
 */
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

    private Provisioning(Builder builder) throws ProvisioningException {
        PathsUtils.assertInstallationDir(builder.installationHome);
        this.home = builder.installationHome;
        this.log = builder.messageWriter == null ? DefaultMessageWriter.getDefaultInstance() : builder.messageWriter;

        universeResolver = builder.getUniverseResolver();
        this.logTime = builder.logTime;
        this.recordState = builder.recordState;
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

    public void provision(ProvisioningDescription config) throws ProvisioningException {
        MavenRepoManager repoManager = (MavenRepoManager) universeResolver.getArtifactResolver(MavenRepoManager.REPOSITORY_ID);
        String coreVersion = Version.getVersion();
        Path tmp = null;
        try {
            tmp = Files.createTempDirectory("galleon-tmp");
            if (config.getProvisioningFile() != null) {
                List<FPID> featurePacks = ProvisioningLightXmlParser.parse(config.getProvisioningFile());
                coreVersion = getCoreVersion(featurePacks, Version.getVersion(), tmp);
            } else {
                for (GalleonFeaturePack fp : config.getFeaturePacks()) {
                    if (fp.getNormalizedPath() != null) {
                        coreVersion = getCoreVersion(fp.getNormalizedPath(), tmp, coreVersion);
                    } else if (fp.getGroupId() != null && fp.getArtifactId() != null) {
                        String coords = getMavenCoords(fp);
                        FeaturePackLocation fpl = FeaturePackLocation.fromString(coords);
                        Path resolvedFP = universeResolver.resolve(fpl);
                        coreVersion = getCoreVersion(resolvedFP, tmp, coreVersion);
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
                        coreVersion = getCoreVersion(resolvedFP, tmp, coreVersion);
                    }
                }
            }
            System.out.println("REQUIRED CORE VERSION is " + coreVersion);

            Class<?> callerClass = getCallerClass(coreVersion, repoManager);

            Method m = callerClass.getDeclaredMethod("provision",
                    Path.class,
                    ProvisioningDescription.class,
                    MessageWriter.class,
                    Boolean.TYPE,
                    Boolean.TYPE,
                    RepositoryArtifactResolver.class,
                    Map.class);
            Thread.currentThread().setContextClassLoader(callerClass.getClassLoader());
            m.invoke(null, home, config,
                    log, logTime, recordState, repoManager, progressTrackers);
        } catch (Exception ex) {
            throw new ProvisioningException(ex);
        } finally {
            IoUtils.recursiveDelete(tmp);
        }
    }

    private String getCoreVersion(Path resolvedFP, Path tmp, String currentVersion) throws Exception {
        Path spec = getFeaturePackSpec(null, resolvedFP, tmp);
        String fpVersion = FeaturePackLightXmlParser.parseVersion(spec);
        System.out.println("Found a version in FP " + resolvedFP + " version is " + fpVersion);
        if (fpVersion != null) {
            if (fpVersion.compareTo(currentVersion) > 0) {
                currentVersion = fpVersion;
            }
        }
        List<FPID> deps = FeaturePackLightXmlParser.parseDependencies(spec);
        return getCoreVersion(deps, currentVersion, tmp);
    }

    private static String getMavenCoords(GalleonFeaturePack fp) {
        StringBuilder builder = new StringBuilder();
        builder.append(fp.getGroupId()).append(":").append(fp.getArtifactId());
        String type = fp.getExtension() == null ? fp.getType() : fp.getExtension();
        if (fp.getClassifier() != null || type != null) {
            builder.append(":").append(fp.getClassifier() == null ? "" : fp.getClassifier()).append(":")
                    .append(type == null ? "" : type);
        }
        if (fp.getVersion() != null) {
            builder.append(":").append(fp.getVersion());
        }
        return builder.toString();
    }

    private Class<?> getCallerClass(String version, MavenRepoManager repoManager) throws ProvisioningException {
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
        callerArtifact.setVersion(Version.getVersion());
        callerArtifact.setExtension("jar");
        repoManager.resolve(callerArtifact);

        URL[] cp = new URL[2];
        try {
            cp[0] = coreArtifact.getPath().toFile().toURI().toURL();
            cp[1] = callerArtifact.getPath().toFile().toURI().toURL();
            URLClassLoader cl = new URLClassLoader(cp, Thread.currentThread().getContextClassLoader());
            return Class.forName("org.jboss.galleon.caller.Provision", true, cl);
        } catch (Exception ex) {
            throw new ProvisioningException(ex);
        }
    }

    private String getCoreVersion(List<FPID> featurePacks, String currentMax, Path tmp) throws ProvisioningException {
        try {
            String version = currentMax;
            for (FPID fpid : featurePacks) {
                Path resolvedFP = universeResolver.resolve(fpid.getLocation());
                Path spec = getFeaturePackSpec(fpid, resolvedFP, tmp);
                String fpVersion = FeaturePackLightXmlParser.parseVersion(spec);
                System.out.println("Found a version in FP " + fpid + " version is " + fpVersion);
                if (fpVersion != null) {
                    if (fpVersion.compareTo(version) > 0) {
                        version = fpVersion;
                    }
                }
                List<FPID> deps = FeaturePackLightXmlParser.parseDependencies(spec);
                version = getCoreVersion(deps, version, tmp);
            }
            return version;
        } catch (Exception ex) {
            throw new ProvisioningException(ex);
        }
    }

    private Path getFeaturePackSpec(FPID fpid, Path resolvedFP, Path tmp) throws Exception {
        Path fpDir = tmp.resolve(resolvedFP.getFileName());
        ZipUtils.unzip(resolvedFP, fpDir);
        return fpDir.resolve(Constants.FEATURE_PACK_XML);
    }

    @Override
    public void close() {
    }

}
