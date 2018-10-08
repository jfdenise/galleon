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
package org.jboss.galleon.cli.cmd.maingrp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.aesh.command.activator.OptionActivator;
import org.aesh.command.impl.completer.FileOptionCompleter;
import org.aesh.command.impl.internal.OptionType;
import org.aesh.command.impl.internal.ParsedCommand;
import org.aesh.command.impl.internal.ParsedOption;
import org.aesh.command.impl.internal.ProcessedOption;
import org.aesh.command.impl.internal.ProcessedOptionBuilder;
import org.aesh.command.parser.OptionParserException;
import org.aesh.readline.AeshContext;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.cli.CliLogging;
import org.jboss.galleon.cli.CommandExecutionException;
import org.jboss.galleon.cli.HelpDescriptions;
import org.jboss.galleon.cli.resolver.PluginResolver;
import org.jboss.galleon.cli.PmCommandActivator;
import org.jboss.galleon.cli.PmCommandInvocation;
import org.jboss.galleon.cli.PmCompleterInvocation;
import org.jboss.galleon.cli.PmSession;
import org.jboss.galleon.cli.Util;
import org.jboss.galleon.cli.cmd.AbstractCommaSeparatedCompleter;
import org.jboss.galleon.cli.cmd.CliErrors;
import org.jboss.galleon.cli.cmd.CommandDomain;
import org.jboss.galleon.cli.cmd.plugin.AbstractPluginsCommand;
import static org.jboss.galleon.cli.cmd.maingrp.AbstractProvisioningCommand.DIR_OPTION_NAME;
import org.jboss.galleon.layout.FeaturePackDescriber;
import org.jboss.galleon.plugin.PluginOption;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.util.PathsUtils;

/**
 *
 * @author jdenise@redhat.com
 */
public class InstallCommand extends AbstractPluginsCommand {

    public static class LayersCompleter extends AbstractCommaSeparatedCompleter {

        @Override
        protected List<String> getItems(PmCompleterInvocation completerInvocation) {
            @SuppressWarnings("unchecked")
            InstallCommand cmd = (InstallCommand) completerInvocation.getCommand();
            try {
                String model = cmd.getModel(completerInvocation.getPmSession());
                String fpid = cmd.getId(completerInvocation.getPmSession());
                if (fpid != null) {
                    FeaturePackLocation loc = FeaturePackLocation.fromString(fpid);
                    List<String> ret = new ArrayList<>();
                    String buffer = completerInvocation.getGivenCompleteValue();
                    Set<String> excluded = new HashSet<>();
                    if (buffer != null) {
                        String[] arr = buffer.split(",");
                        for (String a : arr) {
                            if (!a.isEmpty()) {
                                excluded.add(a.trim());
                            }
                        }
                    }
                    ret.addAll(LayersConfigBuilder.getLayerNames(completerInvocation.getPmSession(), model,
                            loc, excluded));

                    return ret;
                }
            } catch (Exception ex) {
                CliLogging.error(ex.toString());
            }
            return null;
        }

    }

    private class ArgOptionActivator implements OptionActivator {

        @Override
        public boolean isActivated(ParsedCommand parsedCommand) {
            ParsedOption opt = parsedCommand.findLongOptionNoActivatorCheck(FILE_OPTION_NAME);
            return opt == null || opt.value() == null;
        }

    }

    private class FileOptionActivator implements OptionActivator {

        @Override
        public boolean isActivated(ParsedCommand parsedCommand) {
            ParsedOption opt = parsedCommand.argument();
            return opt == null || opt.value() == null;
        }

    }
    public static final String FILE_OPTION_NAME = "file";

    public static final String LAYERS_OPTION_NAME = "layers";
    public static final String CONFIG_OPTION_NAME = "config";
    public static final String MODEL_OPTION_NAME = "model";

    public InstallCommand(PmSession pmSession) {
        super(pmSession);
    }

    @Override
    protected void runCommand(PmCommandInvocation session, Map<String, String> options, FeaturePackLocation loc) throws CommandExecutionException {
        try {
            String filePath = (String) getValue(FILE_OPTION_NAME);
            final ProvisioningManager manager = getManager(session);
            String layers = (String) getValue(LAYERS_OPTION_NAME);
            if (filePath != null) {
                Path p = Util.resolvePath(session.getConfiguration().getAeshContext(), filePath);
                loc = session.getPmSession().getLayoutFactory().addLocal(p, true);
            }
            if (layers == null) {
                manager.install(loc, options);
            } else {
                manager.install(new LayersConfigBuilder(pmSession, layers.split(","),
                        (String) getValue(MODEL_OPTION_NAME),
                        (String) getValue(CONFIG_OPTION_NAME), loc).build(), options);
            }
        } catch (ProvisioningException | IOException ex) {
            throw new CommandExecutionException(session.getPmSession(), CliErrors.installFailed(), ex);
        }
    }

