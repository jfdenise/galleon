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
package org.jboss.galleon.caller;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Map.Entry;
import javax.xml.stream.XMLStreamException;
import org.jboss.galleon.MessageWriter;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.config.ConfigModel;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.progresstracking.ProgressTracker;
import org.jboss.galleon.repo.RepositoryArtifactResolver;
import org.jboss.galleon.tooling.api.Configuration;
import org.jboss.galleon.tooling.api.ConfigurationId;
import org.jboss.galleon.tooling.api.GalleonFeaturePack;
import org.jboss.galleon.tooling.api.GalleonLocalItem;
import org.jboss.galleon.tooling.api.ProvisioningContext;
import org.jboss.galleon.tooling.api.ProvisioningDescription;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.xml.ConfigXmlParser;
import org.jboss.galleon.xml.ProvisioningXmlParser;
import org.jboss.galleon.tooling.spi.ProvisioningContextBuilder;

public class ProvisioningContextBuilderImpl implements ProvisioningContextBuilder {

    @Override
    public ProvisioningContext buildProvisioningContext(URLClassLoader loader, Path home,
            Path provisioning,
            Map<String, String> options,
            MessageWriter msgWriter,
            boolean logTime,
            boolean recordState,
            RepositoryArtifactResolver artifactResolver,
            Map<String, ProgressTracker<?>> progressTrackers) throws ProvisioningException {
        boolean noHome = home == null;
        if (home == null) {
            try {
                home = Files.createTempDirectory("gallon-no-installation");
            } catch (IOException ex) {
                throw new ProvisioningException(ex);
            }
        }
        ProvisioningManager pm = ProvisioningManager.builder().addArtifactResolver(artifactResolver)
                .setInstallationHome(home)
                .setMessageWriter(msgWriter)
                .setLogTime(logTime)
                .setRecordState(recordState)
                .build();
        for (Entry<String, ProgressTracker<?>> entry : progressTrackers.entrySet()) {
            pm.getLayoutFactory().setProgressTracker(entry.getKey(), entry.getValue());
        }

        return new ProvisioningContextImpl(loader, noHome, pm, ProvisioningXmlParser.parse(provisioning),
                options);
    }

    @Override
    public ProvisioningContext buildProvisioningContext(URLClassLoader loader, Path home,
            ProvisioningDescription pConfig,
            MessageWriter msgWriter,
            boolean logTime,
            boolean recordState,
            RepositoryArtifactResolver artifactResolver,
            Map<String, ProgressTracker<?>> progressTrackers) throws ProvisioningException {
        boolean noHome = home == null;
        if (home == null) {
            try {
                home = Files.createTempDirectory("gallon-no-installation");
            } catch (IOException ex) {
                throw new ProvisioningException(ex);
            }
        }
        final ProvisioningConfig.Builder state = ProvisioningConfig.builder();
        ProvisioningManager pm = ProvisioningManager.builder().addArtifactResolver(artifactResolver)
                .setInstallationHome(home)
                .setMessageWriter(msgWriter)
                .setLogTime(logTime)
                .setRecordState(recordState)
                .build();
        for (Entry<String, ProgressTracker<?>> entry : progressTrackers.entrySet()) {
            pm.getLayoutFactory().setProgressTracker(entry.getKey(), entry.getValue());
        }
        for (GalleonFeaturePack fp : pConfig.getFeaturePacks()) {

            final FeaturePackLocation fpl;
            if (fp.getNormalizedPath() != null) {
                fpl = pm.getLayoutFactory().addLocal(fp.getNormalizedPath(), false);
            } else {
                if (fp.getGroupId() != null && fp.getArtifactId() != null) {
                    String coords = fp.getMavenCoords();
                    fpl = FeaturePackLocation.fromString(coords);
                } else {
                    fpl = FeaturePackLocation.fromString(fp.getLocation());
                }
            }

            final FeaturePackConfig.Builder fpConfig = fp.isTransitive() ? FeaturePackConfig.transitiveBuilder(fpl)
                    : FeaturePackConfig.builder(fpl);
            if (fp.isInheritConfigs() != null) {
                fpConfig.setInheritConfigs(fp.isInheritConfigs());
            }
            if (fp.isInheritPackages() != null) {
                fpConfig.setInheritPackages(fp.isInheritPackages());
            }

            if (!fp.getExcludedConfigs().isEmpty()) {
                for (ConfigurationId configId : fp.getExcludedConfigs()) {
                    if (configId.isModelOnly()) {
                        fpConfig.excludeConfigModel(configId.getId().getModel());
                    } else {
                        fpConfig.excludeDefaultConfig(configId.getId());
                    }
                }
            }
            if (!fp.getIncludedConfigs().isEmpty()) {
                for (ConfigurationId configId : fp.getIncludedConfigs()) {
                    if (configId.isModelOnly()) {
                        fpConfig.includeConfigModel(configId.getId().getModel());
                    } else {
                        fpConfig.includeDefaultConfig(configId.getId());
                    }
                }
            }

            if (!fp.getIncludedPackages().isEmpty()) {
                for (String includedPackage : fp.getIncludedPackages()) {
                    fpConfig.includePackage(includedPackage);
                }
            }
            if (!fp.getExcludedPackages().isEmpty()) {
                for (String excludedPackage : fp.getExcludedPackages()) {
                    fpConfig.excludePackage(excludedPackage);
                }
            }

            state.addFeaturePackDep(fpConfig.build());
        }

        for (Configuration config : pConfig.getConfigs()) {
            ConfigModel.Builder configBuilder = ConfigModel.
                    builder(config.getModel(), config.getName());
            for (String layer : config.getLayers()) {
                configBuilder.includeLayer(layer);
            }
            if (config.getExcludedLayers() != null) {
                for (String layer : config.getExcludedLayers()) {
                    configBuilder.excludeLayer(layer);
                }
            }
            for (Entry<String, String> entry : config.getProps().entrySet()) {
                configBuilder.setProperty(entry.getKey(), entry.getValue());
            }
            state.addConfig(configBuilder.build());
        }

        if (pConfig.getCustomConfig() != null && pConfig.getCustomConfig().toFile().exists()) {
            try (BufferedReader reader = Files.newBufferedReader(pConfig.getCustomConfig())) {
                state.addConfig(ConfigXmlParser.getInstance().parse(reader));
            } catch (XMLStreamException | IOException ex) {
                throw new IllegalArgumentException("Couldn't load the customization configuration " + pConfig.getCustomConfig(), ex);
            }
        }

        for (GalleonLocalItem localResolverItem : pConfig.getLocalItems()) {
            if (localResolverItem.getNormalizedPath() != null) {
                pm.getLayoutFactory().addLocal(localResolverItem.getNormalizedPath(),
                        localResolverItem.getInstallInUniverse());
            }
        }
        for(String transitive : pConfig.getTransitiveLocations()) {
            state.addTransitiveDep(FeaturePackLocation.fromString(transitive));
        }
        state.addOptions(pConfig.getOptions());
        return new ProvisioningContextImpl(loader, noHome, pm, state.build(), pConfig.getOptions());

    }
}
