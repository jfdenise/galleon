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
package org.jboss.galleon.cli.cmd.state.pkg.core;

import java.util.HashMap;
import java.util.Map;

import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.cli.core.ProvisioningSession;
import org.jboss.galleon.cli.model.FeatureContainer;
import org.jboss.galleon.cli.model.FeatureContainers;
import org.jboss.galleon.cli.model.Group;
import org.jboss.galleon.cli.path.FeatureContainerPathConsumer;
import org.jboss.galleon.cli.path.PathConsumerException;
import org.jboss.galleon.cli.path.PathParser;
import org.jboss.galleon.cli.path.PathParserException;
import org.jboss.galleon.config.FeaturePackConfig;

/**
 *
 * @author jdenise@redhat.com
 */
public class PackagesUtil {

    public static String getPackage(ProvisioningSession session, FeaturePackLocation.FPID fpid, String pkg) throws PathParserException, PathConsumerException, ProvisioningException, Exception {
        String path = FeatureContainerPathConsumer.PACKAGES_PATH
                + pkg + (pkg.endsWith("" + PathParser.PATH_SEPARATOR) ? "" : PathParser.PATH_SEPARATOR);
        FeatureContainer full = FeatureContainers.fromFeaturePackId(session, fpid, null);

        FeatureContainerPathConsumer consumer = new FeatureContainerPathConsumer(full, false);
        PathParser.parse(path, consumer);
        Group grp = consumer.getCurrentNode(path);
        if (grp == null || grp.getPackage() == null) {
            throw new ProvisioningException("Not a valid package " + pkg);
        }
        return grp.getPackage().getSpec().getName();
    }

    public static Map<FeaturePackConfig, String> getIncludedPackages(ProvisioningSession session, FeaturePackConfig config, String pkg) throws PathParserException, PathConsumerException, ProvisioningException, Exception {
        Map<FeaturePackConfig, String> packages = new HashMap<>();
        if (config == null) {
            for (FeaturePackConfig c : session.getState().getConfig().getFeaturePackDeps()) {
                if (c.getIncludedPackages().contains(pkg)) {
                    packages.put(c, pkg);
                }
            }
            for (FeaturePackConfig c : session.getState().getConfig().getTransitiveDeps()) {
                if (c.getIncludedPackages().contains(pkg)) {
                    packages.put(c, pkg);
                }
            }
        } else {
            if (config.getIncludedPackages().contains(pkg)) {
                packages.put(config, pkg);
            }
        }
        if (packages.isEmpty()) {
            throw new ProvisioningException("Not a valid package " + pkg);
        }
        return packages;
    }

    public static Map<FeaturePackConfig, String> getExcludedPackages(ProvisioningSession session, FeaturePackConfig config, String pkg) throws PathParserException, PathConsumerException, ProvisioningException, Exception {
        Map<FeaturePackConfig, String> packages = new HashMap<>();
        if (config == null) {
            for (FeaturePackConfig c : session.getState().getConfig().getFeaturePackDeps()) {
                if (c.getExcludedPackages().contains(pkg)) {
                    packages.put(c, pkg);
                }
            }
            for (FeaturePackConfig c : session.getState().getConfig().getTransitiveDeps()) {
                if (c.getExcludedPackages().contains(pkg)) {
                    packages.put(c, pkg);
                }
            }
        } else {
            if (config.getExcludedPackages().contains(pkg)) {
                packages.put(config, pkg);
            }
        }
        if (packages.isEmpty()) {
            throw new ProvisioningException("Not a valid package " + pkg);
        }
        return packages;
    }
}
