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
package org.jboss.galleon.cli.cmd.featurepack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Option;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.cli.AbstractCompleter;
import org.jboss.galleon.cli.CommandExecutionException;
import org.jboss.galleon.cli.HelpDescriptions;
import org.jboss.galleon.cli.PmCommandInvocation;
import org.jboss.galleon.cli.PmCompleterInvocation;
import org.jboss.galleon.cli.PmSession;
import org.jboss.galleon.cli.cmd.CliErrors;
import static org.jboss.galleon.cli.cmd.state.InfoTypeCompleter.ALL;
import static org.jboss.galleon.cli.cmd.state.InfoTypeCompleter.LAYERS;
import org.jboss.galleon.cli.cmd.state.StateInfoUtil;
import org.jboss.galleon.cli.model.ConfigInfo;
import static org.jboss.galleon.cli.path.FeatureContainerPathConsumer.CONFIGS;
import static org.jboss.galleon.cli.path.FeatureContainerPathConsumer.DEPENDENCIES;
import static org.jboss.galleon.cli.path.FeatureContainerPathConsumer.OPTIONS;
import org.jboss.galleon.cli.resolver.PluginResolver;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.layout.FeaturePackLayout;
import org.jboss.galleon.layout.ProvisioningLayout;
import org.jboss.galleon.runtime.ProvisioningRuntime;
import org.jboss.galleon.runtime.ProvisioningRuntimeBuilder;
import org.jboss.galleon.state.ProvisionedConfig;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.FeaturePackLocation.FPID;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "get-info", description = HelpDescriptions.GET_INFO_FP)
public class GetInfoCommand extends AbstractFeaturePackCommand {

    public static final String PATCH_FOR = "Patch for ";

    public class InfoTypeCompleter extends AbstractCompleter {

        @Override
        protected List<String> getItems(PmCompleterInvocation completerInvocation) {
            // No patch for un-customized FP.
            return Arrays.asList(ALL, CONFIGS, DEPENDENCIES, LAYERS, OPTIONS);
        }

    }
    @Option(completer = InfoTypeCompleter.class, description = HelpDescriptions.FP_INFO_TYPE)
    private String type;

