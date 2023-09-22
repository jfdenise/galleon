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
import java.io.FileWriter;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.stream.XMLStreamException;
import org.jboss.galleon.CoreVersion;
import org.jboss.galleon.ProvisioningDescriptionException;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.api.Configuration;
import org.jboss.galleon.api.GalleonLayer;
import org.jboss.galleon.api.GalleonProvisioningRuntime;
import org.jboss.galleon.config.ConfigModel;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.config.ParsedConfiguration;
import org.jboss.galleon.api.ProvisioningContext;
import org.jboss.galleon.api.config.ConfigId;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.layout.FeaturePackLayout;
import org.jboss.galleon.layout.ProvisioningLayout;
import org.jboss.galleon.spec.ConfigLayerDependency;
import org.jboss.galleon.spec.ConfigLayerSpec;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.FeaturePackLocation.FPID;
import org.jboss.galleon.universe.UniverseResolver;
import org.jboss.galleon.xml.ConfigXmlParser;
import org.jboss.galleon.xml.ProvisioningXmlParser;
import org.jboss.galleon.xml.ProvisioningXmlWriter;

public class ProvisioningContextImpl implements ProvisioningContext {

    private final ProvisioningManager manager;
    private final boolean noHome;
    private final URLClassLoader loader;

    ProvisioningContextImpl(URLClassLoader loader,
            boolean noHome,
            ProvisioningManager manager) {
        this.loader = loader;
        this.noHome = noHome;
        this.manager = manager;
    }

    @Override
    public FeaturePackLocation addLocal(Path path, boolean installInUniverse) throws ProvisioningException {
        return manager.getLayoutFactory().addLocal(path, installInUniverse);
    }

    @Override
    public void provision(GalleonProvisioningConfig config, Map<String, String> options) throws ProvisioningException {
        if (noHome) {
            throw new ProvisioningException("No installation set, can't provision.");
        }
        ProvisioningConfig c = ProvisioningConfig.toConfig(config);
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(loader);
        try {
            manager.provision(c, options);
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
        manager.provision(c);
    }

    @Override
    public String getCoreVersion() {
        return CoreVersion.getVersion();
    }

    @Override
    public void storeProvisioningConfig(GalleonProvisioningConfig config, Path file) throws XMLStreamException, IOException, ProvisioningDescriptionException {
        ProvisioningConfig c = ProvisioningConfig.toConfig(config);
        try (FileWriter writer = new FileWriter(file.toFile())) {
            ProvisioningXmlWriter.getInstance().write(c, writer);
        }
    }

    @Override
    public Map<FPID, Map<String, GalleonLayer>> getAllLayers(GalleonProvisioningConfig config) throws ProvisioningException, IOException {
        ProvisioningConfig c = ProvisioningConfig.toConfig(config);
        Map<FPID, Map<String, GalleonLayer>> layersMap = new LinkedHashMap<>();
        Set<String> autoInjected = new TreeSet<>();
        try (ProvisioningLayout<FeaturePackLayout> pmLayout = manager.getLayoutFactory().newConfigLayout(c)) {
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
    public GalleonProvisioningRuntime getProvisioningRuntime(GalleonProvisioningConfig config) throws ProvisioningException {
        ProvisioningConfig c = ProvisioningConfig.toConfig(config);
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(loader);
        try {
            return manager.getRuntime(c);
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
    }

    @Override
    public UniverseResolver getUniverseResolver() {
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

    @Override
    public GalleonProvisioningConfig parseProvisioningFile(Path provisioning) throws ProvisioningException {
        ProvisioningConfig c = ProvisioningXmlParser.parse(provisioning);
        return ProvisioningConfig.toConfig(c);
    }

    @Override
    public Configuration parseConfigurationFile(Path configuration) throws ProvisioningException {
        try (BufferedReader reader = Files.newBufferedReader(configuration)) {
            ConfigModel c = ConfigXmlParser.getInstance().parse(reader);
            ParsedConfiguration config = new ParsedConfiguration(c);
            return config;
        } catch (XMLStreamException | IOException ex) {
            throw new ProvisioningException("Couldn't load the customization configuration " + configuration, ex);
        }
    }

}
