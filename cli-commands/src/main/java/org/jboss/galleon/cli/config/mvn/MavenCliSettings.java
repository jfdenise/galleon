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
package org.jboss.galleon.cli.config.mvn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.eclipse.aether.RepositoryListener;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.galleon.cli.Util;

/**
 *
 * @author jdenise@redhat.com
 */
class MavenCliSettings implements MavenSettings {

    private final List<RemoteRepository> repositories;
    private final RepositorySystemSession session;

    MavenCliSettings(MavenConfig config, RepositorySystem repoSystem, RepositoryListener listener) throws ArtifactException {
        repositories = Collections.unmodifiableList(buildRepositories(config));
        MavenProxySelector proxySelector = null;
        session = Util.newRepositorySession(repoSystem, config.getLocalRepository(),
                listener, proxySelector, config.isOffline());
    }

    @Override
    public List<RemoteRepository> getRepositories() {
        return repositories;
    }

    @Override
    public RepositorySystemSession getSession() {
        return session;
    }

    private List<RemoteRepository> buildRepositories(MavenConfig config) throws ArtifactException {
        List<RemoteRepository> repos = new ArrayList<>();
        for (MavenRemoteRepository repo : config.getRemoteRepositories()) {
            repos.add(config.buildRemoteRepository(repo, null, null));
        }
        return repos;
    }

}
