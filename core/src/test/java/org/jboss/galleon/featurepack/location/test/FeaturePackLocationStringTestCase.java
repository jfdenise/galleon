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

package org.jboss.galleon.featurepack.location.test;

import org.jboss.galleon.FeaturePackLocation;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class FeaturePackLocationStringTestCase {

    @Test
    public void testCompleteLocationToString() throws Exception {
        final FeaturePackLocation originalCoords = new FeaturePackLocation("universe", "producer", "channel", "frequency", "build");
        Assert.assertEquals("producer@universe:channel/frequency#build", originalCoords.toString());
    }

    @Test
    public void testCompleteLocationFromString() throws Exception {
        final FeaturePackLocation parsedCoords = FeaturePackLocation.fromString("producer@universe:channel/frequency#build");
        Assert.assertNotNull(parsedCoords);
        Assert.assertEquals("universe", parsedCoords.getUniverse());
        Assert.assertEquals("producer", parsedCoords.getProducer());
        Assert.assertEquals("channel", parsedCoords.getChannelName());
        Assert.assertEquals("frequency", parsedCoords.getFrequency());
        Assert.assertEquals("build", parsedCoords.getBuild());
    }

    @Test
    public void testLocationWithoutUniverseToString() throws Exception {
        final FeaturePackLocation originalCoords = new FeaturePackLocation(null, "producer", "channel", "frequency", "build");
        Assert.assertEquals("producer:channel/frequency#build", originalCoords.toString());
    }

    @Test
    public void testLocationWithoutUniverseFromString() throws Exception {
        final FeaturePackLocation parsedCoords = FeaturePackLocation.fromString("producer:channel/frequency#build");
        Assert.assertNotNull(parsedCoords);
        Assert.assertNull(parsedCoords.getUniverse());
        Assert.assertEquals("producer", parsedCoords.getProducer());
        Assert.assertEquals("channel", parsedCoords.getChannelName());
        Assert.assertEquals("frequency", parsedCoords.getFrequency());
        Assert.assertEquals("build", parsedCoords.getBuild());
    }

    @Test
    public void testChannelWithUniverseAndFrequencyToString() throws Exception {
        final FeaturePackLocation.Channel channel = new FeaturePackLocation("universe", "producer", "channel", "frequency", "build").getChannel();
        Assert.assertEquals("producer@universe:channel", channel.toString());
    }

    @Test
    public void testChannelWithUniverseAndFrequencyFromString() throws Exception {
        final FeaturePackLocation parsedCoords = FeaturePackLocation.fromString("producer@universe:channel/frequency");
        Assert.assertNotNull(parsedCoords);
        Assert.assertEquals("universe", parsedCoords.getUniverse());
        Assert.assertEquals("producer", parsedCoords.getProducer());
        Assert.assertEquals("channel", parsedCoords.getChannelName());
        Assert.assertEquals("frequency", parsedCoords.getFrequency());
        Assert.assertNull(parsedCoords.getBuild());
    }

    @Test
    public void testChannelWithFrequencyNoUniverseToString() throws Exception {
        final FeaturePackLocation.Channel channel = new FeaturePackLocation(null, "producer", "channel", "frequency", "build").getChannel();
        Assert.assertEquals("producer:channel", channel.toString());
    }

    @Test
    public void testChannelWithFrequencyNoUniverseFromString() throws Exception {
        final FeaturePackLocation parsedCoords = FeaturePackLocation.fromString("producer:channel/frequency");
        Assert.assertNotNull(parsedCoords);
        Assert.assertNull(parsedCoords.getUniverse());
        Assert.assertEquals("producer", parsedCoords.getProducer());
        Assert.assertEquals("channel", parsedCoords.getChannelName());
        Assert.assertEquals("frequency", parsedCoords.getFrequency());
        Assert.assertNull(parsedCoords.getBuild());
    }

    @Test
    public void testChannelWithoutFrequencyAndUniverseToString() throws Exception {
        final FeaturePackLocation.Channel channel = new FeaturePackLocation(null, "producer", "channel", null, "build").getChannel();
        Assert.assertEquals("producer:channel", channel.toString());
    }

    @Test
    public void testChannelWithoutFrequencyAndUniverseFromString() throws Exception {
        final FeaturePackLocation parsedCoords = FeaturePackLocation.fromString("producer:channel");
        Assert.assertNotNull(parsedCoords);
        Assert.assertNull(parsedCoords.getUniverse());
        Assert.assertEquals("producer", parsedCoords.getProducer());
        Assert.assertEquals("channel", parsedCoords.getChannelName());
        Assert.assertNull(parsedCoords.getFrequency());
        Assert.assertNull(parsedCoords.getBuild());
    }

    @Test
    public void testFeaturePackIdWithUniverseToString() throws Exception {
        final FeaturePackLocation.FPID fpid = new FeaturePackLocation("universe", "producer", "channel", "frequency", "build").getFPID();
        Assert.assertEquals("producer@universe:channel#build", fpid.toString());
    }

    @Test
    public void testFeaturePackIdWithUniverseFromString() throws Exception {
        final FeaturePackLocation parsedCoords = FeaturePackLocation.fromString("producer@universe:channel#build");
        Assert.assertNotNull(parsedCoords);
        Assert.assertEquals("universe", parsedCoords.getUniverse());
        Assert.assertEquals("producer", parsedCoords.getProducer());
        Assert.assertEquals("channel", parsedCoords.getChannelName());
        Assert.assertNull(parsedCoords.getFrequency());
        Assert.assertEquals("build", parsedCoords.getBuild());
    }

    @Test
    public void testFeaturePackIdWithoutUniverseToString() throws Exception {
        final FeaturePackLocation.FPID fpid = new FeaturePackLocation(null, "producer", "channel", "frequency", "build").getFPID();
        Assert.assertEquals("producer:channel#build", fpid.toString());
    }

    @Test
    public void testFeaturePackIdWithoutUniverseFromString() throws Exception {
        final FeaturePackLocation parsedCoords = FeaturePackLocation.fromString("producer:channel#build");
        Assert.assertNotNull(parsedCoords);
        Assert.assertNull(parsedCoords.getUniverse());
        Assert.assertEquals("producer", parsedCoords.getProducer());
        Assert.assertEquals("channel", parsedCoords.getChannelName());
        Assert.assertNull(parsedCoords.getFrequency());
        Assert.assertEquals("build", parsedCoords.getBuild());
    }

    @Test
    public void testUniverseLocationInFeaturePackLocation() throws Exception {
        final FeaturePackLocation parsedCoords = FeaturePackLocation.fromString("wildfly@maven/org.jboss.galleon-universe:jboss-galleon-universe:1.0.0.Final:14/beta#14.0.0.Beta1-SNAPSHOT");
        Assert.assertNotNull(parsedCoords);
        Assert.assertEquals("maven/org.jboss.galleon-universe:jboss-galleon-universe:1.0.0.Final", parsedCoords.getUniverse());
        Assert.assertEquals("wildfly", parsedCoords.getProducer());
        Assert.assertEquals("14", parsedCoords.getChannelName());
        Assert.assertEquals("beta", parsedCoords.getFrequency());
        Assert.assertEquals("14.0.0.Beta1-SNAPSHOT", parsedCoords.getBuild());
    }

    @Test
    public void testGroupIdArtifactIdAsGalleon1Producer() throws Exception {
        final FeaturePackLocation parsedCoords = FeaturePackLocation.fromString("org.wildfly:wildfly-galleon-pack@galleon1:14/beta#14.0.0.Beta1-SNAPSHOT");
        Assert.assertNotNull(parsedCoords);
        Assert.assertEquals("galleon1", parsedCoords.getUniverse());
        Assert.assertEquals("org.wildfly:wildfly-galleon-pack", parsedCoords.getProducer());
        Assert.assertEquals("14", parsedCoords.getChannelName());
        Assert.assertEquals("beta", parsedCoords.getFrequency());
        Assert.assertEquals("14.0.0.Beta1-SNAPSHOT", parsedCoords.getBuild());
    }

    @Test
    public void testGroupIdArtifactIdAsGalleon1ProducerDefaultUniverse() throws Exception {
        final FeaturePackLocation parsedCoords = FeaturePackLocation.fromString("org.wildfly:wildfly-galleon-pack:14#14.0.0.Beta1-SNAPSHOT");
        Assert.assertNotNull(parsedCoords);
        Assert.assertNull(parsedCoords.getUniverse());
        Assert.assertEquals("org.wildfly:wildfly-galleon-pack", parsedCoords.getProducer());
        Assert.assertEquals("14", parsedCoords.getChannelName());
        Assert.assertNull(parsedCoords.getFrequency());
        Assert.assertEquals("14.0.0.Beta1-SNAPSHOT", parsedCoords.getBuild());
    }
}
