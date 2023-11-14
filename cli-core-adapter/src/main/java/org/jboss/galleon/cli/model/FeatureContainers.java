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
package org.jboss.galleon.cli.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.jboss.galleon.Constants;

import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningOption;
import org.jboss.galleon.cli.core.ProvisioningSession;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.layout.FeaturePackPluginVisitor;
import org.jboss.galleon.plugin.CliPlugin;
import org.jboss.galleon.plugin.ProvisionedConfigHandler;
import org.jboss.galleon.runtime.FeaturePackRuntime;
import org.jboss.galleon.runtime.PackageRuntime;
import org.jboss.galleon.runtime.ProvisioningRuntime;
import org.jboss.galleon.runtime.ProvisioningRuntimeBuilder;
import org.jboss.galleon.runtime.ResolvedSpecId;
import org.jboss.galleon.spec.FeatureSpec;
import org.jboss.galleon.spec.PackageDependencySpec;
import org.jboss.galleon.state.ProvisionedConfig;
import org.jboss.galleon.state.ProvisionedFeature;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.FeaturePackLocation.ProducerSpec;
import org.jboss.galleon.universe.FeaturePackLocation.FPID;

/**
 *
 * @author jdenise@redhat.com
 */
public abstract class FeatureContainers {

    public static FeatureContainer fromFeaturePackId(ProvisioningSession session, FPID fpid,
            String name) throws ProvisioningException, IOException {
        if (fpid.getBuild() == null) {
            FeaturePackLocation loc = session.getUniverse().resolveLatestBuild(fpid.getLocation());
            fpid = loc.getFPID();
        }
        FeatureContainer fp = Caches.getFeaturePackInfo(fpid);
        if (fp != null) {
            return fp;
        }
        try (ProvisioningRuntime rt = buildFullRuntime(fpid, session)) {
            fp = new FeaturePackInfo(name, fpid, rt.getProvisioningConfig());
            populateFeatureContainer(fp, session, rt, true);
            Caches.addFeaturePackInfo(fpid, fp);
        }
        return fp;
    }

    public static FeatureContainer fromProvisioningRuntime(ProvisioningSession session,
            ProvisioningRuntime runtime) throws ProvisioningException, IOException {
        ProvisioningInfo info = new ProvisioningInfo(runtime.getProvisioningConfig());
        populateFeatureContainer(info, session, runtime, false);
        return info;
    }

