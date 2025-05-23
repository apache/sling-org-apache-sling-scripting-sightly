/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.scripting.sightly.impl.engine.compiled;

import java.util.HashMap;
import java.util.Map;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.scripting.sightly.impl.engine.SightlyEngineConfiguration;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SourceIdentifierTest {

    private static final String BUNDLE_SYMBOLIC_NAME = "org.apache.sling.scripting.sightly";

    @Test
    public void testGetClassName() {
        SourceIdentifier sourceIdentifier = getSourceIdentifier("/apps/blah/static/foo/foo.html");
        assertEquals("foo_html", sourceIdentifier.getSimpleClassName());
    }

    @Test
    public void testGetPackageName() {
        SourceIdentifier sourceIdentifier = getSourceIdentifier("/apps/blah/static/foo/foo.html");
        assertEquals(BUNDLE_SYMBOLIC_NAME + ".apps.blah.static_.foo", sourceIdentifier.getPackageName());
    }

    @Test
    public void testGetFullyQualifiedName() {
        SourceIdentifier sourceIdentifier = getSourceIdentifier("/apps/blah/static/foo/foo.html");
        assertEquals(
                BUNDLE_SYMBOLIC_NAME + ".apps.blah.static_.foo.foo_html",
                sourceIdentifier.getPackageName() + "." + sourceIdentifier.getSimpleClassName());
    }

    @Test
    public void testPOJOMangling() {
        Map<String, String> expectedMangling = new HashMap<String, String>() {
            {
                put("/apps/test-static/Pojo.java", "apps.test_static.Pojo");
                put("/apps/_static/Pojo.java", "apps._static.Pojo");
                put("/apps/static/Pojo.java", "apps.static_.Pojo");
                put("/apps/a-b-c/d-e-f/AbcDefPojo.java", "apps.a_b_c.d_e_f.AbcDefPojo");
            }
        };
        for (Map.Entry<String, String> manglingEntry : expectedMangling.entrySet()) {
            SourceIdentifier sourceIdentifier = getSourceIdentifier(manglingEntry.getKey());
            assertEquals(manglingEntry.getValue(), sourceIdentifier.getFullyQualifiedClassName());
        }
    }

    private SourceIdentifier getSourceIdentifier(String path) {
        Resource resource = mock(Resource.class);
        when(resource.getPath()).thenReturn(path);
        SightlyEngineConfiguration configuration = mock(SightlyEngineConfiguration.class);
        when(configuration.getBundleSymbolicName()).thenReturn(BUNDLE_SYMBOLIC_NAME);
        return new SourceIdentifier(configuration, resource.getPath());
    }
}
