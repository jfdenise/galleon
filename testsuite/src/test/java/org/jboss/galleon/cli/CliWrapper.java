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
package org.jboss.galleon.cli;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.aesh.command.CommandException;
import org.aesh.command.CommandNotFoundException;
import org.aesh.command.CommandRuntime;
import org.aesh.command.parser.CommandLineParserException;
import org.aesh.command.validator.CommandValidatorException;
import org.aesh.command.validator.OptionValidatorException;
import org.jboss.galleon.api.APIVersion;
import org.jboss.galleon.cli.config.Configuration;
import org.jboss.galleon.cli.core.ProvisioningSession;
import org.jboss.galleon.universe.UniverseSpec;
import org.jboss.galleon.util.IoUtils;

/**
 *
 * @author jdenise@redhat.com
 */
public class CliWrapper {

    private final PmSession session;
    private final ProvisioningSession ctx;
    private final String userHome;
    private final File testUserHome;
    private final File mvnRepo;
    private final CommandRuntime runtime;
    final ByteArrayOutputStream out = new ByteArrayOutputStream();

    public CliWrapper() throws Exception {
        this(null);
    }

    public CliWrapper(UniverseSpec builtin) throws Exception {
        userHome = System.getProperty("user.home");
        testUserHome = Files.createTempDirectory("galleon-cli-user-home-").toFile();
        System.setProperty("user.home", testUserHome.getAbsolutePath());
        // This is the default repository if no repository has been set.
        mvnRepo = new File(testUserHome, ".m2" + File.separator + "repository");
        mvnRepo.mkdirs();
        session = new PmSession(Configuration.parse(), builtin);
        runtime = CliMain.newRuntime(session, new PrintStream(out, false, StandardCharsets.UTF_8.name()));
        session.getUniverse().disableBackgroundResolution();
        session.getPmConfiguration().getMavenConfig().enableOffline(true);
        session.throwException();
        ctx = (ProvisioningSession) session.getGalleonContext(APIVersion.getVersion());
        ctx.enableTrackers(false);
    }

    public String getOutput() {
        return out.toString();
    }

    public void close() {
        session.removeCoreContext(APIVersion.getVersion());
        getSession().close();
        System.setProperty("user.home", userHome);
        IoUtils.recursiveDelete(testUserHome.toPath());
    }

    public void execute(String str) throws CommandNotFoundException,
            CommandLineParserException, OptionValidatorException,
            CommandValidatorException, CommandException, InterruptedException,
            IOException {
        out.reset();
        runtime.executeCommand(str);
    }

    public Path newDir(String name, boolean create) {
        File dir = new File(testUserHome, name).getAbsoluteFile();
        if (create) {
            dir.mkdirs();
        }
        return dir.toPath();
    }

    /**
     * @return the session
     */
    public PmSession getSession() {
        return session;
    }

    /**
     * @return the ctx
     */
    public ProvisioningSession getContext() {
        return ctx;
    }

    /**
     * @return the mvnRepo
     */
    public Path getMvnRepo() {
        return mvnRepo.toPath();
    }
}
