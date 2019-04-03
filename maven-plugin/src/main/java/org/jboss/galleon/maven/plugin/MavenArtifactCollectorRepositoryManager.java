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
package org.jboss.galleon.maven.plugin;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.galleon.maven.plugin.util.MavenArtifactRepositoryManager;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.util.ZipUtils;

/**
 * Maven artifact manager that collects resolved artifacts and associated pom files.
 * @author jdenise@redhat.com
 */
public class MavenArtifactCollectorRepositoryManager extends MavenArtifactRepositoryManager {

    private static final String ROOT_PATH = "repository";
    private static final String POM = ".pom";
    private static final String MD5 = ".md5";
    private static final String SHA = ".sha";

    private final FileSystem fs;

    /**
     * Creates an instance that only will resolve artifacts using the Maven
     * local repository and collect artifacts inside a zip file.
     *
     * @param zipFile The directory in which artifacts are copied
     * @param repoSystem The repository system instance, must not be
     * {@code null}.
     * @param repoSession The repository session, must not be {@code null}.
     */
    public MavenArtifactCollectorRepositoryManager(final Path zipFile, final RepositorySystem repoSystem, final RepositorySystemSession repoSession) throws IOException {
        this(zipFile, repoSystem, repoSession, null);
    }

    /**
     * Creates an instance that will use a list of remote repositories where to
     * find an artifact if the artifact is not in the local Maven repository and
     * collect artifacts inside a zip file.
     *
     * @param zipFile The zip file in which artifacts are copied
     * @param repoSystem The repository system instance, must not be
     * {@code null}.
     * @param repoSession The repository session, must not be {@code null}.
     * @param repositories The list of remote repositories where to find the
     * artifact if it is not in the local Maven repository.
     */
    public MavenArtifactCollectorRepositoryManager(final Path zipFile,
            final RepositorySystem repoSystem, final RepositorySystemSession repoSession, final List<RemoteRepository> repositories) throws IOException {
        super(repoSystem, repoSession, repositories);
        Map<String, String> env = new HashMap<>();
        env.put("create", "true");
        fs = ZipUtils.newFileSystem(zipFile, env);
    }

    public void done() throws IOException {
        fs.close();
    }

    @Override
    public void resolve(MavenArtifact artifact) throws MavenUniverseException {
        super.resolve(artifact);
        Path artifactLocalPath = artifact.getPath();
        Path localMvnRepoPath = getSession().getLocalRepository().getBasedir().toPath();
        addArtifact(localMvnRepoPath, artifactLocalPath);
        try (Stream<Path> files = Files.list(artifactLocalPath.getParent())) {
            files.filter(MavenArtifactCollectorRepositoryManager::checkAddPath).forEach(new Consumer<Path>() {
                @Override
                public void accept(Path t) {
                    addArtifact(localMvnRepoPath, t);
                }
            });
        } catch (IOException ex) {
            throw new RuntimeException("Can't retrieve files in " + artifactLocalPath.getParent(), ex);
        }
    }

    private void addArtifact(Path localMvnRepoPath, Path artifactLocalPath) {
        Path relativized = localMvnRepoPath.relativize(artifactLocalPath);
        Path pathInZipfile = fs.getPath(ROOT_PATH, relativized.toString());
        try {
            Files.createDirectories(pathInZipfile.getParent());
            Files.copy(artifactLocalPath, pathInZipfile,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new RuntimeException("Can't add " + artifactLocalPath + " to zip file", ex);
        }
    }

    private static boolean checkAddPath(Path path) {
        String name = path.toString();
        return name.endsWith(POM)
                || name.endsWith(MD5)
                || name.endsWith(SHA);
    }

}
