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

import java.util.Collections;
import java.util.Map;

import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.repomanager.RepositoryArtifactResolver;
import org.jboss.galleon.util.CollectionUtils;

/**
 *
 * @author Alexey Loubyansky
 */
public abstract class UniverseResolverBuilder<T extends UniverseResolverBuilder<?>> {

    protected UniverseResolver primaryResolver;
    protected UniverseFactoryLoader ufl;
    protected Map<String, Universe<?>> universes = Collections.emptyMap();
    protected Universe<?> defaultUniverse;

    @SuppressWarnings("unchecked")
    public T setPrimaryUniverseResolver(UniverseResolver universeResolver) throws ProvisioningException {
        this.primaryResolver = universeResolver;
        return (T) this;
    }

    public T addArtifactResolver(RepositoryArtifactResolver artifactResolver) throws ProvisioningException {
        return addArtifactResolver(artifactResolver.getRepositoryId(), artifactResolver);
    }

    @SuppressWarnings("unchecked")
    public T addArtifactResolver(String repoId, RepositoryArtifactResolver artifactResolver) throws ProvisioningException {
        getUfl().addArtifactResolver(repoId, artifactResolver);
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T addDefaultUniverse(String factory, String location) throws ProvisioningException {
        setDefaultUniverse(getUfl().getUniverse(factory, location));
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T addDefaultUniverse(String factory, String location, RepositoryArtifactResolver locationResolver) throws ProvisioningException {
        setDefaultUniverse(getUfl().getUniverse(factory, location, locationResolver));
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T addDefaultUniverse(String factory, String location, String repoId) throws ProvisioningException {
        setDefaultUniverse(getUfl().getUniverse(factory, location, repoId));
        return (T) this;
    }

    public T addUniverse(String name, String factory, String location) throws ProvisioningException {
        return addUniverse(name, getUfl().getUniverse(factory, location));
    }

    public T addUniverse(String name, String factory, String location, RepositoryArtifactResolver locationResolver) throws ProvisioningException {
        return addUniverse(name, getUfl().getUniverse(factory, location, locationResolver));
    }

    public T addUniverse(String name, String factory, String location, String repositoryId) throws ProvisioningException {
        return addUniverse(name, getUfl().getUniverse(factory, location, repositoryId));
    }

    @SuppressWarnings("unchecked")
    public T addUniverse(String name, Universe<?> universe) throws ProvisioningException {
        universes = CollectionUtils.put(universes, name, universe);
        return (T) this;
    }

    public boolean hasUniverse(String name) {
        if(name == null ? defaultUniverse != null : universes.containsKey(name)) {
            return true;
        }
        return primaryResolver == null ? false : primaryResolver.hasUniverse(name);
    }

    protected boolean canBuildUniverseResolver() {
        return ufl != null || defaultUniverse != null || !universes.isEmpty() || primaryResolver != null;
    }

    protected UniverseResolver buildUniverseResolver() throws ProvisioningException {
        if(ufl != null || defaultUniverse != null || !universes.isEmpty()) {
            return new UniverseResolver(this);
        }
        if(primaryResolver == null) {
            throw new ProvisioningException("Universe resolver has not been initialized");
        }
        return primaryResolver;
    }

    private void setDefaultUniverse(Universe<?> universe) throws ProvisioningException {
        if(defaultUniverse != null) {
            throw new ProvisioningException("The default universe has already been initialized");
        }
        defaultUniverse = universe;
    }

    private UniverseFactoryLoader getUfl() {
        if(ufl == null) {
            ufl = UniverseFactoryLoader.getInstance();
        }
        return ufl;
    }
}
