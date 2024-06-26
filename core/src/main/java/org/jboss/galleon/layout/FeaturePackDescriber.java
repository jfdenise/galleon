/*
 * Copyright 2016-2024 Red Hat, Inc. and/or its affiliates
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
package org.jboss.galleon.layout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamException;
import org.jboss.galleon.BaseErrors;

import org.jboss.galleon.Constants;
import org.jboss.galleon.Errors;
import org.jboss.galleon.ProvisioningDescriptionException;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.config.ConfigModel;
import org.jboss.galleon.spec.ConfigLayerSpec;
import org.jboss.galleon.spec.FeaturePackSpec;
import org.jboss.galleon.spec.FeatureSpec;
import org.jboss.galleon.spec.PackageSpec;
import org.jboss.galleon.util.ZipUtils;
import org.jboss.galleon.xml.ConfigLayerSpecXmlParser;
import org.jboss.galleon.xml.ConfigXmlParser;
import org.jboss.galleon.xml.FeaturePackXmlParser;
import org.jboss.galleon.xml.FeatureSpecXmlParser;
import org.jboss.galleon.xml.PackageXmlParser;
import org.jboss.galleon.xml.XmlParsers;

/**
 *
 * Builds a layout description by analyzing the feature-pack layout
 * structure and parsing included XML files.
 *
 * @author Alexey Loubyansky
 */
public class FeaturePackDescriber {

    public static FeaturePackSpec readSpec(Path artifactZip) throws ProvisioningException {
        try (FileSystem zipfs = ZipUtils.newFileSystem(artifactZip)) {
            for(Path zipRoot : zipfs.getRootDirectories()) {
                final Path p = zipRoot.resolve(Constants.FEATURE_PACK_XML);
                if(!Files.exists(p)) {
                    throw new ProvisioningException("Feature-pack archive does not contain " + Constants.FEATURE_PACK_XML);
                }
                try(BufferedReader reader = Files.newBufferedReader(p)) {
                    return FeaturePackXmlParser.getInstance().parse(reader);
                } catch (XMLStreamException e) {
                    throw new ProvisioningException(Errors.parseXml(p), e);
                }
            }
        } catch (IOException e) {
            throw new ProvisioningException(Errors.readFile(artifactZip), e);
        }
        return null;
    }

    public static FeaturePackDescription describeFeaturePackZip(Path artifactZip) throws IOException, ProvisioningDescriptionException {
        try (FileSystem zipfs = ZipUtils.newFileSystem(artifactZip)) {
            for(Path zipRoot : zipfs.getRootDirectories()) {
                return describeFeaturePack(zipRoot, "UTF-8");
            }
        }
        return null;
    }

    public static FeaturePackDescription describeFeaturePack(Path fpDir, String encoding) throws ProvisioningDescriptionException {
        assertDirectory(fpDir);
        final Path fpXml = fpDir.resolve(Constants.FEATURE_PACK_XML);
        if(!Files.exists(fpXml)) {
            throw new ProvisioningDescriptionException(BaseErrors.pathDoesNotExist(fpXml));
        }
        final FeaturePackDescription.Builder layoutBuilder;
        try (Reader is = Files.newBufferedReader(fpXml, Charset.forName(encoding))) {
            final FeaturePackSpec.Builder specBuilder = FeaturePackSpec.builder();
            XmlParsers.parse(is, specBuilder);
            layoutBuilder = FeaturePackDescription.builder(specBuilder);
        } catch (IOException e) {
            throw new ProvisioningDescriptionException(Errors.openFile(fpXml));
        } catch (XMLStreamException e) {
            throw new ProvisioningDescriptionException(Errors.parseXml(fpXml), e);
        }

        final Path packagesDir = fpDir.resolve(Constants.PACKAGES);
        if(Files.exists(packagesDir)) {
            processPackages(layoutBuilder, packagesDir, encoding);
        }
        final Path layersDir = fpDir.resolve(Constants.LAYERS).resolve("standalone");
        if(Files.exists(layersDir)) {
            processLayers(layoutBuilder, layersDir, encoding);
        }
        final Path featuresDir = fpDir.resolve(Constants.FEATURES);
        if(Files.exists(featuresDir)) {
            processFeatures(layoutBuilder, featuresDir, encoding);
        }
        final Path configsDir = fpDir.resolve(Constants.CONFIGS);
        if(Files.exists(configsDir)) {
            processConfigs(layoutBuilder, configsDir, encoding);
        }
        return layoutBuilder.build();
    }

