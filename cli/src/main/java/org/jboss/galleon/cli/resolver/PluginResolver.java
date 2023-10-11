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
package org.jboss.galleon.cli.resolver;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.jboss.galleon.ProvisioningDescriptionException;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningOption;
import org.jboss.galleon.api.GalleonProvisioningLayout;
import org.jboss.galleon.api.Provisioning;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.cli.PmSession;
import org.jboss.galleon.cli.resolver.ResourceResolver.Resolver;
import org.jboss.galleon.layout.FeaturePackPluginVisitor;
import org.jboss.galleon.plugin.ProvisioningPlugin;
import org.jboss.galleon.universe.FeaturePackLocation;

/**
 *
 * @author jdenise@redhat.com
 */
public class PluginResolver implements Resolver<ResolvedPlugins> {

    private GalleonProvisioningConfig config;
    private Path file;
    private final PmSession session;
    private GalleonProvisioningLayout layout;

    private PluginResolver(PmSession session, GalleonProvisioningConfig config) {
        Objects.requireNonNull(session);
        Objects.requireNonNull(config);
        this.session = session;
        this.config = config;
    }

    private PluginResolver(PmSession session, Path file) {
        Objects.requireNonNull(session);
        Objects.requireNonNull(file);
        this.session = session;
        this.file = file;
    }

    private PluginResolver(PmSession session, GalleonProvisioningLayout layout) {
        Objects.requireNonNull(session);
        Objects.requireNonNull(layout);
        this.session = session;
        this.layout = layout;
    }

    public static PluginResolver newResolver(PmSession session,
            GalleonProvisioningLayout layout) {
        return new PluginResolver(session, layout);
    }

    public static PluginResolver newResolver(PmSession session, GalleonProvisioningConfig config) {
        return new PluginResolver(session, config);
    }

    public static PluginResolver newResolver(PmSession session, Path file) {
        return new PluginResolver(session, file);
    }

    public static PluginResolver newResolver(PmSession session, FeaturePackLocation loc) throws ProvisioningDescriptionException {
        GalleonProvisioningConfig config = GalleonProvisioningConfig.builder().addFeaturePackDep(loc).build();
        return new PluginResolver(session, config);
    }

    @Override
    public ResolvedPlugins resolve() throws ResolutionException {
        boolean closeLayout = layout == null;
        GalleonProvisioningLayout pLayout = layout;
        // Silent resolution.
        session.unregisterTrackers();
        Provisioning provisioning = null;
        try {
            try {
                
                if (pLayout == null) {
                    if (config != null) {
                        provisioning = session.newProvisioning(config, false);
                        pLayout = provisioning.newProvisioningLayout(config);
                    } else {
                        // No registration in universe during completion
                        provisioning = session.newProvisioning(file, false);
                        pLayout = provisioning.newProvisioningLayout(file, false);
                    }
                }
                return resolvePlugins(pLayout);

            } catch (Exception ex) {
                throw new ResolutionException(ex.getLocalizedMessage(), ex);
            } finally {
                if (closeLayout && pLayout != null) {
                    pLayout.close();
                }
                if(provisioning != null) {
                    provisioning.close();
                }
            }
        } finally {
            session.registerTrackers();
        }
    }

    public static ResolvedPlugins resolvePlugins(GalleonProvisioningLayout layout) throws ProvisioningException {
        final Set<ProvisioningOption> installOptions = new HashSet<>(ProvisioningOption.getStandardList());
        final Set<ProvisioningOption> diffOptions = new HashSet<>(ProvisioningOption.getStandardList());
        if (layout.hasPlugins()) {
            FeaturePackPluginVisitor<ProvisioningPlugin> visitor = new FeaturePackPluginVisitor<>() {
                @Override
                public void visitPlugin(ProvisioningPlugin plugin) throws ProvisioningException {
                    installOptions.addAll(plugin.getOptions().values());
                }
            };
            layout.visitPlugins(visitor, "org.jboss.galleon.plugin.InstallPlugin");
            FeaturePackPluginVisitor<ProvisioningPlugin> diffVisitor = new FeaturePackPluginVisitor<>() {
                @Override
                public void visitPlugin(ProvisioningPlugin plugin) throws ProvisioningException {
                    diffOptions.addAll(plugin.getOptions().values());
                }
            };
            layout.visitPlugins(diffVisitor, "org.jboss.galleon.plugin.StateDiffPlugin");
        }
        return new ResolvedPlugins(installOptions, diffOptions);
    }

}
