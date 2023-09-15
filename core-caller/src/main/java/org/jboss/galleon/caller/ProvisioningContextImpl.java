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
package org.jboss.galleon.caller;

import java.util.Map;
import org.jboss.galleon.CoreVersion;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.tooling.api.ProvisioningContext;

public class ProvisioningContextImpl implements ProvisioningContext {

    private final ProvisioningManager manager;
    private final ProvisioningConfig config;
    private final Map<String, String> options;

    ProvisioningContextImpl(ProvisioningManager manager, ProvisioningConfig config, Map<String, String> options) {
        this.manager = manager;
        this.config = config;
        this.options = options;
    }

    @Override
    public void provision() throws ProvisioningException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(ProvisioningContextImpl.class.getClassLoader());
        try {
            manager.provision(config, options);
        } finally {
            Thread.currentThread().setContextClassLoader(loader);
        }
    }

    @Override
    public String getCoreVersion() {
        return CoreVersion.getVersion();
    }

    @Override
    public void close() {
        manager.close();
    }
}