    private static void processFeatures(FeaturePackDescription.Builder fpBuilder, Path layersDir, String encoding) throws ProvisioningDescriptionException {
        assertDirectory(layersDir);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(layersDir)) {
            for(Path path : stream) {
                fpBuilder.addFeature(processFeature(path, encoding));
            }
        } catch (IOException e) {
            failedToReadDirectory(layersDir, e);
        }
    }

    private static void processConfigs(FeaturePackDescription.Builder fpBuilder, Path configsDir, String encoding) throws ProvisioningDescriptionException {
        assertDirectory(configsDir);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(configsDir)) {
            for(Path path : stream) {
                processConfigModel(path, encoding, fpBuilder);
            }
        } catch (IOException e) {
            failedToReadDirectory(configsDir, e);
        }
    }

    private static void processLayers(FeaturePackDescription.Builder fpBuilder, Path layersDir, String encoding) throws ProvisioningDescriptionException {
        assertDirectory(layersDir);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(layersDir)) {
            for(Path path : stream) {
                fpBuilder.addLayer(processLayer(path, encoding));
            }
        } catch (IOException e) {
            failedToReadDirectory(layersDir, e);
        }
    }
    private static void processPackages(FeaturePackDescription.Builder fpBuilder, Path packagesDir, String encoding) throws ProvisioningDescriptionException {
        assertDirectory(packagesDir);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(packagesDir)) {
            for(Path path : stream) {
                fpBuilder.addPackage(processPackage(path, encoding));
            }
        } catch (IOException e) {
            failedToReadDirectory(packagesDir, e);
        }
    }

    private static ConfigLayerSpec processLayer(Path layerDir, String encoding) throws ProvisioningDescriptionException {
        assertDirectory(layerDir);
        final Path layerXml = layerDir.resolve(Constants.LAYER_SPEC_XML);
        if(!Files.exists(layerXml)) {
            throw new ProvisioningDescriptionException(BaseErrors.pathDoesNotExist(layerXml));
        }
        try (Reader in = Files.newBufferedReader(layerXml, Charset.forName(encoding))) {
            return ConfigLayerSpecXmlParser.getInstance().parse(in);
        } catch (IOException e) {
            throw new ProvisioningDescriptionException(Errors.openFile(layerXml), e);
        } catch (XMLStreamException e) {
            throw new ProvisioningDescriptionException(Errors.parseXml(layerXml), e);
        }
    }
    private static FeatureSpec processFeature(Path featureDir, String encoding) throws ProvisioningDescriptionException {
        assertDirectory(featureDir);
        final Path featureXml = featureDir.resolve(Constants.SPEC_XML);
        if(!Files.exists(featureXml)) {
            throw new ProvisioningDescriptionException(BaseErrors.pathDoesNotExist(featureXml));
        }
        try (Reader in = Files.newBufferedReader(featureXml, Charset.forName(encoding))) {
            return FeatureSpecXmlParser.getInstance().parse(in);
        } catch (IOException e) {
            throw new ProvisioningDescriptionException(Errors.openFile(featureXml), e);
        } catch (XMLStreamException e) {
            throw new ProvisioningDescriptionException(Errors.parseXml(featureXml), e);
        }
    }

    private static void processConfigModel(Path configDir, String encoding, FeaturePackDescription.Builder fpBuilder) throws ProvisioningDescriptionException {
        assertDirectory(configDir);
        ConfigModel model = null;
        Map<String, ConfigModel> configs = new HashMap<>();
        String modelName = configDir.getFileName().toString();
        final Path modelXml = configDir.resolve(Constants.MODEL_XML);
        if (Files.exists(modelXml)) {
            try (Reader in = Files.newBufferedReader(modelXml, Charset.forName(encoding))) {
                model = ConfigXmlParser.getInstance().parse(in);
            } catch (IOException e) {
                throw new ProvisioningDescriptionException(Errors.openFile(modelXml), e);
            } catch (XMLStreamException e) {
                throw new ProvisioningDescriptionException(Errors.parseXml(modelXml), e);
            }
            configs.put(Constants.MODEL_XML, model);
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(configDir)) {
            for (Path path : stream) {
                String configName = path.getFileName().toString();
                final Path configXml = path.resolve(Constants.CONFIG_XML);
                if (Files.exists(configXml)) {
                    try (Reader in = Files.newBufferedReader(configXml, Charset.forName(encoding))) {
                        configs.put(configName, ConfigXmlParser.getInstance().parse(in));
                    } catch (IOException e) {
                        throw new ProvisioningDescriptionException(Errors.openFile(modelXml), e);
                    } catch (XMLStreamException e) {
                        throw new ProvisioningDescriptionException(Errors.parseXml(modelXml), e);
                    }
                }
            }
        } catch (IOException e) {
            failedToReadDirectory(configDir, e);
        }
        fpBuilder.addConfigModel(modelName, configs);
    }

    private static PackageSpec processPackage(Path pkgDir, String encoding) throws ProvisioningDescriptionException {
        assertDirectory(pkgDir);
        final Path pkgXml = pkgDir.resolve(Constants.PACKAGE_XML);
        if(!Files.exists(pkgXml)) {
            throw new ProvisioningDescriptionException(BaseErrors.pathDoesNotExist(pkgXml));
        }
        try (Reader in = Files.newBufferedReader(pkgXml, Charset.forName(encoding))) {
            return PackageXmlParser.getInstance().parse(in);
        } catch (IOException e) {
            throw new ProvisioningDescriptionException(Errors.openFile(pkgXml), e);
        } catch (XMLStreamException e) {
            throw new ProvisioningDescriptionException(Errors.parseXml(pkgXml), e);
        }
    }

    private static void assertDirectory(Path dir) throws ProvisioningDescriptionException {
        if(!Files.isDirectory(dir)) {
            throw new ProvisioningDescriptionException(BaseErrors.notADir(dir));
        }
    }

    private static void failedToReadDirectory(Path p, IOException e) throws ProvisioningDescriptionException {
        throw new ProvisioningDescriptionException(BaseErrors.readDirectory(p), e);
    }
}
