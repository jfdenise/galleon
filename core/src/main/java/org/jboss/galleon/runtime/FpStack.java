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
package org.jboss.galleon.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.jboss.galleon.config.ConfigId;

import org.jboss.galleon.config.ConfigCustomizations;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.universe.FeaturePackLocation.ProducerSpec;
import org.jboss.galleon.util.CollectionUtils;

/**
 *
 * @author Alexey Loubyansky
 */
class FpStack {

    private static final int INHERIT_PKGS_FALSE = -1;
    private static final int INHERIT_PKGS_NOT_FOUND = 0;
    private static final int INHERIT_PKGS_TRANSITIVE = 1;
    private static final int INHERIT_PKGS_TRUE = 2;

    private static class Level {

        private List<FeaturePackConfig> fpConfigs = Collections.emptyList();
        private Map<ProducerSpec, FeaturePackConfig> transitive = Collections.emptyMap();
        private int currentFp = -1;

        void addFpConfig(FeaturePackConfig fpConfig) {
            fpConfigs = CollectionUtils.add(fpConfigs, fpConfig);
            if(fpConfig.isTransitive()) {
                transitive = CollectionUtils.put(transitive, fpConfig.getLocation().getProducer(), fpConfig);
            }
        }

        boolean hasNext() {
            return currentFp + 1 < fpConfigs.size();
        }

        FeaturePackConfig next() {
            if(!hasNext()) {
                throw new IndexOutOfBoundsException((currentFp + 1) + " exceeded " + fpConfigs.size());
            }
            return fpConfigs.get(++currentFp);
        }

        FeaturePackConfig getCurrentConfig() {
            return fpConfigs.get(currentFp);
        }

        Boolean isExcluded(ProducerSpec producer, ConfigId configId) {
            return FpStack.isExcluded(fpConfigs.get(currentFp), configId);
        }

        Boolean isIncludedInTransitive(ProducerSpec producer, ConfigId configId) {
            final FeaturePackConfig fpConfig = transitive.get(producer);
            return fpConfig == null ? null : FpStack.isIncluded(fpConfigs.get(currentFp), configId);
        }

        boolean isInheritConfigs() {
            return fpConfigs.get(currentFp).isInheritConfigs(true);
        }

        boolean isInheritModelOnlyConfigs() {
            return fpConfigs.get(currentFp).isInheritModelOnlyConfigs();
        }

        private Boolean getInheritPackages() {
            return fpConfigs.get(currentFp).getInheritPackages();
        }

        int isInheritPackages(ProducerSpec producer) {
            final FeaturePackConfig fpConfig = getFpConfig(producer);
            if(fpConfig == null) {
                return INHERIT_PKGS_NOT_FOUND;
            }
            if(!fpConfig.isInheritPackages(true)) {
                return INHERIT_PKGS_FALSE;
            }
            return fpConfig.isTransitive() ? INHERIT_PKGS_TRANSITIVE : INHERIT_PKGS_TRUE;
        }

        boolean isPackageExcluded(ProducerSpec producer, String packageName) {
            final FeaturePackConfig fpConfig = getFpConfig(producer);
            return fpConfig == null ? false : fpConfig.isPackageExcluded(packageName);
        }

        boolean isPackageIncluded(ProducerSpec producer, String packageName) {
            final FeaturePackConfig fpConfig = getFpConfig(producer);
            return fpConfig == null ? false : fpConfig.isPackageIncluded(packageName);
        }

        Boolean isPackageFilteredOut(ProducerSpec producer, String packageName) {
            FeaturePackConfig fpConfig = getFpConfig(producer);
            if (fpConfig == null) {
                return null;
            }
            if(fpConfig.isPackageExcluded(packageName)) {
                return true;
            }
            if(fpConfig.isPackageIncluded(packageName)) {
                return false;
            }
            final Boolean inheritPackages = fpConfig.getInheritPackages();
            return inheritPackages == null ? null : !inheritPackages;
        }

