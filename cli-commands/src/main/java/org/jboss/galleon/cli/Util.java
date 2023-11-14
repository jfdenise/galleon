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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.aesh.command.impl.converter.FileConverter;
import org.aesh.readline.AeshContext;
import org.aesh.readline.util.Parser;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryListener;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.ProxySelector;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.jboss.galleon.BaseErrors;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.util.PathsUtils;


/**
 *
 * @author Alexey Loubyansky
 */
public class Util {

    static InputStream getResourceStream(String resource) throws CommandExecutionException {
        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        final InputStream pomIs = cl.getResourceAsStream(resource);
        if(pomIs == null) {
            throw new CommandExecutionException(resource + " not found");
        }
        return pomIs;
    }

    public static RepositorySystemSession newRepositorySession(final RepositorySystem repoSystem,
            Path path, RepositoryListener listener, ProxySelector proxySelector, boolean offline) {
        final DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        session.setRepositoryListener(listener);
        session.setOffline(offline);
        final LocalRepository localRepo = new LocalRepository(path.toString());
        session.setLocalRepositoryManager(repoSystem.newLocalRepositoryManager(session, localRepo));
        if (proxySelector != null) {
            session.setProxySelector(proxySelector);
        }
        return session;
    }

    // public for testing purpose
    public static RepositorySystem newRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        return locator.getService(RepositorySystem.class);
    }

    public static String formatColumns(List<String> lst, int width, int height) {
        String[] array = new String[lst.size()];
        lst.toArray(array);
        return Parser.formatDisplayList(array, height, width);
    }

    public static Path resolvePath(AeshContext ctx, String path) throws IOException {
        Path workDir = PmSession.getWorkDir(ctx);
        // Must be canonical due to deletion, eg:current dir is inside installation,
        // delete .., when current dir is deleted, absolute path <abs path to dir>/.. becomes invalid
        return Paths.get(new File(FileConverter.translatePath(workDir.toString(), path)).getCanonicalPath());
    }

    public static Path lookupInstallationDir(AeshContext ctx, Path install) throws ProvisioningException {
        if (install != null) {
            if (Files.exists(PathsUtils.getProvisioningXml(install))) {
                return install;
            } else {
                throw new ProvisioningException(BaseErrors.homeDirNotUsable(install));
            }
        } else {
            Path currentDir = PmSession.getWorkDir(ctx);
            while (currentDir != null) {
                if (Files.exists(PathsUtils.getProvisioningXml(currentDir))) {
                    return currentDir;
                }
                currentDir = currentDir.getParent();
            }
            throw new ProvisioningException(BaseErrors.homeDirNotUsable(PmSession.getWorkDir(ctx)));
        }
    }

    public static String formatChannel(FeaturePackLocation loc) {
        String channel = loc.getFrequency() == null ? loc.getChannel().getName() : loc.getChannel().getName()
                + "/" + loc.getFrequency();
        return (loc.getUniverse() == null ? "" : loc.getUniverse() + "@") + (channel == null ? "" : channel);
    }
}
