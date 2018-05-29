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
package org.jboss.galleon.config;

import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;

import org.jboss.galleon.Errors;
import org.jboss.galleon.FeaturePackLocation;
import org.jboss.galleon.ProvisioningDescriptionException;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.util.CollectionUtils;

/**
 *
 * @author Alexey Loubyansky
 */
public abstract class FeaturePackDepsConfigBuilder<B extends FeaturePackDepsConfigBuilder<B>> extends ConfigCustomizationsBuilder<B> {

    UniverseConfig defaultUniverse;
    Map<String, UniverseConfig> universeConfigs = Collections.emptyMap();
    Map<FeaturePackLocation.Channel, FeaturePackConfig> fpDeps = Collections.emptyMap();
    Map<String, FeaturePackConfig> fpDepsByOrigin = Collections.emptyMap();
    Map<FeaturePackLocation.Channel, String> channelToOrigin = Collections.emptyMap();

    public B addFeaturePackDep(FeaturePackConfig dependency) throws ProvisioningDescriptionException {
        return addFeaturePackDep(null, dependency);
    }

    @SuppressWarnings("unchecked")
    public B addFeaturePackDep(String origin, FeaturePackConfig dependency) throws ProvisioningDescriptionException {
        if(fpDeps.containsKey(dependency.getLocation().getChannel())) {
            throw new ProvisioningDescriptionException("Feature-pack already added " + dependency.getLocation().getChannel());
        }
        if(origin != null) {
            if(fpDepsByOrigin.containsKey(origin)){
                throw new ProvisioningDescriptionException(Errors.duplicateDependencyName(origin));
            }
            fpDepsByOrigin = CollectionUtils.put(fpDepsByOrigin, origin, dependency);
            channelToOrigin = CollectionUtils.put(channelToOrigin, dependency.getLocation().getChannel(), origin);
        }
        fpDeps = CollectionUtils.putLinked(fpDeps, dependency.getLocation().getChannel(), dependency);
        return (B) this;
    }

    @SuppressWarnings("unchecked")
    public B removeFeaturePackDep(FeaturePackLocation location) throws ProvisioningException {
        final FeaturePackLocation.Channel channel = location.getChannel();
        final FeaturePackConfig fpDep = fpDeps.get(channel);
        if(fpDep == null) {
            throw new ProvisioningException(Errors.unknownFeaturePack(location.getFPID()));
        }
        if(!fpDep.getLocation().equals(location)) {
            throw new ProvisioningException(Errors.unknownFeaturePack(location.getFPID()));
        }
        if(fpDeps.size() == 1) {
            fpDeps = Collections.emptyMap();
            fpDepsByOrigin = Collections.emptyMap();
            channelToOrigin = Collections.emptyMap();
            return (B) this;
        }
        fpDeps = CollectionUtils.remove(fpDeps, channel);
        if(!channelToOrigin.isEmpty()) {
            final String origin = channelToOrigin.get(channel);
            if(origin != null) {
                if(fpDepsByOrigin.size() == 1) {
                    fpDepsByOrigin = Collections.emptyMap();
                    channelToOrigin = Collections.emptyMap();
                } else {
                    fpDepsByOrigin.remove(origin);
                    channelToOrigin.remove(channel);
                }
            }
        }
        return (B) this;
    }

    public int getFeaturePackDepIndex(FeaturePackLocation location) throws ProvisioningException {
        final FeaturePackLocation.Channel channel = location.getChannel();
        final FeaturePackConfig fpDep = fpDeps.get(channel);
        if (fpDep == null) {
            throw new ProvisioningException(Errors.unknownFeaturePack(location.getFPID()));
        }
        if (!fpDep.getLocation().equals(location)) {
            throw new ProvisioningException(Errors.unknownFeaturePack(location.getFPID()));
        }
        int i = 0;
        for (FeaturePackLocation.Channel g : fpDeps.keySet()) {
            if (g.equals(channel)) {
                break;
            }
            i += 1;
        }
        return i;
    }

    @SuppressWarnings("unchecked")
    public B addFeaturePackDep(int index, FeaturePackConfig dependency) throws ProvisioningDescriptionException {
        if (index >= fpDeps.size()) {
            FeaturePackDepsConfigBuilder.this.addFeaturePackDep(dependency);
        } else {
            if (fpDeps.containsKey(dependency.getLocation().getChannel())) {
                throw new ProvisioningDescriptionException("Feature-pack already added " + dependency.getLocation().getChannel());
            }
            // reconstruct the linkedMap.
            Map<FeaturePackLocation.Channel, FeaturePackConfig> tmp = Collections.emptyMap();
            int i = 0;
            for (Entry<FeaturePackLocation.Channel, FeaturePackConfig> entry : fpDeps.entrySet()) {
                if (i == index) {
                    tmp = CollectionUtils.putLinked(tmp, dependency.getLocation().getChannel(), dependency);
                }
                tmp = CollectionUtils.putLinked(tmp, entry.getKey(), entry.getValue());
                i += 1;
            }
            fpDeps = tmp;
        }
        return (B) this;
    }

    @SuppressWarnings("unchecked")
    public B addUniverse(UniverseConfig universe) throws ProvisioningDescriptionException {
        if(universe.isDefault()) {
            if(defaultUniverse != null) {
                throw new ProvisioningDescriptionException("Failed to make " + universe + " the default universe, "
                        + defaultUniverse + " has already been configured as the default one");
            }
            defaultUniverse = universe;
            return (B) this;
        }
        universeConfigs = CollectionUtils.put(universeConfigs, universe.getName(), universe);
        return (B) this;
    }
}
