/*
 * Copyright 2016-2025 Red Hat, Inc. and/or its affiliates
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

import java.util.HashMap;
import java.util.Map;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.spec.FeaturePackSpec;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.FeaturePackLocation.FPID;

/**
 * Family handling.
 *
 * @author jdenise
 */
class FeaturePackFamily {

    interface FeaturePackSpecResolver {

        FeaturePackSpec resolve(FeaturePackLocation loc) throws ProvisioningException;
    }

    static class FamilyResolutionResult {

        private final FeaturePackConfig memberDependency;
        private final boolean differentFamilyMember;
        private final FeaturePackConfig originalDependency;

        private FamilyResolutionResult(FeaturePackConfig originalDependency, FeaturePackConfig memberDependency, boolean differentFamilyMember) {
            this.originalDependency = originalDependency;
            this.memberDependency = memberDependency;
            this.differentFamilyMember = differentFamilyMember;
        }

        boolean isDifferentMember() {
            return differentFamilyMember;
        }

        FeaturePackConfig getResolvedDependency() {
            return memberDependency;
        }

        FeaturePackConfig getOriginalDependency() {
            return originalDependency;
        }
    }

    class FeaturePackFamilyResolution {

        private final FeaturePackSpecResolver resolver;
        private final FeaturePackLocation fpl;
        FeaturePackFamilyResolution(FeaturePackSpec fpSpec, FeaturePackLocation fpl, FeaturePackSpecResolver resolver) {
            registerFamilyMember(fpSpec.getFamily(), fpl.getFPID());
            this.resolver = resolver;
            this.fpl = fpl;
        }

        FamilyResolutionResult resolveDependency(FeaturePackConfig originalDependency) throws ProvisioningException {
            FPID memberFPID = originalDependency.getLocation().getFPID();
            FeaturePackConfig memberDependency = originalDependency;
            boolean differentFamilyMember = false;
            if (hasFamilyMembers()) {
                boolean allowsFamilyMeber = originalDependency.isFamilyMemberAllowed();
                if (allowsFamilyMeber) {
                    FeaturePackSpec depSpec = resolver.resolve(memberFPID.getLocation());
                    FPID member = depSpec.getFamily() == null ? null : getFamilyMember(depSpec.getFamily());
                    if (member != null) {
                        // Only replace with a member that has a Maven FPL, Galleon channel based FPL are not available
                        // for replacement.
                        if (!originalDependency.getLocation().getProducer().equals(member.getProducer()) && member.getLocation().isMavenCoordinates()) {
                            System.out.println("\nFound a family member");
                            System.out.println("Provisioning time dep : " + member);
                            System.out.println("Build time dep        : " + originalDependency.getLocation().getProducer());
                            memberFPID = member;
                            memberDependency = FeaturePackConfig.builder(memberFPID.getLocation()).init(originalDependency).build();
                            differentFamilyMember = true;
                        }
                    }
                }
            }
            FeaturePackSpec depSpec = resolver.resolve(memberFPID.getLocation());
            if (!differentFamilyMember) {
                // If the feature-pack dependency has a family, and a member already exists, make sure that the dependency matches the current member.
                // otherwise it means that a different member is provisioned but this feature-pack expects a given member.
                FPID existingMember = getFamilyMember(depSpec.getFamily());
                if (existingMember != null && existingMember.getLocation().isMavenCoordinates()) {
                    if (!existingMember.getProducer().equals(originalDependency.getLocation().getProducer())) {
                        throw new ProvisioningException("The feature-pack " + fpl + " expects the dependency on " + originalDependency.getLocation() +
                                " but this dependency is of the family " + depSpec.getFamily() + " for which a different member " +
                                existingMember + " is in use.");
                    }
                }
            }
            registerFamilyMember(depSpec.getFamily(), memberFPID);
            return new FamilyResolutionResult(originalDependency, memberDependency, differentFamilyMember);
        }

    }
    private final Map<String, FeaturePackLocation.FPID> resolvedFamilyMembers = new HashMap<>();

    FeaturePackFamily() {

    }

    private void registerFamilyMember(String family, FPID member) {
        if (family != null) {
            if (!resolvedFamilyMembers.containsKey(family)) {
                resolvedFamilyMembers.put(family, member);
            }
        }
    }

    private FPID getFamilyMember(String family) {
        if (family != null) {
            return resolvedFamilyMembers.get(family);
        }
        return null;
    }

    private boolean hasFamilyMembers() {
        return !resolvedFamilyMembers.isEmpty();
    }

    FeaturePackFamilyResolution newResolution(FeaturePackSpec fpSpec, FeaturePackLocation fpl, FeaturePackSpecResolver resolver) {
        return new FeaturePackFamilyResolution(fpSpec, fpl, resolver);
    }
}
