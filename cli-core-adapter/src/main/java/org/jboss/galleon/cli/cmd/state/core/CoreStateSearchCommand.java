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
package org.jboss.galleon.cli.cmd.state.core;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import org.aesh.utils.Config;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.cli.CommandExecutionException;
import org.jboss.galleon.cli.cmd.CliErrors;
import org.jboss.galleon.cli.cmd.state.StateSearchCommand;
import org.jboss.galleon.cli.cmd.state.pkg.core.CoreAbstractPackageCommand;
import org.jboss.galleon.cli.core.ProvisioningSession;
import org.jboss.galleon.cli.model.FeatureContainer;
import org.jboss.galleon.cli.model.FeatureInfo;
import org.jboss.galleon.cli.model.FeatureSpecInfo;
import org.jboss.galleon.cli.model.Group;
import org.jboss.galleon.cli.model.Identity;
import org.jboss.galleon.cli.model.PackageInfo;
import org.jboss.galleon.cli.model.state.State;
import org.jboss.galleon.cli.path.FeatureContainerPathConsumer;
import org.jboss.galleon.cli.path.PathConsumerException;
import org.jboss.galleon.cli.path.PathParser;
import org.jboss.galleon.cli.path.PathParserException;
import org.jboss.galleon.runtime.ResolvedSpecId;

/**
 *
 * @author jdenise@redhat.com
 */
public class CoreStateSearchCommand extends CoreAbstractStateCommand<StateSearchCommand> {

    @Override
    protected void runCommand(ProvisioningSession session, State state, StateSearchCommand command) throws IOException, ProvisioningException, CommandExecutionException {
        try {
            if (command.getQuery() == null && command.getPkg() == null) {
                throw new CommandExecutionException("One of --query or --package must be set");
            }
            FeatureContainer container = session.getContainer();
            run(session.getContainer(), session, command, false);

            if (command.getInDependencies()) {
                if (!container.getFullDependencies().isEmpty()) {
                    session.getCommandInvocation().println("");
                    session.getCommandInvocation().println("Search in dependencies");
                    for (FeatureContainer c : container.getFullDependencies().values()) {
                        session.getCommandInvocation().println("dependency: " + c.getFPID());
                        run(c, session, command, true);
                    }
                }
            }
        } catch (Exception ex) {
            throw new CommandExecutionException(session.getPmSession(), CliErrors.searchFailed(), ex);
        }
    }

