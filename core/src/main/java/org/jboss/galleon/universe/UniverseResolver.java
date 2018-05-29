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
import java.util.Map;

import org.jboss.galleon.FeaturePackLocation;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.repomanager.RepositoryArtifactResolver;
import org.jboss.galleon.util.CollectionUtils;
import org.jboss.galleon.util.StringUtils;

/**
 *
 * @author Alexey Loubyansky
 */
public class UniverseResolver {

    public static class Builder extends UniverseResolverBuilder<Builder> {

        private Builder(UniverseFactoryLoader ufl, UniverseResolver parent) throws ProvisioningException {
            this.ufl = ufl;
            setPrimaryUniverseResolver(parent);
        }

        public UniverseResolver build() {
            return new UniverseResolver(this);
        }
    }

    public static Builder builder() throws ProvisioningException {
        return new Builder(null, null);
    }

    public static Builder builder(UniverseResolver parent) throws ProvisioningException {
        return new Builder(parent.ufl, parent);
    }

    public static Builder builder(UniverseFactoryLoader ufl) throws ProvisioningException {
        return new Builder(ufl, null);
    }

    public static Builder builder(UniverseFactoryLoader ufl, UniverseResolver ur) throws ProvisioningException {
        return new Builder(ufl, ur);
    }

    private final UniverseResolver parent;
    private final Map<String, Universe<?>> universes;
    private final Universe<?> defaultUniverse;
    private final UniverseFactoryLoader ufl;

    UniverseResolver(UniverseResolverBuilder<?> builder) {
        this.parent = builder.primaryResolver;
        this.universes = CollectionUtils.unmodifiable(builder.universes);
        this.defaultUniverse = builder.defaultUniverse;
        this.ufl = builder.ufl;
    }

    /**
     * Checks whether the default universe can be resolved by this resolver.
     *
     * @return  true if the default universe can be resolved by this resolver
     */
    public boolean hasDefaultUniverse() {
        return defaultUniverse == null ? (parent == null ? false : parent.hasDefaultUniverse()) : true;
    }

    /**
     * Checks whether the universe name can be resolved by this resolver.
     * If the passed in universe name is null, the method will return true if
     * the default universe was configured and false otherwise.
     *
     * @param name  the universe name to check
     * @return  true if the universe name can be resolved by this resolver
     */
    public boolean hasUniverse(String name) {
        if(name == null ? defaultUniverse != null : universes.containsKey(name)) {
            return true;
        }
        return parent == null ? false : parent.hasUniverse(name);
    }

    /**
     * Returns universe object associated the name.
     * If the passed in name is null then if the default universe was configured, it is returned,
     * otherwise an exception is thrown.
     *
     * If universe resolver was configured with a parent resolver, the universe resolution is first
     * attempted locally and only if the universe was not resolved locally the parent invoked.
     * If the parent failed to resolve the universe, an exception is thrown.
     *
     * @param name  the name of the universe
     * @return  universe object associated with the universeName
     * @throws ProvisioningException  in universe object could not be resolved
     */
    public Universe<?> getUniverse(String name) throws ProvisioningException {
        if(name == null) {
            if(defaultUniverse == null) {
                if(parent != null) {
                    return parent.getUniverse(name);
                }
                final StringBuilder buf = new StringBuilder();
                buf.append("The default universe has not been configured, available universes include ");
                StringUtils.append(buf, universes.keySet());
                throw new ProvisioningException(buf.toString());
            }
            return defaultUniverse;
        }
        final Universe<?> universe = universes.get(name);
        if (universe == null) {
            if(parent != null) {
                return parent.getUniverse(name);
            }
            throw new ProvisioningException("Failed to resolve universe " + name);
        }
        return universe;
    }

    /**
     * Resolves feature-pack location to a path in a local repository
     *
     * @param fpl  feature-pack location
     * @return  local feature-pack path
     * @throws ProvisioningException  in case the feature-pack could not be resolved
     */
    public Path resolve(FeaturePackLocation fpl) throws ProvisioningException {
        return getUniverse(fpl.getUniverse()).getProducer(fpl.getProducer()).getChannel(fpl.getChannelName()).resolve(fpl);
    }

    /**
     * Returns repository artifact resolver for specific repository type.
     *
     * @param repositoryId  repository id
     * @return  artifact resolver
     * @throws ProvisioningException  in case artifact resolver was not configured for the repository type
     */
    public RepositoryArtifactResolver getArtifactResolver(String repositoryId) throws ProvisioningException {
        final RepositoryArtifactResolver ar = getArtifactResolverOrNull(repositoryId);
        if(ar == null) {
            throw new ProvisioningException("Repository artifact resolver " + repositoryId + " was not configured");
        }
        return ar;
    }

    protected RepositoryArtifactResolver getArtifactResolverOrNull(String repoId) {
        RepositoryArtifactResolver rar = ufl == null ? null : ufl.getArtifactResolverOrNull(repoId);
        if(rar != null) {
            return rar;
        }
        return parent == null ? null : parent.getArtifactResolverOrNull(repoId);
    }
}
