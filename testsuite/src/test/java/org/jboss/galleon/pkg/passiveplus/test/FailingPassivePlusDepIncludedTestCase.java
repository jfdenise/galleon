/*
 * Copyright 2016-2020 Red Hat, Inc. and/or its affiliates
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
package org.jboss.galleon.pkg.passiveplus.test;

import org.jboss.galleon.Constants;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningOption;
import org.jboss.galleon.config.ConfigModel;
import org.jboss.galleon.config.FeatureConfig;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.creator.FeaturePackCreator;
import org.jboss.galleon.runtime.ResolvedFeatureId;
import org.jboss.galleon.spec.FeatureParameterSpec;
import org.jboss.galleon.spec.FeatureSpec;
import org.jboss.galleon.spec.PackageDependencySpec;
import org.jboss.galleon.state.ProvisionedFeaturePack;
import org.jboss.galleon.state.ProvisionedState;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.MvnUniverse;
import org.jboss.galleon.universe.ProvisionFromUniverseTestBase;
import org.jboss.galleon.xml.ProvisionedConfigBuilder;
import org.jboss.galleon.xml.ProvisionedFeatureBuilder;

/**
 *
 * @author Alexey Loubyansky
 */
public class FailingPassivePlusDepIncludedTestCase extends ProvisionFromUniverseTestBase {

    private FeaturePackLocation prod1;

    @Override
    protected void createProducers(MvnUniverse universe) throws ProvisioningException {
        universe.createProducer("prod1");
    }

    @Override
    protected void createFeaturePacks(FeaturePackCreator creator) throws ProvisioningException {

        prod1 = newFpl("prod1", "1", "1.0.0.Final");

        creator.newFeaturePack()
                .setFPID(prod1.getFPID())
                .addFeatureSpec(FeatureSpec.builder().addPackageDep("p1", PackageDependencySpec.PASSIVE).
                        setName("feat1").addParam(FeatureParameterSpec.createId("param")).build())
                .addFeatureSpec(FeatureSpec.builder().addPackageDep("p2", PackageDependencySpec.OPTIONAL).
                        setName("feat2").addParam(FeatureParameterSpec.createId("param")).build())
                .newPackage("p1", false)
                .addDependency(PackageDependencySpec.required("p2"))
                .getFeaturePack()
                .newPackage("p2", false)
                .getFeaturePack();

    }

    @Override
    protected ProvisioningConfig provisioningConfig() throws ProvisioningException {
        return ProvisioningConfig.builder()
                .addOption(ProvisioningOption.OPTIONAL_PACKAGES.getName(), Constants.PASSIVE_PLUS)
                .addFeaturePackDep(FeaturePackConfig.builder(prod1).build())
                .addConfig(ConfigModel.builder().setModel("model1").
                        setName("name1").
                        addFeature(new FeatureConfig("feat1").setParam("param", "1")).
                        addFeature(new FeatureConfig("feat2").setParam("param", "1")).build())
                .build();
    }

    @Override
    protected ProvisionedState provisionedState() throws ProvisioningException {
        return ProvisionedState.builder()
                .addFeaturePack(ProvisionedFeaturePack.builder(prod1.getFPID())
                        .addPackage("p1")
                        .addPackage("p2")
                        .build())
                .addConfig(ProvisionedConfigBuilder.builder().setModel("model1").setName("name1").
                        addFeature(ProvisionedFeatureBuilder.builder(ResolvedFeatureId.create(prod1.getProducer(), "feat1", "param", "1")).build())
                        .addFeature(ProvisionedFeatureBuilder.builder(ResolvedFeatureId.create(prod1.getProducer(), "feat2", "param", "1")).build())
                        .build())
                .build();
    }
}