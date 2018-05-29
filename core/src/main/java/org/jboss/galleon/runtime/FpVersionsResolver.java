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
package org.jboss.galleon.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.galleon.Errors;
import org.jboss.galleon.FeaturePackLocation;
import org.jboss.galleon.ProvisioningDescriptionException;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.FeaturePackDepsConfig;
import org.jboss.galleon.util.CollectionUtils;

/**
 *
 * @author Alexey Loubyansky
 */
public class FpVersionsResolver {

    static void resolveFpVersions(ProvisioningRuntimeBuilder rt) throws ProvisioningException {
        new FpVersionsResolver(rt).assertVersions();
    }

    static Map<FeaturePackLocation.Channel, FeaturePackLocation.FPID> resolveDeps(ProvisioningRuntimeBuilder rt, FeaturePackDepsConfig fpDeps, Map<FeaturePackLocation.Channel, FeaturePackLocation.FPID> collected)
            throws ProvisioningException {
        if(!fpDeps.hasFeaturePackDeps()) {
            return collected;
        }
        for(FeaturePackConfig fpConfig : fpDeps.getFeaturePackDeps()) {
            final int size = collected.size();
            collected = CollectionUtils.put(collected, fpConfig.getLocation().getChannel(), fpConfig.getLocation().getFPID());
            if(size == collected.size()) {
                continue;
            }
            collected = resolveDeps(rt, rt.getOrLoadFpBuilder(fpConfig.getLocation().getFPID()).spec, collected);
        }
        return collected;
    }

    private final ProvisioningRuntimeBuilder rt;
    private Set<FeaturePackLocation.Channel> missingVersions = Collections.emptySet();
    private List<FeaturePackLocation.Channel> branch = new ArrayList<>();
    private Map<FeaturePackLocation.Channel, Set<FeaturePackLocation.FPID>> conflicts = Collections.emptyMap();
    private Map<FeaturePackLocation.Channel, FeaturePackRuntimeBuilder> loaded = Collections.emptyMap();

    private FpVersionsResolver(ProvisioningRuntimeBuilder rt) {
        this.rt = rt;
    }

    public boolean hasMissingVersions() {
        return !missingVersions.isEmpty();
    }

    public Set<FeaturePackLocation.Channel> getMissingVersions() {
        return missingVersions;
    }

    public boolean hasVersionConflicts() {
        return !conflicts.isEmpty();
    }

    public Map<FeaturePackLocation.Channel, Set<FeaturePackLocation.FPID>> getVersionConflicts() {
        return conflicts;
    }

    private void assertVersions() throws ProvisioningException {
        assertVersions(rt.config);
        if(!missingVersions.isEmpty() || !conflicts.isEmpty()) {
            throw new ProvisioningDescriptionException(Errors.fpVersionCheckFailed(missingVersions, conflicts.values()));
        }
    }

    private void assertVersions(FeaturePackDepsConfig fpDepsConfig) throws ProvisioningException {
        if(!fpDepsConfig.hasFeaturePackDeps()) {
            return;
        }
        final int branchSize = branch.size();
        final Collection<FeaturePackConfig> fpDeps = fpDepsConfig.getFeaturePackDeps();
        Set<FeaturePackLocation.FPID> skip = Collections.emptySet();
        for(FeaturePackConfig fpConfig : fpDeps) {
            final FeaturePackLocation fpl = fpConfig.getLocation();
            if(fpl.getBuild() == null) {
                missingVersions = CollectionUtils.addLinked(missingVersions, fpl.getChannel());
                continue;
            }
            final FeaturePackRuntimeBuilder fp = loaded.get(fpl.getChannel());
            if(fp != null) {
                if(!fp.fpid.equals(fpl.getFPID()) && !branch.contains(fpl.getChannel())) {
                    Set<FeaturePackLocation.FPID> versions = conflicts.get(fp.fpid.getChannel());
                    if(versions != null) {
                        versions.add(fpl.getFPID());
                        continue;
                    }
                    versions = new LinkedHashSet<FeaturePackLocation.FPID>();
                    versions.add(fp.fpid);
                    versions.add(fpl.getFPID());
                    conflicts = CollectionUtils.putLinked(conflicts, fpl.getChannel(), versions);
                }
                skip = CollectionUtils.add(skip, fp.fpid);
                continue;
            }
            load(fpConfig.getLocation().getFPID());
            if(!missingVersions.isEmpty()) {
                missingVersions = CollectionUtils.remove(missingVersions, fpl.getChannel());
            }
            branch.add(fpl.getChannel());
        }
        for(FeaturePackConfig fpConfig : fpDeps) {
            final FeaturePackLocation fpl = fpConfig.getLocation();
            if(fpl.getBuild() == null || skip.contains(fpl.getFPID())) {
                continue;
            }
            assertVersions(rt.getFpBuilder(fpl.getChannel(), true).spec);
        }
        for(int i = 0; i < branch.size() - branchSize; ++i) {
            branch.remove(branch.size() - 1);
        }
    }

    private FeaturePackRuntimeBuilder load(FeaturePackLocation.FPID fpid) throws ProvisioningException {
        final FeaturePackRuntimeBuilder fp = rt.getOrLoadFpBuilder(fpid);
        loaded = CollectionUtils.put(loaded, fpid.getChannel(), fp);
        return fp;
    }
}
