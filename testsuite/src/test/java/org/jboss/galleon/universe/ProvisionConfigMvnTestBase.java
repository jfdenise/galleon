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

package org.jboss.galleon.universe;

import java.nio.file.Path;

import org.jboss.galleon.ArtifactRepositoryManager;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.test.PmProvisionConfigTestBase;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenProducerBase;
import org.jboss.galleon.universe.maven.MavenProducerInstaller;
import org.jboss.galleon.universe.maven.MavenUniverseInstaller;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.universe.maven.repo.SimplisticMavenRepoManager;

/**
 *
 * @author Alexey Loubyansky
 */
public abstract class ProvisionConfigMvnTestBase extends PmProvisionConfigTestBase {

    @Override
    protected ArtifactRepositoryManager initRepoManager(Path repoHome) {
        return SimplisticMavenRepoManager.getInstance(repoHome);
    }

    protected MavenProducerBase createProducer(String producerName, String fpArtifactId) throws ProvisioningException {
        return new MavenProducerInstaller(producerName, (MavenRepoManager) repo,
                new MavenArtifact().setGroupId(TestConstants.GROUP_ID).setArtifactId(producerName).setVersion("1.0.0.Final"),
                TestConstants.GROUP_ID + "." + producerName, fpArtifactId)
                .addFrequencies("alpha", "beta")
                .addChannel("1.0", "[1.0.0,2.0.0)")
                .install();
    }

    protected MavenArtifact createUniverse(String universeName, MavenProducerBase... producers) throws ProvisioningException {
        final MavenArtifact universeArtifact = new MavenArtifact().setGroupId(TestConstants.GROUP_ID).setArtifactId(universeName).setVersion("1.0.0.Final");
        final MavenUniverseInstaller installer = new MavenUniverseInstaller((MavenRepoManager) repo, universeArtifact);
        for(MavenProducerBase p : producers) {
            installer.addProducer(p.getName(), p.getArtifact().setPath(null).setVersionRange("[1.0,2.0-alpha)"));
        }
        installer.install();
        return universeArtifact;
    }
}
