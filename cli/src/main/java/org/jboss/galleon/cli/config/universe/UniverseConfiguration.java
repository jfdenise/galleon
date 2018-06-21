/*
 * Copyright 2016-2018 Red Hat, Inc. and/or its affiliates
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
package org.jboss.galleon.cli.config.universe;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import javax.xml.stream.XMLStreamException;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.cli.config.Configuration;
import org.jboss.galleon.config.UniverseConfig;
import org.jboss.galleon.xml.util.FormattingXmlStreamWriter;

/**
 *
 * @author jdenise@redhat.com
 */
public class UniverseConfiguration {

    private final Set<UniverseConfig> universes = new HashSet<>();
    private final Configuration config;

    public UniverseConfiguration(Configuration config) {
        this.config = config;
    }

    public void addUniverse(UniverseConfig u) throws ProvisioningException, XMLStreamException, IOException {
        addUniverse(u, true);
    }

    void addUniverse(UniverseConfig u, boolean write) throws ProvisioningException, XMLStreamException, IOException {
        if (universes.contains(u)) {
            throw new ProvisioningException("Universe " + u
                    + " already exists");
        }
        universes.add(u);
        if (write) {
            config.needRewrite();
        }
    }

    public Set<UniverseConfig> getUniverses() {
        return Collections.unmodifiableSet(universes);
    }

    public void write(FormattingXmlStreamWriter writer) throws XMLStreamException {
        if (universes.isEmpty()) {
            return;
        }
        writer.writeStartElement(UniverseConfigXml.UNIVERSES);
        for (UniverseConfig u : universes) {
            writer.writeEmptyElement(UniverseConfigXml.UNIVERSE);
            if (u.getName() != null) {
                writer.writeAttribute(UniverseConfigXml.NAME, u.getName());
            }
            writer.writeAttribute(UniverseConfigXml.FACTORY, u.getSpec().getFactory());
            writer.writeAttribute(UniverseConfigXml.LOCATION, u.getSpec().getLocation());
        }
        writer.writeEndElement();
    }

    public void removeUniverse(String name) throws XMLStreamException, IOException {
        Iterator<UniverseConfig> it = universes.iterator();
        while (it.hasNext()) {
            UniverseConfig u = it.next();
            if (u.getName().equals(name)) {
                it.remove();
                break;
            }
        }
        config.needRewrite();
    }
}
