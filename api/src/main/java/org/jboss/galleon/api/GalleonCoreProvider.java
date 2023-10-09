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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.api.config.GalleonFeaturePackConfig;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.core.builder.LocalFP;
import org.jboss.galleon.impl.ProvisioningUtil;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.UniverseResolver;
import org.jboss.galleon.universe.UniverseResolverBuilder;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.util.IoUtils;

/**
 *
 * @author jdenise
 */
public class GalleonCoreProvider extends UniverseResolverBuilder<GalleonCoreProvider> {

    private static class ClassLoaderUsage {

        int num = 1;
        URLClassLoader loader;
    }
    private static final Map<String, ClassLoaderUsage> classLoaders = new HashMap<>();
    private UniverseResolver resolver;
    private final Map<FeaturePackLocation.FPID, LocalFP> locals = new HashMap<>();

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

    public void setUniverseResolver(UniverseResolver resolver) {
        this.resolver = resolver;
    }

    private UniverseResolver getUniverseResolver() throws ProvisioningException {
        if (resolver == null) {
            resolver = buildUniverseResolver();
        }
        return resolver;
    }

    public ProvisioningBuilder newProvisioningBuilder() throws ProvisioningException {
        String coreVersion = APIVersion.getVersion();
        return new ProvisioningBuilder(getUniverseResolver(), locals, coreVersion);
    }

    public ProvisioningBuilder newProvisioningBuilder(Path provisioning) throws ProvisioningException {
        Path tmp = getTmpDirectory();
        try {
            String coreVersion = ProvisioningUtil.getCoreVersion(provisioning, getUniverseResolver(), tmp);
            checkArtifactResolver(coreVersion, getUniverseResolver());
            return new ProvisioningBuilder(getUniverseResolver(), locals, coreVersion);
        } finally {
            IoUtils.recursiveDelete(tmp);
        }
    }

    public ProvisioningBuilder newProvisioningBuilder(GalleonProvisioningConfig config) throws ProvisioningException {
        Path tmp = getTmpDirectory();
        try {
            String coreVersion = APIVersion.getVersion();
            for (GalleonFeaturePackConfig fp : config.getFeaturePackDeps()) {
                LocalFP local = locals.get(fp.getLocation().getFPID());
                Path resolvedFP;
                if (local == null) {
                    resolvedFP = getUniverseResolver().resolve(fp.getLocation());
                } else {
                    resolvedFP = local.getPath();
                }
                try {
                    coreVersion = ProvisioningUtil.getCoreVersion(resolvedFP, coreVersion, tmp, getUniverseResolver());
                } catch (Exception ex) {
                    throw new ProvisioningException(ex);
                }
            }
            checkArtifactResolver(coreVersion, getUniverseResolver());
            return new ProvisioningBuilder(getUniverseResolver(), locals, coreVersion);
        } finally {
            IoUtils.recursiveDelete(tmp);
        }
    }

    private static void checkArtifactResolver(String coreVersion, UniverseResolver universeResolver) throws ProvisioningException {
        if (!APIVersion.getVersion().equals(coreVersion)) {
            // Check that we will be able to resolve the core artifact
            try {
                getArtifactResolver(universeResolver);
            } catch (ProvisioningException ex) {
                throw new ProvisioningException("No maven artifact resolver specified in universe, "
                        + "the Galleon core library can't be resolved");
            }
        }
    }

    private static MavenRepoManager getArtifactResolver(UniverseResolver universeResolver) throws ProvisioningException {
        return (MavenRepoManager) universeResolver.getArtifactResolver(MavenRepoManager.REPOSITORY_ID);
    }

    private static Path getTmpDirectory() throws ProvisioningException {
        try {
            return Files.createTempDirectory("galleon-tmp");
        } catch (IOException ex) {
            throw new ProvisioningException(ex);
        }
    }

    static synchronized void releaseUsage(String version) throws ProvisioningException {
        ClassLoaderUsage usage = classLoaders.get(version);
        if (usage == null) {
            throw new ProvisioningException("Releasing usage of core " + version + " although no usage");
        }
        if (usage.num <= 0) {
            throw new ProvisioningException("Releasing usage of core " + version + " although all usages released");
        }
        usage.num -= 1;
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

    private static synchronized ClassLoaderUsage addDefaultCoreClassLoader() throws ProvisioningException {
        String apiVersion = APIVersion.getVersion();
        try {
            Path corePath = Files.createTempDirectory("galleon-core-default-base-dir");
            corePath.toFile().deleteOnExit();
            // Handle local core
            File defaultCore = corePath.resolve("galleon-core.jar").toFile();
            try (InputStream input = ProvisioningImpl.class.getClassLoader().getResourceAsStream("galleon-core-" + apiVersion + ".jar")) {
                try (OutputStream output = new FileOutputStream(defaultCore, false)) {
                    input.transferTo(output);
                }
            }
            defaultCore.deleteOnExit();
            File defaultCoreCaller = corePath.resolve("galleon-core-caller.jar").toFile();
            try (InputStream input = ProvisioningImpl.class.getClassLoader().getResourceAsStream("galleon-core-caller-" + apiVersion + ".jar")) {
                try (OutputStream output = new FileOutputStream(defaultCoreCaller, false)) {
                    input.transferTo(output);
                }
            }
            defaultCoreCaller.deleteOnExit();
            URL[] cp = new URL[2];
            ClassLoaderUsage usage = new ClassLoaderUsage();
            try {
                cp[0] = defaultCore.toURI().toURL();
                cp[1] = defaultCoreCaller.toURI().toURL();
                usage.loader = new URLClassLoader(cp, Thread.currentThread().getContextClassLoader());
            } catch (Exception ex) {
                throw new ProvisioningException(ex);
            }
            classLoaders.put(apiVersion, usage);
            return usage;
        } catch (IOException ex) {
            throw new ProvisioningException(ex);
        }
    }

    static synchronized URLClassLoader getCallerClassLoader(String version, UniverseResolver universeResolver) throws ProvisioningException {
        ClassLoaderUsage usage = classLoaders.get(version);
        if (usage == null) {
            //System.out.println("NEW USAGE OF " + version);
            if (APIVersion.getVersion().equals(version)) {
                usage = addDefaultCoreClassLoader();
            } else {
                MavenRepoManager repoManager = (MavenRepoManager) universeResolver.getArtifactResolver(MavenRepoManager.REPOSITORY_ID);
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
            }
        } else {
            //System.out.println("REUSE OF " + version);
            usage.num += 1;
        }
        return usage.loader;
    }
}
