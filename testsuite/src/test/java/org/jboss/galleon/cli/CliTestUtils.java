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
package org.jboss.galleon.cli;

import java.nio.file.Path;
import java.util.List;
import org.aesh.command.CommandException;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.config.ConfigModel;
import org.jboss.galleon.config.FeatureConfig;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.creator.FeaturePackCreator;
import org.jboss.galleon.plugin.InstallPlugin;
import org.jboss.galleon.plugin.StateDiffPlugin;
import org.jboss.galleon.spec.ConfigLayerSpec;
import org.jboss.galleon.spec.FeatureParameterSpec;
import org.jboss.galleon.spec.FeatureSpec;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.FeaturePackLocation.FPID;
import org.jboss.galleon.universe.LatestVersionNotAvailableException;
import org.jboss.galleon.universe.MvnUniverse;
import org.jboss.galleon.universe.TestConstants;
import org.jboss.galleon.universe.UniverseSpec;
import org.jboss.galleon.universe.maven.MavenLatestVersionNotAvailableException;
import org.jboss.galleon.universe.maven.MavenUniverseFactory;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.userchanges.persist.test.BasicStateDiffPlugin;
import org.jboss.galleon.userchanges.persist.test.TestConfigsPersistingPlugin;
import org.junit.Assert;

/**
 *
 * @author jdenise@redhat.com
 */
public abstract class CliTestUtils {

    public static final String PRODUCER1 = "producer1";
    public static final String PRODUCER2 = "producer2";
    public static final String PRODUCER3 = "producer3";
    public static final String PRODUCER4 = "producer4";
    public static final String PRODUCER5 = "producer5";
    public static final String UNIVERSE_NAME = "cli-test-universe";

    public static ProvisioningConfig getConfig(Path dir) throws ProvisioningException {
        return ProvisioningManager.builder().setInstallationHome(dir).build().getProvisioningConfig();
    }

    public static FeaturePackLocation buildFPL(UniverseSpec universeSpec,
            String producer, String channel, String frq, String build) {
        return new FeaturePackLocation(universeSpec, producer, channel, frq, build);
    }

    public static UniverseSpec setupUniverse(CliWrapper cli, String name, List<String> producers) throws ProvisioningException {
        MavenRepoManager mgr = cli.getSession().getMavenRepoManager();
        MvnUniverse universe = MvnUniverse.getInstance(name, mgr);
        return setupUniverse(universe, cli, name, producers);
    }

    public static UniverseSpec setupUniverse(MvnUniverse universe, CliWrapper cli, String name, List<String> producers) throws ProvisioningException {
        for (String p : producers) {
            universe.createProducer(p);
        }
        universe.install();
        return new UniverseSpec(MavenUniverseFactory.ID, TestConstants.GROUP_ID + ":" + name);
    }

    public static UniverseSpec setupUniverse(MvnUniverse universe, CliWrapper cli, String name,
            List<String> producers, String defaultFrequency) throws ProvisioningException {
        for (String p : producers) {
            universe.createProducer(p, p + "-feature-pack", defaultFrequency);
        }
        universe.install();
        return new UniverseSpec(MavenUniverseFactory.ID, TestConstants.GROUP_ID + ":" + name);
    }

    public static void install(CliWrapper cli, UniverseSpec universeSpec,
            String producer, String version) throws ProvisioningException {
        FeaturePackCreator creator = FeaturePackCreator.getInstance().addArtifactResolver(cli.getSession().getMavenRepoManager());
        FeaturePackLocation fp1 = new FeaturePackLocation(universeSpec,
                producer, "1", null, version);
        creator.newFeaturePack(fp1.getFPID())
                .newPackage("p1", true)
                .writeContent(producer + "/p1.txt", "fp1 p1");
        creator.install();
    }

    public static void installWithLayers(CliWrapper cli, UniverseSpec universeSpec,
            String producer, String version) throws ProvisioningException {
        FeaturePackCreator creator = FeaturePackCreator.getInstance().addArtifactResolver(cli.getSession().getMavenRepoManager());
        FeaturePackLocation fp1 = new FeaturePackLocation(universeSpec,
                producer, "1", null, version);
        creator.newFeaturePack(fp1.getFPID())
                .addFeatureSpec(FeatureSpec.builder(producer + "-FeatureA")
                        .addParam(FeatureParameterSpec.createId("id"))
                        .build())
                .addConfigLayer(ConfigLayerSpec.builder()
                        .setModel("testmodel").setName("base-" + producer)
                        .addFeature(new FeatureConfig(producer + "-FeatureA").setParam("id", "base"))
                        .build())
                .addConfigLayer(ConfigLayerSpec.builder()
                        .setModel("testmodel").setName("layerA-" + producer)
                        .addLayerDep("base-" + producer)
                        .build())
                .addConfigLayer(ConfigLayerSpec.builder()
                        .setModel("testmodel").setName("layerB-" + producer)
                        .addLayerDep("layerA-" + producer)
                        .build())
                .addConfigLayer(ConfigLayerSpec.builder()
                        .setModel("testmodel").setName("layerC-" + producer)
                        .build())
                .addConfigLayer(ConfigLayerSpec.builder()
                        .setModel("testmodel").setName("layerD-" + producer)
                        .build())
                .addConfig(ConfigModel.builder("testmodel", "config1.xml").
                        includeLayer("layerB-" + producer).build(), true)
                .addConfig(ConfigModel.builder("testmodel", "config2.xml").
                        includeLayer("layerD-" + producer).build(), true)
                .newPackage("p1", true)
                        .writeContent(producer + "/p1.txt", "fp1 p1")
                        .getFeaturePack()
                .addService(StateDiffPlugin.class, BasicStateDiffPlugin.class)
                .addPlugin(TestConfigsPersistingPlugin.class);
        creator.install();
    }

