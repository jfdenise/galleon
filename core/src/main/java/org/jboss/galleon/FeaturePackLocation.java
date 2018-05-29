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

package org.jboss.galleon;

/**
 * Complete feature-pack location incorporates two things: the feature-pack
 * identity and its origin.
 *
 * The identity is used to check whether the feature-pack is present in
 * the installation, check version compatibility, etc.
 *
 * The origin is used to obtain the feature-pack and later after it has been
 * installed to check for version updates.
 *
 * The string format for the complete location is producer[@universe]:channel[/frequency]#build
 *
 * Producer may represent a product or a project.
 *
 * Universe is a set of producers.
 *
 * Channel represents a stream of backward compatible version updates.
 *
 * Frequency is an optional classifier for feature-pack builds that are
 * streamed through the channel, e.g. DR, Alpha, Beta, CR, Final, etc. It is
 * basically the channel's feature-pack build filter.
 *
 * Build is an ID or version of the feature-pack which must be unique in the scope of the channel.
 *
 * @author Alexey Loubyansky
 */
public class FeaturePackLocation {

    /**
     * Creates an instance of FeaturePackLocation from its string representation.
     *
     * @param str  string representation of a feature-pack location
     * @return  an instance of FeaturePackLocation
     * @throws ProvisioningDescriptionException  in case the string is not following the syntax
     */
    public static FeaturePackLocation fromString(String str) throws ProvisioningDescriptionException {
        if(str == null) {
            throw new IllegalArgumentException("str is null");
        }

        int buildSep = str.lastIndexOf('#');
        if(buildSep < 0) {
            buildSep = str.length();
        }
        int universeEnd = buildSep;
        int channelNameEnd = buildSep;
        loop: while(universeEnd > 0) {
            switch(str.charAt(--universeEnd)) {
                case '/':
                    channelNameEnd = universeEnd;
                    break;
                case ':':
                    break loop;
            }
        }
        if(universeEnd <= 0) {
            throw unexpectedFormat(str);
        }
        int producerEnd = 0;
        while(producerEnd < universeEnd) {
            if(str.charAt(producerEnd) == '@') {
                break;
            }
            ++producerEnd;
        }
        if(producerEnd == 0) {
            throw unexpectedFormat(str);
        }

        return new FeaturePackLocation(
                producerEnd == universeEnd ? null : str.substring(producerEnd + 1, universeEnd),
                str.substring(0, producerEnd),
                str.substring(universeEnd + 1, channelNameEnd),
                channelNameEnd == buildSep ? null : str.substring(channelNameEnd + 1, buildSep),
                buildSep == str.length() ? null : str.substring(buildSep + 1));
    }

    public static FPID newFPID(String universe, String producer, String channel, String build) {
        return new FeaturePackLocation(universe, producer, channel, null, build).getFPID();
    }

    private static ProvisioningDescriptionException unexpectedFormat(String str) {
        return new ProvisioningDescriptionException(str + " does not follow format producer[@universe]:channel[/frequency]#build");
    }

    /**
     * Represents a feature-pack identity
     */
    public class FPID {

        private final int hash;

        private FPID() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((build == null) ? 0 : build.hashCode());
            result = prime * result + ((channelName == null) ? 0 : channelName.hashCode());
            result = prime * result + ((producer == null) ? 0 : producer.hashCode());
            result = prime * result + ((universe == null) ? 0 : universe.hashCode());
            hash = result;
        }

        public boolean hasUniverse() {
            return universe != null;
        }

        public String getUniverse() {
            return universe;
        }

        public String getProducer() {
            return producer;
        }

        public String getChannelName() {
            return channelName;
        }

        public String getBuild() {
            return build;
        }

        public Channel getChannel() {
            return FeaturePackLocation.this.getChannel();
        }

        public FeaturePackLocation getLocation() {
            return FeaturePackLocation.this;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            FPID other = (FPID) obj;
            if (build == null) {
                if (other.getBuild() != null)
                    return false;
            } else if (!build.equals(other.getBuild()))
                return false;
            if (channelName == null) {
                if (other.getChannelName() != null)
                    return false;
            } else if (!channelName.equals(other.getChannelName()))
                return false;
            if (producer == null) {
                if (other.getProducer() != null)
                    return false;
            } else if (!producer.equals(other.getProducer()))
                return false;
            if (universe == null) {
                if (other.getUniverse() != null)
                    return false;
            } else if (!universe.equals(other.getUniverse()))
                return false;
            return true;
        }