        private FeaturePackConfig getFpConfig(ProducerSpec producer) {
            for(int i = fpConfigs.size() - 1; i >= 0; --i) {
                final FeaturePackConfig fpConfig = fpConfigs.get(i);
                if(fpConfig.getLocation().getProducer().equals(producer)) {
                    return fpConfig;
                }
            }
            return null;
        }
    }

    private final ProvisioningConfig config;
    private List<Level> levels = new ArrayList<>();
    private Level lastPushed;

    FpStack(ProvisioningConfig config) {
        this.config = config;
    }

    boolean push(FeaturePackConfig fpConfig, boolean extendCurrentLevel) {
        if(!isRelevant(fpConfig)) {
            return false;
        }
        if(extendCurrentLevel) {
            lastPushed.addFpConfig(fpConfig);
            return true;
        }
        final Level newLevel = new Level();
        newLevel.addFpConfig(fpConfig);
        levels.add(newLevel);
        lastPushed = newLevel;
        return true;
    }

    void popLevel() {
        if(isEmpty()) {
            return;
        }
        if(levels.size() == 1) {
            levels.clear();
            lastPushed = null;
        } else {
            levels.remove(levels.size() - 1);
            lastPushed = levels.get(levels.size() - 1);
        }
    }

    boolean isEmpty() {
        return lastPushed == null;
    }

    boolean hasNext() {
        if(lastPushed == null) {
            return false;
        }
        return lastPushed.hasNext();
    }

    FeaturePackConfig next() {
        if(lastPushed == null) {
            throw new NoSuchElementException();
        }
        return lastPushed.next();
    }

    boolean isFilteredOut(ProducerSpec producer, ConfigId configId, boolean fromPrevLevel) {
        Boolean filteredOut = isFilteredOut(config, configId);
        if(filteredOut != null) {
            return filteredOut;
        }
        return isFilteredOutFromDeps(producer, configId, fromPrevLevel);
    }

    boolean isFilteredOutFromDeps(ProducerSpec producer, ConfigId configId, boolean fromPrevLevel) {
        final int last = levels.size() - (fromPrevLevel ? 1 : 0);
        int i = 0;
        int notInheritingLevel = Integer.MAX_VALUE;
        Boolean notInherited = null;
        stackIterator: while(i < last) {
            final Level level = levels.get(i);
            final FeaturePackConfig levelFpConfig = level.getCurrentConfig();
            final ProducerSpec levelProducer = levelFpConfig.getLocation().getProducer();
            for(int j = 0; j <= Math.min(i, notInheritingLevel); ++j) {
                final FeaturePackConfig transitiveFpConfig = levels.get(j).transitive.get(levelProducer);
                if(transitiveFpConfig == null) {
                    continue;
                }
                final Boolean excluded = isFilteredOut(transitiveFpConfig, configId);
                if (excluded != null) {
                    return excluded;
                }
                if (transitiveFpConfig.getInheritConfigs() != null) {
                    if(j <= notInheritingLevel) {
                        notInheritingLevel = j;
                        notInherited = !transitiveFpConfig.getInheritConfigs();
                    }
                    ++i;
                    continue stackIterator;
                }
            }

            final FeaturePackConfig transitiveFpConfig = level.transitive.get(producer);
            if(transitiveFpConfig != null) {
                final Boolean included = isIncluded(transitiveFpConfig, configId);
                if (included != null && included) {
                    return false;
                }
                if(transitiveFpConfig.getInheritConfigs() != null) {
                    ++i;
                    continue;
                }
            }
            if (notInherited == null) {
                notInherited = isExcluded(levelFpConfig, configId);
                notInheritingLevel = i;
            }
            ++i;
        }

        if(notInherited != null && notInherited) {
            return true;
        }

        stackIterator: while(i > 0) {
            final Level level = levels.get(--i);
            final ProducerSpec levelProducer = level.getCurrentConfig().getLocation().getProducer();
            for(int j = 0; j <= i; ++j) {
                final FeaturePackConfig fpConfig = levels.get(j).transitive.get(levelProducer);
                if(fpConfig == null) {
                    continue;
                }
                final Boolean included = isIncluded(fpConfig, configId);
                if (included != null && included) {
                    return false;
                }
                if (fpConfig.getInheritConfigs() != null) {
                    continue stackIterator;
                }
            }
            final FeaturePackConfig transitiveFpConfig = level.transitive.get(producer);
            if(transitiveFpConfig != null) {
                final Boolean included = isIncluded(transitiveFpConfig, configId);
                if (included != null && included) {
                    return false;
                }
                if(transitiveFpConfig.getInheritConfigs() != null) {
                    continue;
                }
            }

            final Boolean excluded = level.isExcluded(producer, configId);
            if(excluded != null) {
                return excluded;
            }
        }
        return false;
    }

