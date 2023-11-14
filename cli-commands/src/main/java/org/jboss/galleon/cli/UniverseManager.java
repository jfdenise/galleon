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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import javax.xml.stream.XMLStreamException;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.cli.config.Configuration;
import org.jboss.galleon.cli.config.mvn.MavenConfig;
import org.jboss.galleon.cli.config.mvn.MavenConfig.MavenChangeListener;
import org.jboss.galleon.universe.Channel;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.Producer;
import org.jboss.galleon.universe.Universe;
import org.jboss.galleon.universe.UniverseFactoryLoader;
import org.jboss.galleon.universe.UniverseResolver;
import org.jboss.galleon.universe.UniverseSpec;
import org.jboss.galleon.universe.maven.MavenProducer;
import org.jboss.galleon.universe.maven.MavenUniverse;
import org.jboss.galleon.universe.maven.MavenUniverseFactory;

/**
 *
 * @author jdenise@redhat.com
 */
public class UniverseManager implements MavenChangeListener {

    public interface UniverseVisitor {

        void visit(Producer<?> producer, FeaturePackLocation loc);

        void exception(UniverseSpec spec, Exception ex);
    }

    public static final String JBOSS_UNIVERSE_GROUP_ID = "org.jboss.universe";
    public static final String JBOSS_UNIVERSE_ARTIFACT_ID = "community-universe";

