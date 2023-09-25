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
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import javax.xml.stream.XMLStreamException;
import org.jboss.galleon.ProvisioningDescriptionException;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.universe.UniverseResolver;
import org.jboss.galleon.universe.FeaturePackLocation;

public interface ProvisioningContext extends AutoCloseable {

    public String getCoreVersion();

    public GalleonProvisioningConfig getConfig() throws ProvisioningDescriptionException;

    public void storeProvisioningConfig(Path target) throws XMLStreamException, IOException, ProvisioningDescriptionException;

    public Map<FeaturePackLocation.FPID, Map<String, GalleonLayer>> getAllLayers() throws ProvisioningException, IOException;

    public GalleonProvisioningRuntime getProvisioningRuntime() throws ProvisioningException;

    UniverseResolver getUniverseResolver();

    public default void provision() throws ProvisioningException {
        provision(Collections.emptyMap());
    }

    public void provision(Map<String, String> options) throws ProvisioningException;

    public GalleonProvisioningConfig parseProvisioningFile(Path provisioning) throws ProvisioningException;

    @Override
    public void close();
}
