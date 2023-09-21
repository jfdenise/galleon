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
package org.jboss.galleon.xml;

import java.io.Reader;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.jboss.galleon.MessageWriter;

import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLMapper;

/**
 *
 * @author Alexey Loubyansky
 */
public class XmlBaseParsers {

    private static XmlBaseParsers INSTANCE;

    public static XmlBaseParsers getInstance(MessageWriter log) {
        if (INSTANCE == null) {
            INSTANCE = new XmlBaseParsers(log);
        }
        return INSTANCE;
    }

    public static void parse(final Reader reader, Object builder, MessageWriter log) throws XMLStreamException {
        getInstance(log).doParse(reader, builder);
    }
    private static final XMLInputFactory inputFactory;
    static {
        final XMLInputFactory tmpIF = XMLInputFactory.newInstance();
        setIfSupported(tmpIF, XMLInputFactory.IS_VALIDATING, Boolean.FALSE);
        setIfSupported(tmpIF, XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        inputFactory = tmpIF;
    }

    private static void setIfSupported(final XMLInputFactory inputFactory, final String property, final Object value) {
        if (inputFactory.isPropertySupported(property)) {
            inputFactory.setProperty(property, value);
        }
    }

    public static XMLStreamReader createXMLStreamReader(Reader reader) throws XMLStreamException {
        return inputFactory.createXMLStreamReader(reader);
    }

    private final XMLMapper mapper;
    private final MessageWriter log;

    protected XmlBaseParsers(MessageWriter log) {
        mapper = XMLMapper.Factory.create();
        this.log = log;
    }

    public void plugin(QName root, XMLElementReader<?> reader) {
        mapper.registerRootElement(root, reader);
    }

    public void doParse(final Reader reader, Object builder) throws XMLStreamException {
        mapper.parseDocument(builder, inputFactory.createXMLStreamReader(reader));
    }
}
