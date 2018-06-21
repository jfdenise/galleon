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
package org.jboss.galleon.cli.cmd.universe;

import java.util.Collection;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.utils.Config;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.cli.PmCommandInvocation;
import org.jboss.galleon.cli.Universe;
import org.jboss.galleon.cli.Universe.StreamLocation;
import org.jboss.galleon.universe.Channel;
import org.jboss.galleon.universe.Producer;
import org.jboss.galleon.universe.UniverseResolver;
import org.jboss.galleon.universe.UniverseSpec;
import org.jboss.galleon.universe.maven.MavenChannel;
import org.jboss.galleon.universe.maven.MavenProducer;
import org.jboss.galleon.universe.maven.MavenUniverse;
import org.jboss.galleon.universe.maven.MavenUniverseException;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "list", description = "List universes and products")
public class UniverseListCommand implements Command<PmCommandInvocation> {

    @Override
    public CommandResult execute(PmCommandInvocation commandInvocation) throws CommandException, InterruptedException {
        for (Universe universe : commandInvocation.getPmSession().getUniverses().getUniverses()) {
            commandInvocation.println("[LEGACY] Universe " + universe.getLocation().getName()
                    + ", coordinates " + universe.getLocation().getCoordinates());
            for (StreamLocation loc : universe.getStreamLocations()) {
                commandInvocation.println("   " + loc.getName() + ", coordinates "
                        + loc.getCoordinates() + ", version range " + loc.getVersionRange());
            }
        }
        for (UniverseSpec universe : commandInvocation.getPmSession().getUniverseResolver().getUniverses()) {
            printUniverse(universe, commandInvocation);
        }

        return CommandResult.SUCCESS;
    }

    private void printUniverse(UniverseSpec spec, PmCommandInvocation invoc) throws CommandException {
        try {
            UniverseResolver resolver = invoc.getPmSession().getUniverseResolver();
            org.jboss.galleon.universe.Universe universe = resolver.getUniverse(spec);
            if (universe instanceof MavenUniverse) {
                printMavenUniverse(spec, (MavenUniverse) universe, invoc);
            } else {
                printGenericUniverse(spec, universe, invoc);
            }
        } catch (Exception ex) {
            throw new CommandException(ex);
        }
    }

    private void printMavenUniverse(UniverseSpec spec, MavenUniverse universe, PmCommandInvocation invoc) throws MavenUniverseException {
        invoc.println(Config.getLineSeparator() + spec.getFactory() + "/" + spec.getLocation());
        Collection<MavenProducer> producers = universe.getProducers();
        if (producers.isEmpty()) {
            invoc.println("No product available");
        } else {
            for (MavenProducer producer : producers) {
                invoc.println("Product: " + producer.getName() + ", artefact " + producer.getFeaturePackGroupId() + ":" + producer.getFeaturePackArtifactId());
                invoc.println(" Releases ");
                for (MavenChannel channel : producer.getChannels()) {
                    for (String freq : channel.getFrequencies()) {
                        invoc.println(producer.getName() + ":" + channel.getName() + "/" + freq + ", version range " + channel.getVersionRange());
                    }
                }
            }
        }
    }

    private void printGenericUniverse(UniverseSpec spec, org.jboss.galleon.universe.Universe<?> universe, PmCommandInvocation invoc) throws ProvisioningException {
        invoc.println(Config.getLineSeparator() + spec.getFactory() + "/" + spec.getLocation());
        Collection<?> producers = universe.getProducers();
        if (producers.isEmpty()) {
            invoc.println("No product available");
        } else {
            for (Producer<?> producer : universe.getProducers()) {
                invoc.println("Product: " + producer.getName());
                invoc.println(" Releases ");
                for (Channel channel : producer.getChannels()) {
                    invoc.println(producer.getName() + ":" + channel.getName());
                }
            }
        }
    }

}