    public static void install(CliWrapper cli, UniverseSpec universeSpec,
            String producer, String version, Class<? extends InstallPlugin> plugin) throws ProvisioningException {
        FeaturePackCreator creator = FeaturePackCreator.getInstance().addArtifactResolver(cli.getSession().getMavenRepoManager());
        FeaturePackLocation fp1 = new FeaturePackLocation(universeSpec,
                producer, "1", null, version);
        creator.newFeaturePack(fp1.getFPID())
                .addPlugin(plugin)
                .newPackage("p1", true)
                .writeContent("fp1/p1.txt", "fp1 p1");
        creator.install();
    }

    public static void legacyInstall(CliWrapper cli, Path directory, FeaturePackLocation loc) throws ProvisioningException {
        FeaturePackCreator creator = new FeaturePackCreator();
        creator.newFeaturePack(loc.getFPID())
                .newPackage("p1", true)
                .writeContent("fp1/p1.txt", "fp1 p1");
        creator.install(directory);
    }

    public static FPID installPatch(CliWrapper cli, UniverseSpec universeSpec,
            String producer, String version, String qualifier, Path directory) throws ProvisioningException {
        FeaturePackCreator creator = FeaturePackCreator.getInstance().addArtifactResolver(cli.getSession().getMavenRepoManager());
        FeaturePackLocation fp1 = new FeaturePackLocation(universeSpec,
                producer, "1", null, version + "-patch-" + qualifier);
        creator.newFeaturePack(fp1.getFPID())
                .setPatchFor(buildFPL(universeSpec, producer, "1", null, version + "." + qualifier).getFPID())
                .newPackage("p1", true)
                .writeContent("fp1/p1.txt", "fp1 p1 patch");
        creator.install(directory);
        return fp1.getFPID();
    }

    public static Path installAndCheck(CliWrapper cli, String dirName, FeaturePackLocation toInstall, FeaturePackLocation expected) throws Exception {
        Path dir = cli.newDir(dirName, false);
        cli.execute("install " + toInstall + " --dir=" + dir.toString());
        Assert.assertTrue(dir.toFile().exists());
        ProvisioningConfig config = CliTestUtils.getConfig(dir);
        FeaturePackConfig cf = config.getFeaturePackDep(toInstall.getProducer());
        Assert.assertEquals(cf.getLocation().toString(), expected, cf.getLocation());
        return dir;
    }

    public static Path installAndCheck(CliWrapper cli, String dirName, Path toInstall) throws Exception {
        Path dir = cli.newDir(dirName, false);
        cli.execute("install --file=" + toInstall + " --dir=" + dir.toString());
        Assert.assertTrue(dir.toFile().exists());
        return dir;
    }

    public static void checkNoVersionAvailable(CliWrapper cli, FeaturePackLocation toInstall, FeaturePackLocation expected) throws Exception {
        Path dir = cli.newDir("install" + System.currentTimeMillis(), false);
        FeaturePackLocation loc = null;
        try {
            cli.execute("install " + toInstall + " --dir=" + dir.toString());
            throw new Exception("Install should have failed");
        } catch (CommandException ex) {
            if (ex.getCause() instanceof CommandExecutionException) {
                if (ex.getCause().getCause() != null) {
                    if (ex.getCause().getCause() instanceof LatestVersionNotAvailableException) {
                        LatestVersionNotAvailableException lex = (LatestVersionNotAvailableException) ex.getCause().getCause();
                        loc = lex.getLocation();
                    }
                }
            } else {
                if (ex.getCause() instanceof MavenLatestVersionNotAvailableException) {
                    MavenLatestVersionNotAvailableException mex = (MavenLatestVersionNotAvailableException) ex.getCause();
                    loc = mex.getLocation();
                }
            }
        }
        if (loc == null) {
            throw new Exception("Expected exception not found");
        }
        Assert.assertEquals(loc.toString(), expected, loc);
        Assert.assertFalse(dir.toFile().exists());
    }
}