    private final ExecutorService executorService = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread thr = new Thread(r, "Galleon CLI universe initializer");
            thr.setDaemon(true);
            return thr;
        }
    });
    private MavenUniverse builtinUniverse;
    private final UniverseSpec builtinUniverseSpec;
    private final UniverseResolver universeResolver;
    private final PmSession pmSession;
    private final List<Future<?>> submited = new ArrayList<>();
    private volatile boolean closed;

    private boolean bckResolution = true;

    UniverseManager(PmSession pmSession, Configuration config, CliMavenArtifactRepositoryManager maven,
            UniverseResolver universeResolver, UniverseSpec builtin) throws ProvisioningException {
        this.pmSession = pmSession;
        config.getMavenConfig().addListener(this);
        UniverseFactoryLoader.getInstance().addArtifactResolver(maven);
        this.universeResolver = universeResolver;
        builtinUniverseSpec = builtin == null ? new UniverseSpec(MavenUniverseFactory.ID,
                JBOSS_UNIVERSE_GROUP_ID + ":" + JBOSS_UNIVERSE_ARTIFACT_ID) : builtin;
    }

    public void disableBackgroundResolution() {
        bckResolution = false;
    }

    /**
     * Universe resolution is done in a separate thread to not impact startup
     * time.
     */
    synchronized void resolveBuiltinUniverse() {
        if (closed) {
            return;
        }
        Future<?> f = executorService.submit(() -> {
            synchronized (UniverseManager.this) {
                if (closed) {
                    return;
                }
                try {
                    List<FeaturePackLocation> deps = new ArrayList<>();
                    builtinUniverse = (MavenUniverse) universeResolver.getUniverse(builtinUniverseSpec, true);
                    if (closed) {
                        return;
                    }
                    //speed-up future completion and execution by retrieving producers and channels
                    for (Producer<?> p : builtinUniverse.getProducers()) {
                        final MavenProducer mvnProducer = (MavenProducer) p;
                        if (mvnProducer.isResolvedLocally()) {
                            mvnProducer.refresh();
                        }
                        if (closed) {
                            return;
                        }
                        for (Channel c : p.getChannels()) {
                            if (closed) {
                                return;
                            }
                            FeaturePackLocation ploc = new FeaturePackLocation(builtinUniverseSpec, p.getName(), c.getName(), null, null);
                            deps.add(ploc);
                        }
                    }
                } catch (Exception ex) {
                    CliLogging.exceptionResolvingBuiltinUniverse(ex);
                }
            }
        });
        submited.add(f);
    }

    synchronized void close() {
        closed = true;
        executorService.shutdownNow();
        boolean terminated = true;
        for (Future<?> f : submited) {
            if (!f.isDone()) {
                terminated = false;
                break;
            }
        }
        if (!terminated) {
            // We need to in order to have all the layout closed before the factory.
            // This should not exeed few seconds, resolution stops has soon as it
            // detects that we are closed (closing == true).
            pmSession.println("Awaiting termination of background resolution...");
            try {
                executorService.awaitTermination(20, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.interrupted();
                pmSession.println("Interrupted");
            }
        }

    }

    public MavenUniverse getBuiltinUniverse() {
        synchronized (this) {
            return builtinUniverse;
        }
    }

    public UniverseSpec getBuiltinUniverseSpec() {
        return builtinUniverseSpec;
    }

    public synchronized Universe<?> getUniverse(UniverseSpec spec) throws ProvisioningException {
        return universeResolver.getUniverse(spec);
    }

    public synchronized Path resolve(FeaturePackLocation fpl) throws ProvisioningException {
        return universeResolver.resolve(fpl);
    }

    public synchronized boolean isResolved(FeaturePackLocation fpl) throws ProvisioningException {
        return universeResolver.isResolved(fpl);
    }

    public synchronized FeaturePackLocation resolveLatestBuild(FeaturePackLocation fpl) throws ProvisioningException {
        return universeResolver.resolveLatestBuild(fpl);
    }

    public void resolveUniverse(UniverseSpec u) throws ProvisioningException {
        // Resolve universe synchronously.
        Universe<?> universe = universeResolver.getUniverse(u);
        for (Producer<?> p : universe.getProducers()) {
            for (Channel c : p.getChannels()) {
            }
        }
    }

    @Override
    public void configurationChanged(MavenConfig config) throws XMLStreamException, IOException {
        if (bckResolution) {
            resolveBuiltinUniverse();
        }
    }

    public void visitUniverse(UniverseSpec universeSpec,
            UniverseVisitor visitor, boolean allBuilds) throws ProvisioningException {
        visit(visitor, getUniverse(universeSpec), universeSpec, allBuilds);
    }

    public static void visit(UniverseVisitor visitor, Universe<?> universe,
            UniverseSpec universeSpec, boolean allBuilds) throws ProvisioningException {
        for (Producer<?> producer : universe.getProducers()) {
            for (Channel channel : producer.getChannels()) {
                if (allBuilds) {
                    List<String> builds = getAllBuilds(universeSpec, producer, channel);
                    if (builds != null && !builds.isEmpty()) {
                        for (String build : builds) {
                            visitor.visit(producer, new FeaturePackLocation(universeSpec,
                                    producer.getName(), channel.getName(), null, build));
                        }
                    }
                }
                for (String freq : producer.getFrequencies()) {
                    String build = getBuild(universeSpec, producer, channel, freq);
                    // We have a latest build for the frequency.
                    if (build != null) {
                        FeaturePackLocation loc = new FeaturePackLocation(universeSpec,
                                producer.getName(), channel.getName(), freq, build);
                        visitor.visit(producer, loc);
                    }
                }
            }
        }
    }

    private static List<String> getAllBuilds(UniverseSpec spec, Producer<?> producer, Channel channel) {
        FeaturePackLocation loc = new FeaturePackLocation(spec, producer.getName(), channel.getName(), null, null);
        List<String> build = Collections.emptyList();
        try {
            build = channel.getAllBuilds(loc);
        } catch (ProvisioningException ex) {
            // OK, no build.
        }
        return build;
    }

    private static String getBuild(UniverseSpec spec, Producer<?> producer, Channel channel, String freq) {
        FeaturePackLocation loc = new FeaturePackLocation(spec, producer.getName(), channel.getName(), freq, null);
        String build = null;
        try {
            build = channel.getLatestBuild(loc);
        } catch (ProvisioningException ex) {
            // OK, no build.
        }
        return build;
    }

}