    @Override
    protected Set<PluginOption> getPluginOptions(FeaturePackLocation loc) throws ProvisioningException {
        try {
            //If we have a file, retrieve the options from the file.
            String file = (String) getValue(FILE_OPTION_NAME);
            if (file == null) {
                // Check in argument or option, that is the option completion case.
                file = getOptionValue(FILE_OPTION_NAME);
            }
            if (file == null) {
                return pmSession.getResolver().get(loc.toString(),
                        PluginResolver.newResolver(pmSession, loc)).getInstall();
            } else {
                return pmSession.getResolver().get(file,
                        PluginResolver.newResolver(pmSession, loc)).getInstall();
            }
        } catch (InterruptedException ex) {
            Thread.interrupted();
            throw new ProvisioningException(ex);
        } catch (ExecutionException ex) {
            throw new ProvisioningException(ex.getCause());

        }
    }

    @Override
    protected String getName() {
        return "install";
    }

    @Override
    protected String getDescription() {
        return HelpDescriptions.INSTALL;
    }

    @Override
    protected List<ProcessedOption> getOtherOptions() throws OptionParserException {
        List<ProcessedOption> options = new ArrayList<>();
        ProcessedOption dir = ProcessedOptionBuilder.builder().name(DIR_OPTION_NAME).
                hasValue(true).
                type(String.class).
                optionType(OptionType.NORMAL).
                description(HelpDescriptions.INSTALLATION_DIRECTORY).
                completer(FileOptionCompleter.class).
                build();
        options.add(dir);
        ProcessedOption file = ProcessedOptionBuilder.builder().name(FILE_OPTION_NAME).
                hasValue(true).
                type(String.class).
                optionType(OptionType.NORMAL).
                activator(new FileOptionActivator()).
                description(HelpDescriptions.FP_FILE_PATH).
                completer(FileOptionCompleter.class).
                build();
        options.add(file);
        ProcessedOption layers = ProcessedOptionBuilder.builder().name(LAYERS_OPTION_NAME).
                hasValue(true).
                type(String.class).
                optionType(OptionType.NORMAL).
                completer(LayersCompleter.class).
                description(HelpDescriptions.INSTALL_LAYERS).
                build();
        options.add(layers);
        ProcessedOption model = ProcessedOptionBuilder.builder().name(MODEL_OPTION_NAME).
                hasValue(true).
                type(String.class).
                optionType(OptionType.NORMAL).
                description(HelpDescriptions.INSTALL_MODEL).
                build();
        options.add(model);
        ProcessedOption config = ProcessedOptionBuilder.builder().name(CONFIG_OPTION_NAME).
                hasValue(true).
                type(String.class).
                optionType(OptionType.NORMAL).
                description(HelpDescriptions.INSTALL_CONFIG).
                build();
        options.add(config);
        return options;
    }

    @Override
    protected String getId(PmSession session) throws CommandExecutionException {
        String filePath = (String) getValue(FILE_OPTION_NAME);
        if (filePath == null) {
            filePath = getOptionValue(FILE_OPTION_NAME);
            if (filePath == null) {
                return super.getId(session);
            }
        }
        Path path = Util.resolvePath(session.getAeshContext(), filePath);
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return FeaturePackDescriber.readSpec(path).getFPID().toString();
        } catch (ProvisioningException ex) {
            throw new CommandExecutionException(session, CliErrors.retrieveFeaturePackID(), ex);
        }
    }

    String getModel(PmSession session) throws CommandExecutionException {
        String model = (String) getValue(MODEL_OPTION_NAME);
        if (model == null) {
            model = getOptionValue(MODEL_OPTION_NAME);
        }
        return model;
    }

    @Override
    protected OptionActivator getArgumentActivator() {
        return new ArgOptionActivator();
    }

    @Override
    protected void doValidateOptions(PmCommandInvocation invoc) throws CommandExecutionException {
        String filePath = (String) getValue(FILE_OPTION_NAME);
        if (filePath == null) {
            super.doValidateOptions(invoc);
            return;
        }
        String arg = (String) getValue(ARGUMENT_NAME);
        if (arg != null) {
            throw new CommandExecutionException("Only one of file or Feature-pack location is allowed.");
        }
        Path p = Util.resolvePath(invoc.getConfiguration().getAeshContext(), filePath);
        if (!Files.exists(p)) {
            throw new CommandExecutionException(p + " doesn't exist.");
        }
    }

    @Override
    protected boolean canComplete(PmSession pmSession) {
        //Only if we have a valid directory
        String targetDirArg = (String) getValue(DIR_OPTION_NAME);
        if (targetDirArg == null) {
            // Check in argument or option, that is the option completion case.
            targetDirArg = getOptionValue(DIR_OPTION_NAME);
        }
        if (targetDirArg != null) {
            return true;
        }
        // Current dir must be empty or contain an installation
        Path workDir = PmSession.getWorkDir(pmSession.getAeshContext());
        return Files.exists(PathsUtils.getProvisioningXml(workDir)) || workDir.toFile().list().length == 0;
    }

    @Override
    public Path getInstallationDirectory(AeshContext context) {
        String targetDirArg = (String) getValue(DIR_OPTION_NAME);
        Path workDir = PmSession.getWorkDir(context);
        return targetDirArg == null ? workDir : Util.resolvePath(context, targetDirArg);
    }

    @Override
    protected PmCommandActivator getActivator() {
        return null;
    }

    @Override
    public CommandDomain getDomain() {
        return CommandDomain.PROVISIONING;
    }
}
