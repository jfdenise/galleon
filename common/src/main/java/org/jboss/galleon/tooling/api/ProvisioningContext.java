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
import java.nio.file.Path;
import java.util.Map;
import javax.xml.stream.XMLStreamException;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.universe.FeaturePackLocation;

public interface ProvisioningContext extends AutoCloseable {

    public void provision() throws ProvisioningException;

    public String getCoreVersion();

    public void storeProvisioningConfig(Path target) throws XMLStreamException, IOException;

    public Map<FeaturePackLocation.FPID, Map<String, GalleonLayer>> getAllLayers() throws ProvisioningException, IOException;

    public ProvisioningDescription getProvisioningDescription() throws ProvisioningException;

    @Override
    public void close();
}