    private void run(FeatureContainer container, ProvisioningSession session, StateSearchCommand command, boolean dependencySearch) throws PathParserException,
            PathConsumerException, ProvisioningException {
        if (command.getPkg()!= null) {
            PackageInfo spec = getPackage(dependencySearch ? container : new CoreAbstractPackageCommand.AllPackagesContainer(container), command.getPkg());
            session.getCommandInvocation().println(Config.getLineSeparator() + "As a direct dependency of a package:");
            StringBuilder pBuilder = new StringBuilder();
            for (Entry<String, Group> pkgs : container.getPackages().entrySet()) {
                Group root = pkgs.getValue();
                for (Group g : root.getGroups()) {
                    for (Group dep : g.getGroups()) {
                        if (dep.getIdentity().equals(spec.getIdentity())) {
                            pBuilder.append("  " + g.getIdentity()).append(Config.getLineSeparator());
                            break;
                        }
                    }
                }
            }
            if (pBuilder.length() != 0) {
                session.getCommandInvocation().println(pBuilder.toString());
            } else {
                session.getCommandInvocation().println("NONE");
            }
            Set<ResolvedSpecId> fspecs = findFeatures(spec, container);
            session.getCommandInvocation().println("Reachable from features:");
            if (fspecs.isEmpty()) {
                session.getCommandInvocation().println("NONE");
            } else {
                for (ResolvedSpecId id : fspecs) {
                    List<FeatureInfo> features = container.getAllFeatures().get(id);
                    // Can be null if we have all specs whatever the set of features.
                    if (features != null) {
                        for (FeatureInfo fi : features) {
                            session.getCommandInvocation().println("  " + fi.getPath());
                        }
                    } else {
                        session.getCommandInvocation().println("      [spec only] " + toPath(id));
                    }
                }
            }
            return;
        }
        session.getCommandInvocation().println(Config.getLineSeparator() + "Packages:");
        StringBuilder pBuilder = new StringBuilder();
        for (Entry<String, Group> pkgs : container.getPackages().entrySet()) {
            Group root = pkgs.getValue();
            for (Group g : root.getGroups()) {
                PackageInfo p = g.getPackage();
                if (p.getIdentity().toString().contains(command.getQuery())) {
                    pBuilder.append("  " + FeatureContainerPathConsumer.PACKAGES_PATH + p.getIdentity()).append(Config.getLineSeparator());
                    if (!dependencySearch) {
                        pBuilder.append("    Reachable from features:").append(Config.getLineSeparator());
                        Set<ResolvedSpecId> fspecs = findFeatures(p, container);
                        if (fspecs.isEmpty()) {
                            pBuilder.append("      NONE" + Config.getLineSeparator());
                        }
                        for (ResolvedSpecId id : fspecs) {
                            List<FeatureInfo> features = container.getAllFeatures().get(id);
                            // Can be null if we have all specs whatever the set of features.
                            if (features != null) {
                                for (FeatureInfo fi : features) {
                                    pBuilder.append("      " + fi.getPath()).append(Config.getLineSeparator());
                                }
                            } else {
                                pBuilder.append("  [spec only] " + toPath(id)).append(Config.getLineSeparator());
                            }
                        }
                    }
                }
            }
        }
        if (pBuilder.length() != 0) {
            session.getCommandInvocation().println(pBuilder.toString());
        } else {
            session.getCommandInvocation().println("NONE");
        }

        pBuilder = new StringBuilder();
        session.getCommandInvocation().println(Config.getLineSeparator() + "Package dependencies:");
        for (Entry<String, Group> pkgs : container.getPackages().entrySet()) {
            Group root = pkgs.getValue();
            for (Group g : root.getGroups()) {
                StringBuilder depBuilder = new StringBuilder();
                for (Group dep : g.getGroups()) {
                    if (dep.getIdentity().toString().contains(command.getQuery())) {
                        depBuilder.append("  " + dep.getIdentity()).append(Config.getLineSeparator());
                        break;
                    }
                }
                if (depBuilder.length() != 0) {
                    pBuilder.append("  Found as a direct dependencies of " + g.getIdentity()).append(Config.getLineSeparator());
                    pBuilder.append(depBuilder);
                }
            }
        }
        if (pBuilder.length() != 0) {
            session.getCommandInvocation().println(pBuilder.toString());
        } else {
            session.getCommandInvocation().println("NONE");
        }

        pBuilder = new StringBuilder();
        session.getCommandInvocation().println(Config.getLineSeparator() + "Package content:");
        for (Entry<String, Group> entry : container.getPackages().entrySet()) {
            Group root = entry.getValue();
            for (Group g : root.getGroups()) {
                PackageInfo pkginfo = g.getPackage();
                StringBuilder contentBuilder = new StringBuilder();
                for (String c : pkginfo.getContent()) {
                    if (c.contains(command.getQuery())) {
                        contentBuilder.append(c).append(Config.getLineSeparator());
                    }
                }
                if (contentBuilder.length() != 0) {
                    pBuilder.append("  Found in content of "
                            + g.getIdentity()).append(Config.getLineSeparator());
                    pBuilder.append(contentBuilder);
                }
            }
        }
        if (pBuilder.length() != 0) {
            session.getCommandInvocation().println(pBuilder.toString());
        } else {
            session.getCommandInvocation().println("NONE");
        }
        pBuilder = new StringBuilder();
        // Features?
        session.getCommandInvocation().println(Config.getLineSeparator() + "Features:");
        for (Entry<ResolvedSpecId, List<FeatureInfo>> features : container.getAllFeatures().entrySet()) {
            ResolvedSpecId id = features.getKey();
            List<FeatureInfo> fs = features.getValue();
            if (fs == null) {
                if (id.getName().contains(command.getQuery())) {
                    pBuilder.append("  [spec only] " + toPath(id)).append(Config.getLineSeparator());
                }
            } else {
                for (FeatureInfo fi : fs) {
                    if (fi.getPath().contains(command.getQuery())) {
                        pBuilder.append("  " + fi.getPath()).append(Config.getLineSeparator());
                    }
                }
            }
        }
        if (pBuilder.length() != 0) {
            session.getCommandInvocation().println(pBuilder.toString());
        } else {
            session.getCommandInvocation().println("NONE");
        }
    }

    private String toPath(ResolvedSpecId id) {
        return FeatureContainerPathConsumer.FEATURES_PATH
                + Identity.buildOrigin(id.getProducer()) + PathParser.PATH_SEPARATOR
                + id.getName().replaceAll("\\.", "" + PathParser.PATH_SEPARATOR);
    }

    private Set<ResolvedSpecId> findFeatures(PackageInfo spec, FeatureContainer container) {
        Set<ResolvedSpecId> fspecs = new HashSet<>();
        for (Entry<ResolvedSpecId, FeatureSpecInfo> features : container.getAllSpecs().entrySet()) {
            for (PackageInfo info : features.getValue().getPackages()) {
                Group grp = container.getAllPackages().get(info.getIdentity());
                Set<Identity> identities = new HashSet<>();
                visitPkg(grp, identities);
                if (identities.contains(spec.getIdentity())) {
                    fspecs.add(features.getKey());
                    break;
                }
            }
        }
        return fspecs;
    }

    private PackageInfo getPackage(FeatureContainer container, String id) throws PathParserException, PathConsumerException, ProvisioningException {
        String path = FeatureContainerPathConsumer.PACKAGES_PATH + id;
        FeatureContainerPathConsumer consumer = new FeatureContainerPathConsumer(container, false);
        PathParser.parse(path, consumer);
        Group grp = consumer.getCurrentNode(path);
        if (grp == null) {
            throw new ProvisioningException("Invalid path");
        }
        if (grp.getPackage() == null) {
            throw new ProvisioningException("Path is not a package");
        }
        return grp.getPackage();
    }

    private void visitPkg(Group pkg, Set<Identity> identities) {
        if (!identities.contains(pkg.getIdentity())) {
            identities.add(pkg.getIdentity());
            for (Group dep : pkg.getGroups()) {
                visitPkg(dep, identities);
            }
        }
    }
}