        @Override
        public String toString() {
            return FeaturePackLocation.toString(universe, producer, channelName, null, build);
        }
    }

    /**
     * Represents the origin of a feature-pack
     */
    public class Channel {

        private final int hash;

        private Channel() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((channelName == null) ? 0 : channelName.hashCode());
            result = prime * result + ((producer == null) ? 0 : producer.hashCode());
            result = prime * result + ((universe == null) ? 0 : universe.hashCode());
            hash = result;
        }

        public boolean hasUniverse() {
            return universe != null;
        }

        public String getUniverse() {
            return universe;
        }

        public String getProducer() {
            return producer;
        }

        public String getChannelName() {
            return channelName;
        }

        public FeaturePackLocation getLocation() {
            return FeaturePackLocation.this;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Channel other = (Channel) obj;
            if (channelName == null) {
                if (other.getChannelName() != null)
                    return false;
            } else if (!channelName.equals(other.getChannelName()))
                return false;
            if (producer == null) {
                if (other.getProducer() != null)
                    return false;
            } else if (!producer.equals(other.getProducer()))
                return false;
            if (universe == null) {
                if (other.getUniverse() != null)
                    return false;
            } else if (!universe.equals(other.getUniverse()))
                return false;
            return true;
        }

        @Override
        public String toString() {
            return FeaturePackLocation.toString(universe, producer, channelName, null, null);
        }
    }

    private final String universe;
    private final String producer;
    private final String channelName;
    private final String frequency;
    private final String build;
    private final int hash;
    private Channel channel;
    private FPID fpid;

    public FeaturePackLocation(String universe, String producer, String channelName, String frequency, String build) {
        this.universe = universe;
        this.producer = producer;
        this.channelName = channelName;
        this.frequency = frequency;
        this.build = build;

        final int prime = 31;
        int result = 1;
        result = prime * result + ((channelName == null) ? 0 : channelName.hashCode());
        result = prime * result + ((build == null) ? 0 : build.hashCode());
        result = prime * result + ((frequency == null) ? 0 : frequency.hashCode());
        result = prime * result + ((producer == null) ? 0 : producer.hashCode());
        result = prime * result + ((fpid == null) ? 0 : fpid.hashCode());
        result = prime * result + ((universe == null) ? 0 : universe.hashCode());
        this.hash = result;
    }

    public boolean hasUniverse() {
        return universe != null;
    }

    public String getUniverse() {
        return universe;
    }

    public String getProducer() {
        return producer;
    }

    public String getChannelName() {
        return channelName;
    }

    public boolean hasFrequency() {
        return frequency != null;
    }

    public String getFrequency() {
        return frequency;
    }

    public String getBuild() {
        return build;
    }

    public Channel getChannel() {
        if(channel == null) {
            channel = new Channel();
        }
        return channel;
    }

    public FPID getFPID() {
        if(fpid == null) {
            fpid = new FPID();
        }
        return fpid;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        FeaturePackLocation other = (FeaturePackLocation) obj;
        if (channelName == null) {
            if (other.channelName != null)
                return false;
        } else if (!channelName.equals(other.channelName))
            return false;
        if (build == null) {
            if (other.build != null)
                return false;
        } else if (!build.equals(other.build))
            return false;
        if (frequency == null) {
            if (other.frequency != null)
                return false;
        } else if (!frequency.equals(other.frequency))
            return false;
        if (producer == null) {
            if (other.producer != null)
                return false;
        } else if (!producer.equals(other.producer))
            return false;
        if (universe == null) {
            if (other.universe != null)
                return false;
        } else if (!universe.equals(other.universe))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return toString(universe, producer, channelName, frequency, build);
    }

    private static String toString(String universe, String producer, String channel, String frequency, String build) {
        final StringBuilder buf = new StringBuilder();
        buf.append(producer);
        if(universe != null && !universe.isEmpty()) {
            buf.append('@').append(universe);
        }
        buf.append(':').append(channel);
        if(frequency != null && !frequency.isEmpty()) {
            buf.append('/').append(frequency);
        }
        if(build != null && !build.isEmpty()) {
            buf.append('#').append(build);
        }
        return buf.toString();
    }
}
