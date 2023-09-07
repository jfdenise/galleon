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
package org.jboss.galleon.spec;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.jboss.galleon.ProvisioningDescriptionException;
import org.jboss.galleon.config.FeaturePackDepsConfig;
import org.jboss.galleon.config.FeaturePackDepsConfigBuilder;
import org.jboss.galleon.universe.FeaturePackLocation.FPID;
import org.jboss.galleon.universe.UniverseSpec;
import org.jboss.galleon.util.CollectionUtils;
import org.jboss.galleon.util.StringUtils;

/**
 * This class describes the feature-pack as it is available in the repository.
 *
 * @author Alexey Loubyansky
 */
public class FeaturePackSpec extends FeaturePackDepsConfig {

    public static class Builder extends FeaturePackDepsConfigBuilder<Builder> {

        private FPID fpid;
        private Set<String> defPackages = Collections.emptySet();
        private FPID patchFor;
        private Map<String, FeaturePackPlugin> plugins = Collections.emptyMap();
        private Set<String> systemPaths = Collections.emptySet();
        private String galleonMinVersion;

        protected Builder() {
        }

        public Builder setFPID(FPID fpid) {
            this.fpid = fpid;
            return this;
        }

        public Builder setGalleonMinVersion(String version) {
            this.galleonMinVersion = version;
            return this;
        }

        public String getGalleonMinVersion() {
            return galleonMinVersion;
        }

        public FPID getFPID() {
            return fpid;
        }

        public Builder setPatchFor(FPID patchFor) {
            if(patchFor == null) {
                this.patchFor = null;
                return this;
            }
            if(patchFor.getBuild() == null) {
                throw new IllegalArgumentException("FPID is missing build number");
            }
            this.patchFor = patchFor;
            return this;
        }

        @Override
        public boolean hasDefaultUniverse() {
            return true;
        }

        @Override
        public UniverseSpec getDefaultUniverse() {
            return this.defaultUniverse == null ? fpid.getLocation().getUniverse() : this.defaultUniverse;
        }

        public Builder addDefaultPackage(String packageName) {
            assert packageName != null : "packageName is null";
            defPackages = CollectionUtils.addLinked(defPackages, packageName);
            return this;
        }

        public Builder addDefaultPackages(Set<String> packageNames) {
            assert packageNames != null : "packageNames is null";
            if(!packageNames.isEmpty()) {
                defPackages = CollectionUtils.addAllLinked(defPackages, packageNames);
            }
            return this;
        }

        public Builder addPlugin(FeaturePackPlugin plugin) {
            plugins = CollectionUtils.putLinked(plugins, plugin.getId(), plugin);
            return this;
        }

        public FeaturePackSpec build() throws ProvisioningDescriptionException {
            try {
                return new FeaturePackSpec(this);
            } catch(ProvisioningDescriptionException e) {
                throw new ProvisioningDescriptionException("Failed to build feature-pack spec for " + fpid, e);
            }
        }

        public Builder addSystemPaths(String systemPath) {
            systemPaths = CollectionUtils.add(systemPaths, systemPath);
            return this;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(FPID fpid) {
        return new Builder().setFPID(fpid);
    }

    private final FPID fpid;
    private final Set<String> defPackages;
    private final Map<String, FeaturePackPlugin> plugins;
    private final FPID patchFor;
    private final Set<String> systemPaths;
    private final String galleonMinVersion;

    protected FeaturePackSpec(Builder builder) throws ProvisioningDescriptionException {
        super(builder);
        this.fpid = builder.fpid;
        this.defPackages = CollectionUtils.unmodifiable(builder.defPackages);
        this.plugins = CollectionUtils.unmodifiable(builder.plugins);
        this.patchFor = builder.patchFor;
        this.systemPaths = CollectionUtils.unmodifiable(builder.systemPaths);
        this.galleonMinVersion = builder.galleonMinVersion;
    }

    public String getGalleonMinVersion() {
        return galleonMinVersion;
    }

    public FPID getFPID() {
        return fpid;
    }

    public boolean isPatch() {
        return patchFor != null;
    }

    public FPID getPatchFor() {
        return patchFor;
    }

    public boolean hasDefaultPackages() {
        return !defPackages.isEmpty();
    }

    public Set<String> getDefaultPackageNames() {
        return defPackages;
    }

    public boolean isDefaultPackage(String name) {
        return defPackages.contains(name);
    }

    public boolean hasPlugins() {
        return !plugins.isEmpty();
    }

    public Map<String, FeaturePackPlugin> getPlugins() {
        return plugins;
    }

    public boolean hasSystemPaths() {
        return !systemPaths.isEmpty();
    }

    public Set<String> getSystemPaths() {
        return systemPaths;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((defPackages == null) ? 0 : defPackages.hashCode());
        result = prime * result + ((fpid == null) ? 0 : fpid.hashCode());
        result = prime * result + ((patchFor == null) ? 0 : patchFor.hashCode());
        result = prime * result + ((plugins == null) ? 0 : plugins.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        FeaturePackSpec other = (FeaturePackSpec) obj;
        if (defPackages == null) {
            if (other.defPackages != null)
                return false;
        } else if (!defPackages.equals(other.defPackages))
            return false;
        if (fpid == null) {
            if (other.fpid != null)
                return false;
        } else if (!fpid.equals(other.fpid))
            return false;
        if (patchFor == null) {
            if (other.patchFor != null)
                return false;
        } else if (!patchFor.equals(other.patchFor))
            return false;
        if (plugins == null) {
            if (other.plugins != null)
                return false;
        } else if (!plugins.equals(other.plugins))
            return false;
        return true;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append('[').append(fpid);
        if(patchFor != null) {
            buf.append(" patch-for=").append(patchFor);
        }
        if(!fpDeps.isEmpty()) {
            StringUtils.append(buf.append(" dependencies="), fpDeps.keySet());
        }
        if(!definedConfigs.isEmpty()) {
            StringUtils.append(buf.append(" defaultConfigs="), definedConfigs.values());
        }
        if(!defPackages.isEmpty()) {
            StringUtils.append(buf.append(" defaultPackages="), defPackages);
        }
        if(!plugins.isEmpty()) {
            StringUtils.append(buf.append(" plugins="), plugins.values());
        }
        return buf.append("]").toString();
    }
}