    @Override
    protected void runCommand(PmCommandInvocation commandInvocation) throws CommandExecutionException {
        if (fpl != null && file != null) {
            throw new CommandExecutionException("File and location can't be both set");
        }
        if (fpl == null && file == null) {
            throw new CommandExecutionException("File or location must be set");
        }
        PmSession session = commandInvocation.getPmSession();
        FeaturePackLayout product = null;
        List<FeaturePackLocation> dependencies = new ArrayList<>();
        ProvisioningConfig provisioning;
        ProvisioningLayout<FeaturePackLayout> layout = null;
        try {
            try {
                if (fpl != null) {
                    FeaturePackLocation loc;
                    loc = session.getResolvedLocation(null, fpl);
                    FeaturePackConfig config = FeaturePackConfig.forLocation(loc);
                    provisioning = ProvisioningConfig.builder().addFeaturePackDep(config).build();
                    layout = session.getLayoutFactory().newConfigLayout(provisioning);
                } else {
                    layout = session.getLayoutFactory().newConfigLayout(file.toPath(), true);
                }

                for (FeaturePackLayout fpLayout : layout.getOrderedFeaturePacks()) {
                    boolean isProduct = true;
                    for (FeaturePackLayout fpLayout2 : layout.getOrderedFeaturePacks()) {
                        if (fpLayout2.getSpec().hasTransitiveDep(fpLayout.getFPID().getProducer())
                                || fpLayout2.getSpec().getFeaturePackDep(fpLayout.getFPID().getProducer()) != null) {
                            isProduct = false;
                            break;
                        }
                    }
                    if (isProduct) {
                        product = fpLayout;
                    } else {
                        dependencies.add(session.getExposedLocation(null, fpLayout.getFPID().getLocation()));
                    }
                }
            } catch (ProvisioningException ex) {
                throw new CommandExecutionException(commandInvocation.getPmSession(), CliErrors.infoFailed(), ex);
            }

            if (product == null) {
                throw new CommandExecutionException("No feature-pack found");
            }

            StateInfoUtil.printFeaturePack(commandInvocation,
                    session.getExposedLocation(null, product.getFPID().getLocation()));

            try {
                final FPID patchFor = product.getSpec().getPatchFor();
                if (patchFor != null) {
                    commandInvocation.println(PATCH_FOR + patchFor);
                }
            } catch (ProvisioningException e) {
                throw new CommandExecutionException(commandInvocation.getPmSession(), CliErrors.infoFailed(), e);
            }

            try {
                if (type != null) {
                    switch (type) {
                        case ALL: {
                            displayDependencies(commandInvocation, dependencies);
                            displayConfigs(commandInvocation, layout);
                            displayLayers(commandInvocation, layout);
                            displayOptions(commandInvocation, layout);
                            break;
                        }
                        case CONFIGS: {
                            displayConfigs(commandInvocation, layout);
                            break;
                        }
                        case DEPENDENCIES: {
                            displayDependencies(commandInvocation, dependencies);
                            break;
                        }
                        case LAYERS: {
                            displayLayers(commandInvocation, layout);
                            break;
                        }
                        case OPTIONS: {
                            displayOptions(commandInvocation, layout);
                            break;
                        }
                        default: {
                            throw new CommandExecutionException(CliErrors.invalidInfoType());
                        }
                    }
                }
            } catch (ProvisioningException | IOException ex) {
                throw new CommandExecutionException(commandInvocation.getPmSession(), CliErrors.infoFailed(), ex);
            }
        } finally {
            if (layout != null) {
                layout.close();
            }
        }
    }

    private void displayDependencies(PmCommandInvocation commandInvocation, List<FeaturePackLocation> dependencies) throws CommandExecutionException {
        String str = StateInfoUtil.buildDependencies(dependencies, null);
        if (str != null) {
            commandInvocation.println("Dependencies");
            commandInvocation.println(str);
        }
    }

    private void displayConfigs(PmCommandInvocation commandInvocation,
            ProvisioningLayout<FeaturePackLayout> pLayout) throws ProvisioningException, IOException {
        Map<String, List<ConfigInfo>> configs = new HashMap<>();
        try (ProvisioningRuntime rt = ProvisioningRuntimeBuilder.
                newInstance(commandInvocation.getPmSession().getMessageWriter(false))
                .initRtLayout(pLayout.transform(ProvisioningRuntimeBuilder.FP_RT_FACTORY))
                .setEncoding(ProvisioningManager.Builder.ENCODING)
                .build()) {
            for (ProvisionedConfig m : rt.getConfigs()) {
                String model = m.getModel();
                List<ConfigInfo> names = configs.get(model);
                if (names == null) {
                    names = new ArrayList<>();
                    configs.put(model, names);
                }
                if (m.getName() != null) {
                    names.add(new ConfigInfo(model, m.getName(), m.getLayers()));
                }
            }
            String str = StateInfoUtil.buildConfigs(configs, pLayout);
            if (str != null) {
                commandInvocation.println(str);
            }
        }
    }

    private void displayLayers(PmCommandInvocation commandInvocation,
            ProvisioningLayout<FeaturePackLayout> pLayout) throws ProvisioningException, IOException {
        String str = StateInfoUtil.buildLayers(pLayout);
        if (str != null) {
            commandInvocation.println(str);
        }
    }

    private void displayOptions(PmCommandInvocation commandInvocation,
            ProvisioningLayout<FeaturePackLayout> layout) throws ProvisioningException {
        String str = StateInfoUtil.buildOptions(PluginResolver.resolvePlugins(layout));
        if (str != null) {
            commandInvocation.println(str);
        }
    }
}