    private static Boolean isFilteredOut(ConfigCustomizations configCustoms, ConfigId configId) {
        if(configId.isModelOnly()) {
            return configCustoms.isConfigModelExcluded(configId) || !configCustoms.isInheritModelOnlyConfigs();
        }

        if(configCustoms.isConfigExcluded(configId)) {
            return true;
        }
        if(configCustoms.isConfigIncluded(configId)) {
            return false;
        }
        if(configCustoms.isConfigModelExcluded(configId)) {
            if(configCustoms.isConfigIncluded(configId)) {
                return false;
            }
            return true;
        }
        if(configCustoms.isConfigModelIncluded(configId)) {
            if(configCustoms.isConfigExcluded(configId)) {
                return true;
            }
            return false;
        }

        final Boolean inheritConfigs = configCustoms.getInheritConfigs();
        return inheritConfigs == null || inheritConfigs ? null : true;
    }

    boolean isIncludedInTransitiveDeps(ProducerSpec producer, ConfigId configId) {
        final int end = levels.size() - 1;
        int i = 0;
        while(i < end) {
            final Level level = levels.get(i++);
            final ProducerSpec levelProducer = level.getCurrentConfig().getLocation().getProducer();
            for(int j = 0; j <= i; ++j) {
                final Boolean included = levels.get(j).isIncludedInTransitive(levelProducer, configId);
                if(included != null) {
                    return included;
                }
            }
        }
        return false;
    }

    private static Boolean isIncluded(ConfigCustomizations configCustoms, ConfigId configId) {
        if(configId.isModelOnly()) {
            return configCustoms.isConfigModelIncluded(configId) || configCustoms.isInheritModelOnlyConfigs();
        }
        if(configCustoms.isConfigIncluded(configId)) {
            return true;
        }
        if (configCustoms.isConfigExcluded(configId)) {
            return false;
        }
        if(configCustoms.isConfigModelIncluded(configId)) {
            if(configCustoms.isConfigExcluded(configId)) {
                return false;
            }
            return true;
        }
        if (configCustoms.isConfigModelExcluded(configId)) {
            if (configCustoms.isConfigIncluded(configId)) {
                return true;
            }
            return false;
        }
        final Boolean inheritConfigs = configCustoms.getInheritConfigs();
        return inheritConfigs == null || inheritConfigs ? null : false;
    }

    private static Boolean isExcluded(ConfigCustomizations configCustoms, ConfigId configId) {
        if(configId.isModelOnly()) {
            return configCustoms.isConfigModelExcluded(configId) || !configCustoms.isInheritModelOnlyConfigs();
        }
        if(configCustoms.isConfigIncluded(configId)) {
            return false;
        }
        if (configCustoms.isConfigExcluded(configId)) {
            return true;
        }
        if(configCustoms.isConfigModelIncluded(configId)) {
            if(configCustoms.isConfigExcluded(configId)) {
                return true;
            }
            return false;
        }
        if (configCustoms.isConfigModelExcluded(configId)) {
            if (configCustoms.isConfigIncluded(configId)) {
                return false;
            }
            return true;
        }
        final Boolean inheritConfigs = configCustoms.getInheritConfigs();
        return inheritConfigs == null || inheritConfigs ? null : true;
    }

