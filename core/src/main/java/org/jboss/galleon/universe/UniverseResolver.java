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
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.repomanager.RepositoryArtifactResolver;
import org.jboss.galleon.util.CollectionUtils;

/**
 *
 * @author Alexey Loubyansky
 */
public class UniverseResolver {

    public static class Builder extends UniverseResolverBuilder<Builder> {

        private Builder(UniverseFactoryLoader ufl) throws ProvisioningException {
            this.ufl = ufl;
        }

        public UniverseResolver build() {
            return new UniverseResolver(this);
        }
    }

    public static Builder builder() throws ProvisioningException {
        return new Builder(null);
    }

    public static Builder builder(UniverseFactoryLoader ufl) throws ProvisioningException {
        return new Builder(ufl);
    }

    private final UniverseFactoryLoader ufl;
    private Map<UniverseSpec, Universe<?>> resolvedUniverses = Collections.emptyMap();

    UniverseResolver(UniverseResolverBuilder<?> builder) {
        this.ufl = builder.getUfl();
    }

    /**
     * Returns the universe factory loader
     *
     * @return  universe factory loader
     */
    public UniverseFactoryLoader getFactoryLoader() {
        return ufl;
    }

    /**
     * Returns universe object for the source.
     *
     * @param universeSource  universe source
     * @return  universe object for the source
     * @throws ProvisioningException  in universe object could not be resolved
     */
    public Universe<?> getUniverse(UniverseSpec universeSource) throws ProvisioningException {
        Universe<?> resolved = resolvedUniverses.get(universeSource);
        if(resolved == null) {
            resolved = ufl.getUniverse(universeSource);
            resolvedUniverses = CollectionUtils.put(resolvedUniverses, universeSource, resolved);
        }
        return resolved;
    }

    /**
     * Resolves feature-pack location to a path in a local repository
     *
     * @param fps  feature-pack location
     * @return  local feature-pack path
     * @throws ProvisioningException  in case the feature-pack could not be resolved
     */
    public Path resolve(FeaturePackLocation fps) throws ProvisioningException {
        return getUniverse(fps.getUniverse()).getProducer(fps.getProducer()).getChannel(fps.getChannelName()).resolve(fps);
    }

    /**
     * Returns repository artifact resolver for specific repository type.
     *
     * @param repositoryId  repository id
     * @return  artifact resolver
     * @throws ProvisioningException  in case artifact resolver was not configured for the repository type
     */
    public RepositoryArtifactResolver getArtifactResolver(String repositoryId) throws ProvisioningException {
        final RepositoryArtifactResolver ar = ufl.getArtifactResolverOrNull(repositoryId);
        if(ar == null) {
            throw new ProvisioningException("Repository artifact resolver " + repositoryId + " was not configured");
        }
        return ar;
    }

    public Set<UniverseSpec> getUniverses() {
        return resolvedUniverses.keySet();
    }
}
