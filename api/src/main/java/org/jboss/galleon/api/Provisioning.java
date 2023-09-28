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
package org.jboss.galleon.api;

import java.nio.file.Path;
import java.util.List;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.impl.ProvisioningUtil;
import org.jboss.galleon.progresstracking.ProgressCallback;
import org.jboss.galleon.progresstracking.ProgressTracker;
import org.jboss.galleon.universe.UniverseSpec;

public interface Provisioning extends AutoCloseable {

    @Override
    public void close();

    /**
     * Location of the installation.
     *
     * @return location of the installation
     */
    public Path getInstallationHome();

    /**
     * Whether to log provisioning time
     *
     * @return Whether provisioning time should be logged at the end
     */
    public boolean isLogTime();

    /**
     * Whether provisioning state will be recorded after (re-)provisioning.
     *
     * @return true if the provisioning state is recorded after provisioning,
     * otherwise false
     */
    public boolean isRecordState();

    public static boolean isFeaturePack(Path path) {
        return ProvisioningUtil.isFeaturePack(path);
    }

    public static GalleonFeaturePackDescription getFeaturePackDescription(Path path) throws ProvisioningException {
        return ProvisioningUtil.getFeaturePackDescription(path);
    }

    public ProvisioningContext buildProvisioningContext(Path provisioning) throws ProvisioningException;

    public FeaturePackLocation addLocal(Path path, boolean installInUniverse) throws ProvisioningException;

    public ProvisioningContext buildProvisioningContext(GalleonProvisioningConfig config) throws ProvisioningException;

    public ProvisioningContext buildProvisioningContext(GalleonProvisioningConfig config, List<Path> customConfigs) throws ProvisioningException;

    // Required by CLI
    /**
     * Add named universe spec to the provisioning configuration
     *
     * @param name universe name
     * @param universeSpec universe spec
     * @throws ProvisioningException in case of an error
     */
    public void addUniverse(String name, UniverseSpec universeSpec) throws ProvisioningException;

    /**
     * Removes universe spec associated with the name from the provisioning
     * configuration
     *
     * @param name name of the universe spec or null for the default universe
     * spec
     * @throws ProvisioningException in case of an error
     */
    public void removeUniverse(String name) throws ProvisioningException;

    /**
     * Set the default universe spec for the installation
     *
     * @param universeSpec universe spec
     * @throws ProvisioningException in case of an error
     */
    public void setDefaultUniverse(UniverseSpec universeSpec) throws ProvisioningException;

    public GalleonProvisioningConfig getProvisioningConfig() throws ProvisioningException;

    public void setProgressCallback(String id, ProgressCallback<?> callback);

    public void setProgressTracker(String id, ProgressTracker<?> tracker);
}
