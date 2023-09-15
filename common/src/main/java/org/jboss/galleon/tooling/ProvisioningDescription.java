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
package org.jboss.galleon.tooling;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jboss.galleon.ProvisioningException;

public class ProvisioningDescription {

    public static class Builder {

        private List<GalleonFeaturePack> packs = Collections.emptyList();
        private Map<String, String> options = Collections.emptyMap();

        private List<Configuration> configs = Collections.emptyList();
        private List<GalleonLocalItem> localItems = Collections.emptyList();
        private Path customConfig;

        private Path provisioningFile;

        private Builder() {
        }

        public Builder setProvisioningFile(Path provisioningFile) {
            this.provisioningFile = provisioningFile;
            return this;
        }

        public Builder setConfigs(List<Configuration> configs) {
            this.configs = configs;
            return this;
        }

        public Builder setLocalItems(List<GalleonLocalItem> localItems) {
            this.localItems = localItems;
            return this;
        }

        public Builder setCustomConfig(Path customConfig) {
            this.customConfig = customConfig;
            return this;
        }

        public Builder setFeaturePacks(List<GalleonFeaturePack> packs) {
            this.packs = packs;
            return this;
        }

        public Builder setOptions(Map<String, String> options) {
            this.options = options;
            return this;
        }

        public ProvisioningDescription build() throws ProvisioningException {
            if (provisioningFile != null) {
                if (packs != null && !packs.isEmpty()) {
                    throw new ProvisioningException("A provisioning file has been set, FeaturePacks can't be set.");
                }
            }
            return new ProvisioningDescription(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final List<GalleonFeaturePack> packs;
    private final Map<String, String> options = new HashMap<>();
    private final List<Configuration> configs = new ArrayList<>();
    private final List<GalleonLocalItem> localItems;
    private final Path customConfig;
    private final Path provisioningFile;

    private ProvisioningDescription(Builder builder) throws ProvisioningException {

        this.provisioningFile = builder.provisioningFile;

        this.packs = builder.packs;

        this.localItems = builder.localItems;
        this.customConfig = builder.customConfig;
        this.options.putAll(builder.options);

        configs.addAll(builder.configs);
    }

    /**
     * @return the packs
     */
    public List<GalleonFeaturePack> getFeaturePacks() {
        return packs;
    }

    /**
     * @return the options
     */
    public Map<String, String> getOptions() {
        return options;
    }

    /**
     * @return the configs
     */
    public List<Configuration> getConfigs() {
        return configs;
    }

    /**
     * @return the localItems
     */
    public List<GalleonLocalItem> getLocalItems() {
        return localItems;
    }

    /**
     * @return the customConfig
     */
    public Path getCustomConfig() {
        return customConfig;
    }

    /**
     * @return the provisioningFile
     */
    public Path getProvisioningFile() {
        return provisioningFile;
    }

}
