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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author jdenise
 */
public class GalleonLayer implements Comparable<GalleonLayer> {

    private final String name;
    private final Set<String> dependencies = new TreeSet<>();
    private final Map<String, String> properties = new HashMap<>();
    private boolean isAutomaticInjection;

    public GalleonLayer(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public String getName() {
        return name;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 53 * hash + Objects.hashCode(this.name);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final GalleonLayer other = (GalleonLayer) obj;
        return Objects.equals(this.name, other.name);
    }

    @Override
    public int compareTo(GalleonLayer t) {
        return name.compareTo(t.name);
    }

    /**
     * @return the dependencies
     */
    public Set<String> getDependencies() {
        return dependencies;
    }

    /**
     * @return the properties
     */
    public Map<String, String> getProperties() {
        return properties;
    }

    /**
     * @return the isAutomaticInjection
     */
    public boolean isIsAutomaticInjection() {
        return isAutomaticInjection;
    }

    /**
     * @param isAutomaticInjection the isAutomaticInjection to set
     */
    public void setIsAutomaticInjection(boolean isAutomaticInjection) {
        this.isAutomaticInjection = isAutomaticInjection;
    }

}
