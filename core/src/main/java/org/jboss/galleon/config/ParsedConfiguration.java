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
package org.jboss.galleon.config;

import java.util.ArrayList;
import java.util.List;
import org.jboss.galleon.api.Configuration;

/**
 *
 * @author jdenise
 */
class ParsedConfiguration extends Configuration {

    private final ConfigModel model;

    ParsedConfiguration(ConfigModel model) {
        this.model = model;
        setModel(model.getModel());
        setName(model.getName());
        List<String> layers = new ArrayList<>();
        layers.addAll(model.getIncludedLayers());
        setLayers(layers);
        List<String> excludedLayers = new ArrayList<>();
        excludedLayers.addAll(model.getExcludedLayers());
        setExcludedLayers(excludedLayers);
        setProps(model.getProperties());
    }

    /**
     * @return the model
     */
    public ConfigModel getConfigModel() {
        return model;
    }
}
