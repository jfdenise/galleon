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
package org.jboss.galleon.tooling.api;

import java.util.Collections;
import java.util.Set;

/**
 *
 * @author jdenise@redhat.com
 */
public class Configuration {

    private String model;
    private String name;
    private Set<String> layers = Collections.emptySet();
    private Set<String> excludedLayers = Collections.emptySet();

    public String getModel() {
        return model;
    }

    public String getName() {
        return name;
    }

    public Set<String> getLayers() {
        return layers;
    }

    public Set<String> getExcludedLayers() {
        return excludedLayers;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setLayers(Set<String> layers) {
        this.layers = layers;
    }

    public void setExcludedLayers(Set<String> excludedLayers) {
        this.excludedLayers = excludedLayers;
    }
}
