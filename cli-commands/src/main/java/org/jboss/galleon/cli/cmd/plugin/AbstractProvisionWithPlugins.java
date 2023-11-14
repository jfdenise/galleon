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
package org.jboss.galleon.cli.cmd.plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.aesh.command.impl.completer.FileOptionCompleter;
import org.aesh.command.impl.internal.OptionType;
import org.aesh.command.impl.internal.ProcessedOption;
import org.aesh.command.impl.internal.ProcessedOptionBuilder;
import org.aesh.command.parser.OptionParserException;
import org.aesh.readline.AeshContext;
import org.jboss.galleon.cli.CliLogging;
import org.jboss.galleon.cli.CommandExecutionException;
import org.jboss.galleon.cli.HelpDescriptions;
import org.jboss.galleon.cli.PmCommandActivator;
import org.jboss.galleon.cli.PmCommandInvocation;
import org.jboss.galleon.cli.PmSession;
import org.jboss.galleon.cli.Util;
import org.jboss.galleon.cli.cmd.AbstractDynamicCommand;
import org.jboss.galleon.cli.cmd.CommandWithInstallationDirectory;
import org.jboss.galleon.util.PathsUtils;

/**
 *
 * @author jdenise@redhat.com
 */
public abstract class AbstractProvisionWithPlugins extends AbstractDynamicCommand implements CommandWithInstallationDirectory {

    public static final String DIR_OPTION_NAME = "dir";
    public static final String VERBOSE_OPTION_NAME = "verbose";

    protected AbstractProvisionWithPlugins(PmSession pmSession) {
        super(pmSession, true);
    }

    protected abstract List<ProcessedOption> getOtherOptions() throws OptionParserException;

    @Override
    protected List<ProcessedOption> getStaticOptions() throws OptionParserException {
        List<ProcessedOption> options = new ArrayList<>();
        options.add(ProcessedOptionBuilder.builder().name(DIR_OPTION_NAME).
                hasValue(true).
                type(String.class).
                optionType(OptionType.NORMAL).
                description(HelpDescriptions.INSTALLATION_DIRECTORY).
                completer(FileOptionCompleter.class).
                build());
        options.add(ProcessedOptionBuilder.builder().name(VERBOSE_OPTION_NAME).
                hasValue(false).
                type(Boolean.class).
                description(HelpDescriptions.VERBOSE).
                optionType(OptionType.BOOLEAN).
                build());
        options.addAll(getOtherOptions());
        return options;
    }

    public boolean isVerbose() {
        return contains(VERBOSE_OPTION_NAME);
    }

    public String getDir() {
        return (String) getValue(DIR_OPTION_NAME);
    }

    @Override
    public Path getInstallationDirectory(AeshContext context) {
        try {
            return getAbsolutePath(getDir(), context);
        } catch (IOException ex) {
            CliLogging.exception(ex);
            return null;
        }
    }

    public Path getAbsolutePath(String path, AeshContext context) throws IOException {
        return path == null ? PmSession.getWorkDir(context) : Util.resolvePath(context, path);
    }

    protected abstract void doRunCommand(PmCommandInvocation session, Map<String, String> options) throws CommandExecutionException;

    @Override
    protected void runCommand(PmCommandInvocation session, Map<String, String> options) throws CommandExecutionException {
        if (isVerbose()) {
            session.getPmSession().enableMavenTrace(true);
        }
        try {
            doRunCommand(session, options);
        } finally {
            session.getPmSession().enableMavenTrace(false);
        }
    }

    @Override
    protected PmCommandActivator getActivator() {
        return null;
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
        Path workDir = PmSession.getWorkDir(pmSession.getAeshContext());
        return Files.exists(PathsUtils.getProvisioningXml(workDir));
    }
}
