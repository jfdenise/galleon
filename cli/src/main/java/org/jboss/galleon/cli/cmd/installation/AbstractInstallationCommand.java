/*
 * Copyright 2016-2019 Red Hat, Inc. and/or its affiliates
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
package org.jboss.galleon.cli.cmd.installation;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.aesh.command.option.Option;
import org.aesh.readline.AeshContext;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.api.GalleonProvisioningLayout;
import org.jboss.galleon.api.GalleonProvisioningRuntime;
import org.jboss.galleon.api.Provisioning;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.cli.CommandExecutionException;
import org.jboss.galleon.cli.HelpDescriptions;
import org.jboss.galleon.cli.PmSession;
import org.jboss.galleon.cli.PmSessionCommand;
import org.jboss.galleon.cli.Util;
import org.jboss.galleon.cli.cmd.CommandWithInstallationDirectory;
import static org.jboss.galleon.cli.cmd.plugin.AbstractProvisionWithPlugins.DIR_OPTION_NAME;
import org.jboss.galleon.cli.model.FeatureContainer;
import org.jboss.galleon.cli.model.FeatureContainers;
import org.jboss.galleon.util.PathsUtils;

/**
 *
 * @author jdenise@redhat.com
 */
public abstract class AbstractInstallationCommand extends PmSessionCommand implements CommandWithInstallationDirectory {

    @Option(name = DIR_OPTION_NAME, required = false,
            description = HelpDescriptions.INSTALLATION_DIRECTORY)
    protected File targetDirArg;

    protected Provisioning getManager(PmSession session) throws ProvisioningException {
        return session.newProvisioning(Util.lookupInstallationDir(session.getAeshContext(),
                targetDirArg == null ? null : targetDirArg.toPath()), false);
    }

    @Override
    public Path getInstallationDirectory(AeshContext context) {
        try {
            return Util.lookupInstallationDir(context, targetDirArg == null ? null : targetDirArg.toPath());
        } catch (ProvisioningException ex) {
            return null;
        }
    }

    public FeatureContainer getFeatureContainer(PmSession session, GalleonProvisioningLayout layout) throws ProvisioningException,
            CommandExecutionException, IOException {
        FeatureContainer container;
        Provisioning manager = getManager(session);

        if (Files.exists(PathsUtils.getProvisionedStateXml(manager.getInstallationHome()))) {
            throw new CommandExecutionException("Specified directory doesn't contain an installation");
        }
        if (layout == null) {
            GalleonProvisioningConfig config = manager.getProvisioningConfig();
            try (GalleonProvisioningRuntime runtime = manager.getProvisioningRuntime(config)) {
                container = FeatureContainers.fromProvisioningRuntime(session, runtime);
            }
        } else {
            try (GalleonProvisioningRuntime runtime = manager.getProvisioningRuntime(layout)) {
                container = FeatureContainers.fromProvisioningRuntime(session, runtime);
            }
        }
        return container;
    }
}
