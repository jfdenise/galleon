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

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Map.Entry;
import org.jboss.galleon.MessageWriter;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.progresstracking.ProgressTracker;
import org.jboss.galleon.repo.RepositoryArtifactResolver;
import org.jboss.galleon.api.ProvisioningContext;
import org.jboss.galleon.core.builder.ProvisioningContextBuilder;

public class ProvisioningContextBuilderImpl implements ProvisioningContextBuilder {

    @Override
    public ProvisioningContext buildProvisioningContext(URLClassLoader loader, Path home,
            MessageWriter msgWriter,
            boolean logTime,
            boolean recordState,
            RepositoryArtifactResolver artifactResolver,
            Map<String, ProgressTracker<?>> progressTrackers) throws ProvisioningException {
        boolean noHome = home == null;
        if (home == null) {
            try {
                home = Files.createTempDirectory("gallon-no-installation");
            } catch (IOException ex) {
                throw new ProvisioningException(ex);
            }
        }
        ProvisioningManager pm = ProvisioningManager.builder().addArtifactResolver(artifactResolver)
                .setInstallationHome(home)
                .setMessageWriter(msgWriter)
                .setLogTime(logTime)
                .setRecordState(recordState)
                .build();
        for (Entry<String, ProgressTracker<?>> entry : progressTrackers.entrySet()) {
            pm.getLayoutFactory().setProgressTracker(entry.getKey(), entry.getValue());
        }
        return new ProvisioningContextImpl(loader, noHome, pm);
    }
}
