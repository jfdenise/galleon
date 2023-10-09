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
import org.jboss.galleon.MessageWriter;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.universe.UniverseResolver;
import org.jboss.galleon.universe.UniverseResolverBuilder;

public class ProvisioningBuilder extends UniverseResolverBuilder<ProvisioningBuilder> {

    private Path installationHome;
    private MessageWriter messageWriter;
    private UniverseResolver resolver;
    private boolean logTime;
    private boolean recordState = true;
    private boolean useDefaultCore;

    private ProvisioningBuilder() {
    }

    public ProvisioningBuilder setInstallationHome(Path installationHome) {
        this.installationHome = installationHome;
        return this;
    }

    public ProvisioningBuilder setUseDefaultCore(boolean useDefaultCore) {
        this.useDefaultCore = useDefaultCore;
        return this;
    }

    public ProvisioningBuilder setUniverseResolver(UniverseResolver resolver) throws ProvisioningException {
        this.resolver = resolver;
        return this;
    }

    public ProvisioningBuilder setMessageWriter(MessageWriter messageWriter) {
        this.messageWriter = messageWriter;
        return this;
    }

    public ProvisioningBuilder setLogTime(boolean logTime) {
        this.logTime = logTime;
        return this;
    }

    public ProvisioningBuilder setRecordState(boolean recordState) {
        this.recordState = recordState;
        return this;
    }

    public Provisioning build() throws ProvisioningException {
        return new ProvisioningImpl(this);
    }

    /**
     * Location of the installation.
     *
     * @return location of the installation
     */
    public Path getInstallationHome() {
        return installationHome;
    }

    public MessageWriter getMessageWriter() {
        return messageWriter;
    }

    /**
     * Whether to log provisioning time
     *
     * @return Whether provisioning time should be logged at the end
     */
    public boolean isLogTime() {
        return logTime;
    }

    public boolean isRecordState() {
        return recordState;
    }

    protected UniverseResolver getUniverseResolver() throws ProvisioningException {
        return resolver == null ? buildUniverseResolver() : resolver;
    }

    public boolean isUseDefaultCore() {
        return useDefaultCore;
    }

    public static ProvisioningBuilder builder() {
        return new ProvisioningBuilder();
    }

}
