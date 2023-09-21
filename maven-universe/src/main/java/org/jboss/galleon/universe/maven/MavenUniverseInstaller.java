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
package org.jboss.galleon.universe.maven;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamException;
import org.jboss.galleon.DefaultMessageWriter;
import org.jboss.galleon.MessageWriter;

import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.universe.maven.xml.MavenProducerSpecXmlWriter;
import org.jboss.galleon.util.IoUtils;
import org.jboss.galleon.util.ZipUtils;

import static org.jboss.galleon.universe.maven.MavenUniverseConstants.*;

/**
 *
 * @author Alexey Loubyansky
 */
public class MavenUniverseInstaller extends MavenUniverseBase {

    private Map<String, MavenProducer> producers = new HashMap<>();
    private boolean installed;

    public MavenUniverseInstaller(MavenRepoManager repoManager, MavenArtifact artifact) {
        super(repoManager, artifact, new DefaultMessageWriter());
    }

    public MavenUniverseInstaller(MavenRepoManager repoManager, MavenArtifact artifact, MessageWriter messageWriter) {
        super(repoManager, artifact, messageWriter);
    }

    public MavenUniverseInstaller extendUniverse(MavenArtifact extendArtifact) throws MavenUniverseException {
        final MavenUniverse otherUniverse = new MavenUniverse(repo, extendArtifact, messageWriter);
        for(MavenProducer producer : otherUniverse.getProducers()) {
            addProducer(producer);
        }
        return this;
    }

    public MavenUniverseInstaller addProducer(String producer, String groupId, String artifactId, String versionRange) throws MavenUniverseException {
        return addProducer(producer, new MavenArtifact().setGroupId(groupId).setArtifactId(artifactId).setVersionRange(versionRange));
    }

    public MavenUniverseInstaller addProducer(String producer, MavenArtifact artifact) throws MavenUniverseException {
        if(artifact.getVersionRange() == null) {
            MavenErrors.missingVersionRange(artifact);
        }
        return addProducer(new MavenProducer(producer, repo, artifact, messageWriter));
    }

    public MavenUniverseInstaller addProducer(MavenProducer producer) throws MavenUniverseException {
        producers.put(producer.getName(), producer);
        return this;
    }

    public MavenUniverseInstaller removeProducer(String producerName) {
        if(!producers.isEmpty()) {
            producers.remove(producerName);
        }
        return this;
    }

    public void install() throws MavenUniverseException {
        if(installed) {
            throw new MavenUniverseException("The universe has already been installed");
        }
        Path tmpDir = null;
        try {
            tmpDir = Files.createTempDirectory("gln-mvn-universe");
            final Path zipRoot = tmpDir.resolve("root");
            final Path locations = getProducerLocations(zipRoot);
            Files.createDirectories(locations);
            for(MavenProducerBase producer : producers.values()) {
                final Path producerDir = locations.resolve(producer.getName());
                Files.createDirectory(producerDir);
                final Path producerXml = producerDir.resolve(MAVEN_PRODUCER_XML);
                MavenProducerSpecXmlWriter.getInstance().write(producer, producerXml);
            }
            final Path artifactFile = tmpDir.resolve(artifact.getArtifactFileName());
            Files.createDirectories(artifactFile.getParent());
            ZipUtils.zip(zipRoot, artifactFile);
            repo.install(artifact, artifactFile);
        } catch (IOException | XMLStreamException e) {
            throw new MavenUniverseException("Failed to create Maven universe artifact", e);
        } finally {
            if(tmpDir != null) {
                IoUtils.recursiveDelete(tmpDir);
            }
        }
        installed = true;
    }

    @Override
    public boolean hasProducer(String producerName) throws MavenUniverseException {
        return producers.containsKey(producerName);
    }

    @Override
    public MavenProducer getProducer(String producerName) throws MavenUniverseException {
        final MavenProducer producer = producers.get(producerName);
        if(producer == null) {
            throw MavenErrors.producerNotFound(producerName);
        }
        return producer;
    }

    @Override
    public Collection<MavenProducer> getProducers() throws MavenUniverseException {
        return producers.values();
    }

}