    private static void populateFeatureContainer(FeatureContainer fp,
            ProvisioningSession session, ProvisioningRuntime runtime, boolean allSpecs) throws ProvisioningException, IOException {
        // Need a Map of FeaturePack to resolve external packages/
        Map<String, FeaturePackRuntime> gavs = new HashMap<>();
        for (FeaturePackRuntime rt : runtime.getFeaturePacks()) {
            gavs.put(Identity.buildOrigin(rt.getFPID().getProducer()), rt);
        }
        List<CliPlugin> cliPlugins = new ArrayList<>();
        FeaturePackPluginVisitor<CliPlugin> visitor = new FeaturePackPluginVisitor<CliPlugin>() {
            @Override
            public void visitPlugin(CliPlugin plugin) throws ProvisioningException {
                cliPlugins.add(plugin);
            }
        };
        runtime.getLayout().visitPlugins(visitor, CliPlugin.class);
        CliPlugin plugin = cliPlugins.isEmpty() ? null : cliPlugins.get(0);
        PackageGroupsBuilder pkgBuilder = new PackageGroupsBuilder();
        FeatureSpecsBuilder specsBuilder = new FeatureSpecsBuilder();
        String optionalOption = runtime.getProvisioningConfig().
                getOption(ProvisioningOption.OPTIONAL_PACKAGES.getName());
        if (optionalOption == null) {
            optionalOption = Constants.ALL;
        }
        boolean includeOptional = false;
        boolean includePassive = false;
        boolean checkPassive = false;
        if (optionalOption.equals(Constants.ALL)) {
            includePassive = true;
            includeOptional = true;
        } else {
            if (optionalOption.equals(Constants.NONE)) {
                // No optional included.
                includeOptional = false;
                includePassive = false;
            } else {
                if (optionalOption.equals(Constants.PASSIVE)) {
                    // Include passives that have dependencies present.
                    includeOptional = false;
                    includePassive = true;
                    checkPassive = true;
                } else {
                    if (optionalOption.equals(Constants.PASSIVE_PLUS)) {
                        // Include passives that have dependencies present and optionals
                        includeOptional = true;
                        includePassive = true;
                        checkPassive = true;
                    } else {
                        throw new ProvisioningException("Not recognized value for " + Constants.OPTIONAL_PACKAGES);
                    }
                }
            }
        }
        boolean includeOptionalF = includeOptional;
        boolean includePassiveF = includePassive;
        Map<String, Map<String, PackageRuntime>> allPackages = new HashMap<>();
        for (FeaturePackRuntime rt : runtime.getFeaturePacks()) {
            fp.addLayers(rt.loadLayers());
            pkgBuilder.resetRoots();
            allPackages.put(rt.getFPID().getProducer().toString(), new HashMap<>());
            for (PackageRuntime pkg : rt.getPackages()) {
                if (checkPassive && pkg.isPassive() && !pkg.isPassiveIncluded()) { // exclude passives that don't match.
                    continue;
                }
                allPackages.get(rt.getFPID().getProducer().toString()).put(pkg.getName(), pkg);
                pkgBuilder.buildGroups(new PackageInfo(pkg, Identity.
                        fromChannel(rt.getFPID().getProducer(), pkg.getName()), plugin), new PackageGroupsBuilder.PackageInfoBuilder() {
                    @Override
                    public PackageInfo build(Identity identity, PackageInfo parent) {
                        try {
                            // Packages that have no origin, doesn't mean that they are local.
                            // It depends on the way FP dependencies have an origin or not.
                            // If a FP dependency has no origin, the "local" package could be
                            // located in this dependency.
                            FeaturePackRuntime currentRuntime = parent.getFeaturePackRuntime();
                            Identity resolvedIdentity = null;
                            PackageRuntime p = null;
                            if (identity.getOrigin().equals(Identity.EMPTY)) {
                                // First local to the parent package.
                                p = currentRuntime.getPackage(identity.getName());
                                if (p == null) {
                                    // Then lookup dependencies with no origin.
                                    for (FeaturePackConfig fpdep : currentRuntime.getSpec().getFeaturePackDeps()) {
                                        if (currentRuntime.getSpec().originOf(fpdep.getLocation().getProducer()) == null) {
                                            FeaturePackRuntime depRuntime = gavs.get(Identity.buildOrigin(fpdep.getLocation().getProducer()));
                                            p = depRuntime.getPackage(identity.getName());
                                            if (p != null) {
                                                resolvedIdentity = Identity.fromChannel(fpdep.getLocation().getProducer(), identity.getName());
                                                break;
                                            }
                                        }
                                    }
                                } else {
                                    resolvedIdentity = Identity.fromChannel(currentRuntime.getFPID().getProducer(), identity.getName());
                                }
                            } else {
                                // Could be a free text, maps that to ga. Only ga are exposed as origin for now.
                                // XXX JFDENISE, TODO, we could expose actual origin but would require to expose the mapping.
                                FeaturePackRuntime extRt = gavs.get(identity.getOrigin());
                                if (extRt == null) {
                                    FeaturePackConfig fpdep = currentRuntime.getSpec().getFeaturePackDep(identity.getOrigin());
                                    if (fpdep != null) {
                                        resolvedIdentity = Identity.fromChannel(fpdep.getLocation().getProducer(), identity.getName());
                                        extRt = gavs.get(resolvedIdentity.getOrigin());
                                    }
                                } else {
                                    resolvedIdentity = identity;
                                }
                                if (extRt != null) {
                                    p = extRt.getPackage(identity.getName());
                                }
                            }

                            if (p == null) {
                                // Optional package that has been excluded
                                return null;
                            }

                            return new PackageInfo(p, resolvedIdentity, plugin);
                        } catch (IOException | ProvisioningException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            }
            fp.setPackagesRoot(Identity.buildOrigin(rt.getFPID().getProducer()), pkgBuilder.getPackagesRoot());
            // Attach the full set, this targets the container dependency that expose them all.
            if (allSpecs) {
                Group specsRoot = specsBuilder.buildTree(runtime.getLayout(), session,
                        rt.getFPID(), fp.getFPID(), pkgBuilder.getPackages(), allSpecs, null);
                fp.setFeatureSpecRoot(Identity.buildOrigin(rt.getFPID().getProducer()), specsRoot);
            }
        }
        fp.setAllPackages(pkgBuilder.getPackages());

        Map<ProducerSpec, Set<ResolvedSpecId>> actualSet = new HashMap<>();
        Map<ResolvedSpecId, List<FeatureInfo>> features = new HashMap<>();
        for (ProvisionedConfig c : runtime.getConfigs()) {
            ConfigInfo config = new ConfigInfo(c.getModel(), c.getName(), c.getLayers());
            fp.addFinalConfig(config);
            FeatureGroupsBuilder grpBuilder = new FeatureGroupsBuilder();
            c.handle(new ProvisionedConfigHandler() {
                @Override
                public void nextFeature(ProvisionedFeature feature) throws ProvisioningException {
                    FeaturePackRuntime rt = runtime.getFeaturePack(feature.getSpecId().getProducer());
                    FeatureSpec featureSpec = rt.getFeatureSpec(feature.getSpecId().getName());
                    ProducerSpec producer = feature.getSpecId().getProducer();
                    for (PackageDependencySpec spec : featureSpec.getLocalPackageDeps()) {
                        PackageRuntime pkg = allPackages.get(producer.toString()).get(spec.getName());
                        if(pkg != null) {
                            if (includePassiveF && pkg.isPassive()) {
                                fp.addPassivePackage(producer.toString(), feature.getSpecId().getName(), spec.getName());
                            } else {
                                if (includeOptionalF && pkg.isOptional()) {
                                    fp.addOptionalPackage(producer.toString(), feature.getSpecId().getName(), spec.getName());
                                }
                            }
                        }
                    }
                    if (featureSpec.hasExternalPackageDeps()) {
                        for (String origin : featureSpec.getPackageOrigins()) {
                            for (PackageDependencySpec spec : featureSpec.getExternalPackageDeps(origin)) {
                                PackageRuntime pkg = allPackages.get(origin).get(spec.getName());
                                if (pkg != null) {
                                    if (includePassiveF && pkg.isPassive()) {
                                        fp.addPassivePackage(origin, feature.getSpecId().getName(), spec.getName());
                                    } else if (includeOptionalF && pkg.isOptional()) {
                                        fp.addOptionalPackage(origin, feature.getSpecId().getName(), spec.getName());
                                    }
                                }
                            }
                        }
                    }
                    Set<ResolvedSpecId> set = actualSet.get(feature.getSpecId().getProducer());
                    if (set == null) {
                        set = new HashSet<>();
                        actualSet.put(feature.getSpecId().getProducer(), set);
                    }
                    set.add(feature.getSpecId());
                    String fullSpecName = feature.getSpecId().getName();
                    List<String> path = new ArrayList<>();
                    Group parent = grpBuilder.buildFeatureGroups(fullSpecName, feature.getId(), path);
                    FeatureInfo featInfo = new FeatureInfo(config, feature, path, fp.getFPID());
                    List<FeatureInfo> lst = features.get(feature.getSpecId());
                    if (lst == null) {
                        lst = new ArrayList<>();
                        features.put(feature.getSpecId(), lst);
                    }
                    lst.add(featInfo);
                    parent.setFeature(featInfo);
                    // Specs have already been computed first place.
                    if (allSpecs) {
                        FeatureSpecInfo spec = specsBuilder.getAllSpecs().get(feature.getSpecId());
                        featInfo.attachSpecInfo(spec);
                        parent.setFeature(featInfo);
                    }
                }
            });
            config.setFeatureGroupRoot(grpBuilder.getRoot());
        }
        // Handle packages that are not directly referenced from a feature.
        for (Entry<String,Map<String, PackageRuntime>> packageEntry : allPackages.entrySet()) {
            String producer = packageEntry.getKey();
            Set<String> allOptionals = new HashSet<>();
            Set<String> allPassives = new HashSet<>();
            Map<String, Set<String>> optionals = fp.getOptionalPackages().get(producer);
            if (optionals != null) {
                for (Set<String> vals : optionals.values()) {
                    allOptionals.addAll(vals);
                }
            }
            Map<String, Set<String>> passives = fp.getPassivePackages().get(producer);
            if (passives != null) {
                for (Set<String> vals : passives.values()) {
                    allPassives.addAll(vals);
                }
            }
            Map<String, PackageRuntime> packages = packageEntry.getValue();
            for (Entry<String, PackageRuntime> entry : packages.entrySet()) {
                String name = entry.getKey();
                PackageRuntime pkg = entry.getValue();
                if (!allOptionals.contains(name) && !allPassives.contains(name)) {
                    if (includePassiveF && pkg.isPassive()) {
                        fp.addOrphanPassivePackage(producer, name);
                    } else if (includeOptionalF && pkg.isOptional()) {
                        fp.addOrphanOptionalPackage(producer, name);
                    }
                }
            }
        }
        if (!allSpecs) {
            // Build the set of FeatureSpecInfo, side effect is to connect
            // packages and feature-specs.
            for (Entry<ProducerSpec, Set<ResolvedSpecId>> entry : actualSet.entrySet()) {
                Group specsRoot = specsBuilder.buildTree(runtime.getLayout(),
                        session, entry.getKey().getLocation().getFPID(), fp.getFPID(),
                        pkgBuilder.getPackages(), false, entry.getValue());
                for (ResolvedSpecId rs : entry.getValue()) {
                    List<FeatureInfo> lst = features.get(rs);
                    for (FeatureInfo fi : lst) {
                        fi.attachSpecInfo(specsBuilder.getAllSpecs().get(rs));
                    }
                }
                fp.setFeatureSpecRoot(Identity.buildOrigin(entry.getKey()), specsRoot);
            }
        }
        fp.seAllFeatureSpecs(specsBuilder.getAllSpecs());
        fp.setAllFeatures(features);
    }

    private static ProvisioningRuntime buildFullRuntime(FPID fpid, ProvisioningSession pmSession) throws ProvisioningException {
        FeaturePackConfig config = FeaturePackConfig.forLocation(fpid.getLocation());
        ProvisioningConfig provisioning = ProvisioningConfig.builder().addFeaturePackDep(config).build();
        ProvisioningRuntime runtime = ProvisioningRuntimeBuilder.newInstance(pmSession.getMessageWriter(false))
                .initLayout(pmSession.getLayoutFactory(), provisioning)
                .build();
        return runtime;
    }
}
