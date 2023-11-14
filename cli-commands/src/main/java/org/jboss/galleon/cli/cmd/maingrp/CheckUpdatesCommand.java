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
package org.jboss.galleon.cli.cmd.maingrp;

import org.aesh.command.CommandDefinition;
import org.aesh.command.impl.internal.ParsedCommand;
import org.aesh.command.impl.internal.ParsedOption;
import org.aesh.command.option.Option;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.cli.CommandExecutionException;
import org.jboss.galleon.cli.HelpDescriptions;
import org.jboss.galleon.cli.cmd.InstalledProducerCompleter;
import org.jboss.galleon.cli.PmCommandInvocation;
import org.jboss.galleon.cli.PmOptionActivator;
import org.jboss.galleon.cli.PmSession;
import org.jboss.galleon.cli.cmd.CommandDomain;
import org.jboss.galleon.cli.cmd.installation.AbstractInstallationCommand;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "check-updates", description = HelpDescriptions.CHECK_UPDATES)
public class CheckUpdatesCommand extends AbstractInstallationCommand {

    public static class FPOptionActivator extends PmOptionActivator {

        @Override
        public boolean isActivated(ParsedCommand pc) {
            ParsedOption opt = pc.findLongOptionNoActivatorCheck(ALL_DEPENDENCIES_OPTION_NAME);
            return opt == null || opt.value() == null;
        }

    }

    public static class AllDepsOptionActivator extends PmOptionActivator {

        @Override
        public boolean isActivated(ParsedCommand pc) {
            ParsedOption opt = pc.findLongOptionNoActivatorCheck(FP_OPTION_NAME);
            return opt == null || opt.value() == null;
        }

    }
    public static final String UP_TO_DATE = "Up to date. No available updates nor patches.";
    public static final String UPDATES_AVAILABLE = "Some updates and/or patches are available.";
    public static final String FP_OPTION_NAME = "feature-packs";

    private static final String NONE = "none";
    public static final String ALL_DEPENDENCIES_OPTION_NAME = "include-all-dependencies";

    @Option(name = ALL_DEPENDENCIES_OPTION_NAME, hasValue = false, required = false,
            description = HelpDescriptions.CHECK_UPDATES_DEPENDENCIES,
            activator = AllDepsOptionActivator.class)
    boolean includeAll;

    @Option(name = FP_OPTION_NAME, hasValue = true, required = false,
            completer = InstalledProducerCompleter.class, description = HelpDescriptions.CHECK_UPDATES_FP,
            activator = FPOptionActivator.class)
    String fp;

    @Override
    protected void runCommand(PmCommandInvocation session) throws CommandExecutionException {
        throw new CommandExecutionException("Shouldn't be called");
    }

    public boolean isIncludeAll() {
        return includeAll;
    }

    public String getFp() {
        return fp;
    }
    @Override
    public String getCommandClassName(PmSession session) throws ProvisioningException {
        return "org.jboss.galleon.cli.cmd.maingrp.core.CoreCheckUpdatesCommand";
    }
    @Override
    public CommandDomain getDomain() {
        return CommandDomain.PROVISIONING;
    }
}