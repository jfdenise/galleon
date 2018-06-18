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

package org.jboss.galleon.universe.resolver;

import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.creator.FeaturePackCreator;
import org.jboss.galleon.state.ProvisionedFeaturePack;
import org.jboss.galleon.state.ProvisionedState;
import org.jboss.galleon.test.util.fs.state.DirState;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.FeaturePackLocation.FPID;
import org.jboss.galleon.universe.ProvisionConfigMvnTestBase;
import org.jboss.galleon.universe.UniverseSpec;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenUniverseFactory;

/**
 *
 * @author Alexey Loubyansky
 */
public class UniverseResolverInFeaturePackDepsTestCase extends ProvisionConfigMvnTestBase {

    private static final FeaturePackLocation FP1_FPL = FeaturePackLocation.fromString("producer1@universe1:1.0#1.0.0.Final");
    private static final FeaturePackLocation FP2_FPL = FeaturePackLocation.fromString("producer2@universe2:1.0#1.0.0.Final");

    private MavenArtifact universe1Art;
    private MavenArtifact universe2Art;
    private FPID resolvedFp1Fpl;
    private FPID resolvedFp2Fpl;

    @Override
    protected void createFeaturePacks(FeaturePackCreator creator) throws ProvisioningException {

        universe1Art = createUniverse("universe1", createProducer("producer1", "fp1"));
        universe2Art = createUniverse("universe2", createProducer("producer2", "fp1"));

        resolvedFp1Fpl = FeaturePackLocation
                .fromString("producer1@" + new UniverseSpec(MavenUniverseFactory.ID, universe1Art.getCoordsAsString()) + ':'
                        + FP1_FPL.getChannelName() + '#' + FP1_FPL.getBuild())
                .getFPID();
        resolvedFp2Fpl = FeaturePackLocation
                .fromString("producer2@" + new UniverseSpec(MavenUniverseFactory.ID, universe2Art.getCoordsAsString()) + ':'
                        + FP1_FPL.getChannelName() + '#' + FP1_FPL.getBuild())
                .getFPID();

        creator
        .newFeaturePack()
                .setFPID(resolvedFp1Fpl)
                .addUniverse("universe2", MavenUniverseFactory.ID, universe2Art.getCoordsAsString())
            .addDependency(FP2_FPL)
            .newPackage("p1", true)
                .writeContent("fp1/p1.txt", "fp1 p1")
                .getFeaturePack()
        .getCreator()
        .newFeaturePack()
            .setFPID(resolvedFp2Fpl)
            .newPackage("p1", true)
                .writeContent("fp2/p1.txt", "fp2 p1")
                .getFeaturePack()
        .getCreator()
        .install();
    }

    @Override
    protected ProvisioningConfig provisioningConfig() throws ProvisioningException {
        return ProvisioningConfig.builder()
                .addUniverse("universe1", MavenUniverseFactory.ID, universe1Art.getCoordsAsString())
                .addFeaturePackDep(FP1_FPL)
                .build();
    }

    @Override
    protected ProvisionedState provisionedState() throws ProvisioningException {
        return ProvisionedState.builder()
                .addFeaturePack(ProvisionedFeaturePack.builder(resolvedFp1Fpl)
                        .addPackage("p1")
                        .build())
                .addFeaturePack(ProvisionedFeaturePack.builder(resolvedFp2Fpl)
                        .addPackage("p1")
                        .build())
                .build();
    }

    @Override
    protected DirState provisionedHomeDir() {
        return newDirBuilder()
                .addFile("fp1/p1.txt", "fp1 p1")
                .addFile("fp2/p1.txt", "fp2 p1")
                .build();
    }
}