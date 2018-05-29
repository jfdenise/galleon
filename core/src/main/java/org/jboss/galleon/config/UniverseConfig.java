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

/**
 *
 * @author Alexey Loubyansky
 */
public class UniverseConfig {

    private final String name;
    private final String factory;
    private final String location;

    public UniverseConfig(String name, String factory, String location) {
        this.name = name;
        this.factory = factory;
        this.location = location;
    }

    public boolean isDefault() {
        return name == null;
    }

    public String getName() {
        return name;
    }

    public String getFactory() {
        return factory;
    }

    public String getLocation() {
        return location;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((factory == null) ? 0 : factory.hashCode());
        result = prime * result + ((location == null) ? 0 : location.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        UniverseConfig other = (UniverseConfig) obj;
        if (factory == null) {
            if (other.factory != null)
                return false;
        } else if (!factory.equals(other.factory))
            return false;
        if (location == null) {
            if (other.location != null)
                return false;
        } else if (!location.equals(other.location))
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append("[universe ")
                .append(name == null ? "default" : name)
                .append(" factory=").append(factory)
                .append(" location ").append(location)
                .append(']').toString();
    }
}
