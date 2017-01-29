/*
 * (C) Copyright ${year} Code-House, Łukasz Dywicki.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.code_house.maven.osgi.resolver.strict;

import org.code_house.maven.osgi.resolver.shared.CustomVersionRangeResolver;
import org.code_house.maven.osgi.resolver.shared.version.OsgiVersionScheme;
import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.MetadataResolver;
import org.eclipse.aether.impl.RepositoryEventDispatcher;
import org.eclipse.aether.impl.SyncContextFactory;
import org.eclipse.aether.impl.VersionRangeResolver;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.spi.log.LoggerFactory;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;
import org.eclipse.aether.version.VersionScheme;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component(role = VersionRangeResolver.class)
public class StrictOsgiVersionRangeResolver extends CustomVersionRangeResolver {

    public StrictOsgiVersionRangeResolver() {
        // enable default constructor
    }

    @Inject
    public StrictOsgiVersionRangeResolver(MetadataResolver metadataResolver, SyncContextFactory syncContextFactory, RepositoryEventDispatcher repositoryEventDispatcher, LoggerFactory loggerFactory) {
        super(metadataResolver, syncContextFactory, repositoryEventDispatcher, loggerFactory);
    }

    public VersionRangeResult resolveVersionRange(RepositorySystemSession session, VersionRangeRequest request) throws VersionRangeResolutionException {
        VersionRangeResult result = new VersionRangeResult(request);

        VersionScheme osgiVersionScheme = new OsgiVersionScheme();
        VersionScheme genericVersionScheme = new OsgiVersionScheme();

        String rawVersion = request.getArtifact().getVersion();
        VersionConstraint versionConstraint;
        try {
            versionConstraint = osgiVersionScheme.parseVersionConstraint(rawVersion);
        } catch (InvalidVersionSpecificationException e) {
            result.addException(e);
            throw new VersionRangeResolutionException(result);
        }

        if (versionConstraint.getRange() == null || (rawVersion.charAt(0) != '(' && rawVersion.charAt(0) != '[' && rawVersion.charAt(rawVersion.length()) != ')' && rawVersion.charAt(rawVersion.length()) != ']')) {
            // version is exact (ie. range is quite narrow) or passed version does not contain typical range characters so we do not use range but look for *specific* version
            result.setVersionConstraint(versionConstraint);
            result.addVersion(versionConstraint.getVersion());
        } else {
            result.setVersionConstraint(versionConstraint);

            Map<String, ArtifactRepository> versionIndex = getVersions(session, result, request);

            List<Version> versions = new ArrayList<>();
            for (Map.Entry<String, ArtifactRepository> v : versionIndex.entrySet()) {
                try {
                    if (versionConstraint.containsVersion(osgiVersionScheme.parseVersion(v.getKey()))) {
                        // here is whole magic of "compatibility", we use osgi range filtering logic to choose versions
                        // but we use later on traditional maven versions to keep proper order (alpha < beta < release)
                        // which might get broken when osgi treat them as qualifiers
                        Version regularVersion = genericVersionScheme.parseVersion(v.getKey());
                        versions.add(regularVersion);
                        result.setRepository(regularVersion, v.getValue());
                    }
                } catch (InvalidVersionSpecificationException e) {
                    result.addException(e);
                }
            }

            Collections.sort(versions);
            result.setVersions(versions);
        }

        return result;
    }

}
