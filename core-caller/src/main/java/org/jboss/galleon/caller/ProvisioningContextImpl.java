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

import java.io.FileWriter;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.stream.XMLStreamException;
import org.jboss.galleon.CoreVersion;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.config.ConfigId;
import org.jboss.galleon.config.ConfigModel;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.layout.FeaturePackLayout;
import org.jboss.galleon.layout.ProvisioningLayout;
import org.jboss.galleon.spec.ConfigLayerDependency;
import org.jboss.galleon.spec.ConfigLayerSpec;
import org.jboss.galleon.tooling.api.Configuration;
import org.jboss.galleon.tooling.api.ConfigurationId;
import org.jboss.galleon.tooling.api.GalleonFeaturePack;
import org.jboss.galleon.tooling.api.GalleonLayer;
import org.jboss.galleon.tooling.api.GalleonProvisioningRuntime;
import org.jboss.galleon.tooling.api.ProvisioningContext;
import org.jboss.galleon.tooling.api.ProvisioningDescription;
import org.jboss.galleon.universe.BaseUniverseResolver;
import org.jboss.galleon.universe.FeaturePackLocation.FPID;
import org.jboss.galleon.xml.ProvisioningXmlWriter;

public class ProvisioningContextImpl implements ProvisioningContext {

    private final ProvisioningManager manager;
    private final ProvisioningConfig config;
    private final Map<String, String> options;
    private final boolean noHome;
    private final URLClassLoader loader;
    ProvisioningContextImpl(URLClassLoader loader,
            boolean noHome,
            ProvisioningManager manager, ProvisioningConfig config, Map<String, String> options) {
        this.loader = loader;
        this.noHome = noHome;
        this.manager = manager;
        this.config = config;
        this.options = options;
    }

    @Override
    public void provision() throws ProvisioningException {
        if (noHome) {
            throw new ProvisioningException("No installation set, can't provision.");
        }
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(loader);
        try {
            manager.provision(config, options);
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
    }

    @Override
    public String getCoreVersion() {
        return CoreVersion.getVersion();
    }

    @Override
    public void storeProvisioningConfig(Path file) throws XMLStreamException, IOException {
        try (FileWriter writer = new FileWriter(file.toFile())) {
            ProvisioningXmlWriter.getInstance().write(config, writer);
        }
    }

    @Override
    public ProvisioningDescription getProvisioningDescription() throws ProvisioningException {
        ProvisioningDescription.Builder builder = ProvisioningDescription.builder();
        List<GalleonFeaturePack> fps = new ArrayList<>();
        for (FeaturePackConfig fpConfig : config.getFeaturePackDeps()) {
            GalleonFeaturePack fp = new GalleonFeaturePack();
            fp.setLocation(fpConfig.getLocation().toString());
            fp.setInheritPackages(fpConfig.getInheritPackages());
            fp.setInheritConfigs(fpConfig.getInheritConfigs());
            Set<ConfigurationId> excluded = new HashSet<>();
            for (ConfigId cid : fpConfig.getExcludedConfigs()) {
                ConfigurationId id = new ConfigurationId();
                id.setModel(cid.getModel());
                id.setName(cid.getName());
                excluded.add(id);
            }
            fp.setExcludedConfigs(excluded);
            Set<ConfigurationId> included = new HashSet<>();
            for (ConfigId cid : fpConfig.getIncludedConfigs()) {
                ConfigurationId id = new ConfigurationId();
                id.setModel(cid.getModel());
                id.setName(cid.getName());
                included.add(id);
            }
            fp.setIncludedConfigs(included);
            fp.setExcludedPackages(fpConfig.getExcludedPackages());
            fp.setIncludedPackages(fpConfig.getIncludedPackages());
            fp.setTransitive(fpConfig.isTransitive());
            fps.add(fp);
        }
        builder.setFeaturePacks(fps);
        List<Configuration> configs = new ArrayList<>();
        for (ConfigModel model : config.getDefinedConfigs()) {
            Configuration config = new Configuration();
            config.setModel(model.getModel());
            config.setName(model.getName());
            List<String> excluded = new ArrayList<>();
            excluded.addAll(model.getExcludedLayers());
            config.setExcludedLayers(excluded);
            List<String> included = new ArrayList<>();
            included.addAll(model.getIncludedLayers());
            config.setLayers(included);
            configs.add(config);
        }
        builder.setConfigs(configs);
        builder.setOptions(options);
        return builder.build();
    }

    @Override
    public Map<FPID, Map<String, GalleonLayer>> getAllLayers() throws ProvisioningException, IOException {
        Map<FPID, Map<String, GalleonLayer>> layersMap = new LinkedHashMap<>();
        Set<String> autoInjected = new TreeSet<>();
        try (ProvisioningLayout<FeaturePackLayout> pmLayout = manager.getLayoutFactory().newConfigLayout(config)) {
            for (FeaturePackLayout fp : pmLayout.getOrderedFeaturePacks()) {
                Map<String, GalleonLayer> layers = new HashMap<>();
                ConfigModel m = fp.loadModel("standalone");
                if (m != null) {
                    autoInjected.addAll(m.getIncludedLayers());
                }
                for (ConfigId layer : fp.loadLayers()) {
                    ConfigLayerSpec spec = fp.loadConfigLayerSpec(layer.getModel(), layer.getName());
                    Set<String> dependencies = new TreeSet<>();
                    for (ConfigLayerDependency dep : spec.getLayerDeps()) {
                        dependencies.add(dep.getName());
                    }
                    // Case where a layer is redefined in multiple FP. Add all deps.

                    GalleonLayer l = new GalleonLayer(layer.getName());
                    l.getDependencies().addAll(dependencies);
                    l.getProperties().putAll(spec.getProperties());
                    layers.put(layer.getName(), l);
                }

                for (GalleonLayer l : layers.values()) {
                    if (autoInjected.contains(l.getName())) {
                        l.setIsAutomaticInjection(true);
                    }
                }

                layersMap.put(fp.getFPID(), layers);
            }
        }

        return layersMap;
    }

    @Override
    public GalleonProvisioningRuntime getProvisioningRuntime() throws ProvisioningException {
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(loader);
        try {
             return manager.getRuntime(config);
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
    }

    @Override
    public BaseUniverseResolver getUniverseResolver() {
        return manager.getLayoutFactory().getUniverseResolver();
    }

    @Override
    public void close() {
        try {
            loader.close();
        } catch (IOException ex) {
            System.err.println("Error closing core classloader");
        }
        manager.close();
    }
}
