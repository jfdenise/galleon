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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.aesh.command.impl.internal.OptionType;
import org.aesh.command.impl.internal.ProcessedOption;
import org.aesh.command.impl.internal.ProcessedOptionBuilder;
import org.aesh.command.parser.OptionParserException;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningOption;
import org.jboss.galleon.cli.CommandExecutionException;
import org.jboss.galleon.cli.HelpDescriptions;
import org.jboss.galleon.cli.PmCommandActivator;
import org.jboss.galleon.cli.PmCommandInvocation;
import org.jboss.galleon.cli.PmSession;
import org.jboss.galleon.cli.cmd.CliErrors;
import org.jboss.galleon.cli.cmd.CommandDomain;
import org.jboss.galleon.cli.cmd.InstalledFPLCompleter;
import org.jboss.galleon.cli.cmd.plugin.AbstractProvisionWithPlugins;
import static org.jboss.galleon.cli.cmd.plugin.AbstractProvisionWithPlugins.DIR_OPTION_NAME;
import org.jboss.galleon.cli.model.state.State;
import org.jboss.galleon.cli.resolver.PluginResolver;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.layout.FeaturePackLayout;
import org.jboss.galleon.layout.ProvisioningLayout;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.util.PathsUtils;
import org.jboss.galleon.xml.ProvisioningXmlParser;

/**
 *
 * @author jdenise@redhat.com
 */
public class UninstallCommand extends AbstractProvisionWithPlugins {

    public UninstallCommand(PmSession pmSession) {
        super(pmSession);
    }

    @Override
    protected List<ProcessedOption> getOtherOptions() throws OptionParserException {
        List<ProcessedOption> options = new ArrayList<>();
        options.add(ProcessedOptionBuilder.builder().name(ARGUMENT_NAME).
                hasValue(true).
                description(HelpDescriptions.FP_TO_REMOVE).
                type(String.class).
                optionType(OptionType.ARGUMENT).
                completer(InstalledFPLCompleter.class).
                build());
        return options;
    }

    @Override
    protected void doRunCommand(PmCommandInvocation session, Map<String, String> options) throws CommandExecutionException {
        try {
            getManager(session).uninstall(getFPID(session.getPmSession()), options);
        } catch (ProvisioningException | IOException e) {
            throw new CommandExecutionException(session.getPmSession(), CliErrors.uninstallFailed(), e);
        }
    }

    private FeaturePackLocation.FPID getFPID(PmSession session) throws CommandExecutionException {
        String fpid = getFPID();
        if (fpid == null) {
            throw new CommandExecutionException("No feature-pack provided");
        }
        try {
            return session.getResolvedLocation(getInstallationDirectory(session.getAeshContext()), fpid).getFPID();
        } catch (Exception e) {
            throw new CommandExecutionException(session, CliErrors.resolveLocationFailed(), e);
        }
    }

    @Override
    protected String getName() {
        return "uninstall";
    }

    @Override
    protected boolean canComplete(PmSession pmSession) {
        if (getFPID() == null) {
            return false;
        }
        return super.canComplete(pmSession);
    }

    @Override
    protected String getDescription() {
        return HelpDescriptions.UNINSTALL;
    }

    @Override
    protected List<DynamicOption> getDynamicOptions(State state) throws Exception {
        String fpid = getFPID();
        Path dir = getAbsolutePath(getUninstallDir(), pmSession.getAeshContext());
        // Build layout from this directory.
        ProvisioningConfig config = ProvisioningXmlParser.parse(PathsUtils.getProvisioningXml(dir), pmSession.getMessageWriter(false));
        if (config != null) {
            // Silent resolution.
            pmSession.unregisterTrackers();
            try {
                try (ProvisioningLayout<FeaturePackLayout> layout = pmSession.
                        getLayoutFactory().newConfigLayout(config, pmSession.getMessageWriter(false))) {
                    layout.uninstall(pmSession.
                            getResolvedLocation(getInstallationDirectory(pmSession.
                                    getAeshContext()), fpid).getFPID());
                    Set<ProvisioningOption> opts = PluginResolver.newResolver(pmSession,
                            layout).resolve().getInstall();
                    List<DynamicOption> options = new ArrayList<>();
                    for (ProvisioningOption opt : opts) {
                        DynamicOption dynOption = new DynamicOption(opt.getName(),
                                opt.isRequired());
                        options.add(dynOption);
                    }
                    return options;
                }
            } finally {
                pmSession.registerTrackers();
            }
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    protected void doValidateOptions(PmCommandInvocation invoc) throws CommandExecutionException {
    }

    private String getFPID() {
        String fpid = (String) getValue(ARGUMENT_NAME);
        if (fpid == null) {
            // Check in argument, that is the option completion case.
            fpid = getArgumentValue();
        }
        return fpid;
    }

    private String getUninstallDir() {
        String targetDirArg = (String) getValue(DIR_OPTION_NAME);
        if (targetDirArg == null) {
            // Check in argument or option, that is the option completion case.
            targetDirArg = getOptionValue(DIR_OPTION_NAME);
        }
        return targetDirArg;
    }

    @Override
    public CommandDomain getDomain() {
        return CommandDomain.PROVISIONING;
    }

    @Override
    protected PmCommandActivator getActivator() {
        return null;
    }
}
