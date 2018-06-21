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
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.config.UniverseConfig;
import org.jboss.galleon.util.ParsingUtils;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 *
 * @author jdenise@redhat.com
 */
public class UniverseConfigXml {

    public static final String UNIVERSES = "universes";
    static final String UNIVERSE = "universe";
    static final String NAME = "name";
    static final String FACTORY = "factory";
    static final String LOCATION = "location";

    public static void read(XMLExtendedStreamReader reader, UniverseConfiguration config)
            throws ProvisioningException, XMLStreamException, IOException {
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.END_ELEMENT: {
                    if (reader.getLocalName().equals(UNIVERSES)) {
                        return;
                    }
                    break;
                }
                case XMLStreamConstants.START_ELEMENT: {
                    switch (reader.getLocalName()) {
                        case UNIVERSE: {
                            UniverseConfig u = new UniverseConfig(reader.getAttributeValue(null, NAME),
                                    reader.getAttributeValue(null, FACTORY), reader.getAttributeValue(null, LOCATION));
                            config.addUniverse(u, false);
                            break;
                        }
                        default: {
                            throw ParsingUtils.unexpectedContent(reader);
                        }
                    }
                    break;
                }
                default: {
                    throw ParsingUtils.unexpectedContent(reader);
                }
            }
        }
    }
}
