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
package org.jboss.galleon;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;

import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.util.StringUtils;

/**
 *
 * @author Alexey Loubyansky
 */
public interface BaseErrors {

    // GENERAL MESSAGES

    static String pathDoesNotExist(Path p) {
        return "Failed to locate " + p.toAbsolutePath();
    }

    static String mkdirs(Path p) {
        return "Failed to make directories " + p.toAbsolutePath();
    }

    static String readDirectory(Path p) {
        return "Failed to read directory " + p.toAbsolutePath();
    }

    static String notADir(Path p) {
        return p.toAbsolutePath() + " is not a directory";
    }
    // FEATURE PACK INSTALL MESSAGES

    static String homeDirNotUsable(Path p) {
        return p + " has to be empty or contain a provisioned installation to be used by the tool";
    }

    static String noVersionAvailable(FeaturePackLocation fpl) {
        return "No version is available for " + fpl;
    }

    static String defaultChannelNotConfigured(String producer) {
        return "Default channel has not been configured for feature-pack producer " + producer;
    }

    static String frequencyNotSupported(final Collection<String> frequencies, FeaturePackLocation fpl) {
        final StringBuilder buf = new StringBuilder();
        buf.append("The frequency specified in ").append(fpl).append(" is not supported, the producer ");
        if (frequencies.isEmpty()) {
            buf.append(" does not suport frequencies");
        } else {
            buf.append("supported frequencies are ");
            final String[] arr = frequencies.toArray(new String[frequencies.size()]);
            Arrays.sort(arr);
            StringUtils.append(buf, Arrays.asList(arr));
        }
        return buf.toString();
    }

    static String writeFile(Path p) {
        return "Failed to write to " + p.toAbsolutePath();
    }
}