    private boolean isRelevant(FeaturePackConfig fpConfig) {
        if(isEmpty()) {
            return true;
        }
        final ProducerSpec producer = fpConfig.getLocation().getProducer();
        final int inheritPkgs = isInheritPackages(producer);
        if(inheritPkgs == INHERIT_PKGS_NOT_FOUND || inheritPkgs == INHERIT_PKGS_TRANSITIVE && !fpConfig.isTransitive()) {
            return true;
        }
        if(inheritPkgs > 0) {
            if(fpConfig.hasExcludedPackages()) {
                for(String excluded : fpConfig.getExcludedPackages()) {
                    if(!isPackageExcluded(producer, excluded) && !isPackageIncluded(producer, excluded)) {
                        return true;
                    }
                }
            }
            if(fpConfig.hasIncludedPackages()) {
                for(String included : fpConfig.getIncludedPackages()) {
                    if(!isPackageIncluded(producer, included) && !isPackageExcluded(producer, included)) {
                        return true;
                    }
                }
            }
        }

        if (fpConfig.hasDefinedConfigs()) {
            boolean configsInherited = true;
            for(int i = levels.size() - 1; i >= 0; --i) {
                if(!levels.get(i).isInheritConfigs()) {
                    configsInherited = false;
                    break;
                }
            }
            if (configsInherited) {
                return true;
            }
        }

        if (fpConfig.hasModelOnlyConfigs()) {
            boolean configsInherited = true;
            for(int i = levels.size() - 1; i >= 0; --i) {
                if(!levels.get(i).isInheritModelOnlyConfigs()) {
                    configsInherited = false;
                    break;
                }
            }
            if (configsInherited) {
                return true;
            }
        }
        return false;
    }

    private int isInheritPackages(ProducerSpec producer) {
        int result = INHERIT_PKGS_NOT_FOUND;
        for(int i = levels.size() - 1; i >= 0; --i) {
            final int levelResult = levels.get(i).isInheritPackages(producer);
            if(levelResult < 0) {
                return levelResult;
            }
            if(levelResult > result) {
                result = levelResult;
            }
        }
        return result;
    }

    boolean isPackageExcluded(ProducerSpec producer, String packageName) {
        for(int i = levels.size() - 1; i >= 0; --i) {
            if(levels.get(i).isPackageExcluded(producer, packageName)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPackageIncluded(ProducerSpec producer, String packageName) {
        for(int i = levels.size() - 1; i >= 0; --i) {
            if(levels.get(i).isPackageIncluded(producer, packageName)) {
                return true;
            }
        }
        return false;
    }

    boolean isPackageFilteredOut(ProducerSpec producer, String packageName) {
        final int levelsTotal = levels.size();
        if(levelsTotal == 0) {
            return false;
        }
        Level level = levels.get(0);
        Boolean filteredOut = level.isPackageFilteredOut(producer, packageName);
        if(filteredOut != null) {
            return filteredOut;
        }
        if(levelsTotal == 1) {
            return false;
        }
        Boolean inheritPackages = level.getInheritPackages();
        stackIterator: for(int i = 1; i < levelsTotal; ++i) {
            level = levels.get(i);
            final ProducerSpec currentFpProducer = level.getCurrentConfig().getLocation().getProducer();

            for(int j = 0; j < i; ++j) {
                final FeaturePackConfig transitiveFpConfig = levels.get(j).transitive.get(currentFpProducer);
                if(transitiveFpConfig == null) {
                    continue;
                }

                final Boolean transitiveInheritPackages = transitiveFpConfig.getInheritPackages();
                if (transitiveInheritPackages != null) {
                    if (!transitiveInheritPackages) {
                        return true;
                    }
                    continue stackIterator;
                }
                break;
            }

            if(inheritPackages != null && !inheritPackages) {
                return true;
            }
            filteredOut = level.isPackageFilteredOut(producer, packageName);
            if(filteredOut != null) {
                return filteredOut;
            }
            inheritPackages = level.getInheritPackages();
        }
        return false;
    }
}
