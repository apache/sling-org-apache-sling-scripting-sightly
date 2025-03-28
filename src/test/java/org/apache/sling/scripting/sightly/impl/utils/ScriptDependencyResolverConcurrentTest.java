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
package org.apache.sling.scripting.sightly.impl.utils;

import javax.script.Bindings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.scripting.SlingBindings;
import org.apache.sling.scripting.api.resource.ScriptingResourceResolverProvider;
import org.apache.sling.scripting.sightly.impl.engine.SightlyEngineConfiguration;
import org.apache.sling.scripting.sightly.render.RenderContext;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class ScriptDependencyResolverConcurrentTest {

    @Parameterized.Parameters
    public static Object[] data() {
        return new Object[20][0];
    }

    @Rule
    public SlingContext context = new SlingContext();

    @Before
    public void before() throws PersistenceException {
        // resource hierarchy
        ResourceUtil.getOrCreateResource(
                context.resourceResolver(), "/apps/base/partial.html", "nt:file", "sling:Folder", true);
        ResourceUtil.getOrCreateResource(
                context.resourceResolver(), "/apps/inherit/inherit.html", "nt:file", "sling:Folder", true);
        Resource inherit = context.resourceResolver().getResource("/apps/inherit");
        assertNotNull(inherit);
        ModifiableValueMap inheritProperties = inherit.adaptTo(ModifiableValueMap.class);
        assertNotNull(inheritProperties);
        inheritProperties.put("sling:resourceSuperType", "base");
        context.resourceResolver().commit();

        Map<String, Object> testResourceProperties = new HashMap<>();
        testResourceProperties.put("sling:resourceType", "inherit");
        ResourceUtil.getOrCreateResource(
                context.resourceResolver(), "/content/test", testResourceProperties, "sling:Folder", true);
        context.request().setResource(context.resourceResolver().getResource("/content/test"));
    }

    @Test
    public void testDependenciesResolvingCacheEnabledConcurrent_neverReturnsNull()
            throws InterruptedException, ExecutionException {
        SightlyEngineConfiguration configuration = mock(SightlyEngineConfiguration.class);
        when(configuration.getScriptResolutionCacheSize()).thenReturn(1024);
        context.registerService(configuration);

        ResourceResolver scriptingResolver = spy(context.resourceResolver());

        ScriptingResourceResolverProvider scriptingResourceResolverProvider =
                mock(ScriptingResourceResolverProvider.class);
        when(scriptingResourceResolverProvider.getRequestScopedResourceResolver())
                .thenReturn(scriptingResolver);
        context.registerService(ScriptingResourceResolverProvider.class, scriptingResourceResolverProvider);

        ScriptDependencyResolver scriptDependencyResolver =
                spy(context.registerInjectActivateService(new ScriptDependencyResolver()));

        RenderContext renderContext = mock(RenderContext.class);
        Bindings bindings = mock(Bindings.class);
        when(renderContext.getBindings()).thenReturn(bindings);
        when(bindings.get(SlingBindings.REQUEST)).thenReturn(context.request());

        // invoke resolveScript with the same identifier
        int size = 100;
        ExecutorService executor = Executors.newFixedThreadPool(size);

        try {
            List<Future<Resource>> resolutionValues = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                resolutionValues.add(executor.submit(() -> {
                    return scriptDependencyResolver.resolveScript(renderContext, "partial.html");
                }));
            }
            int nullValues = 0;
            for (Future<Resource> v : resolutionValues) {
                Resource r = v.get();
                if (r == null) {
                    nullValues++;
                }
            }
            assertEquals("resolveScript must never return null here", 0, nullValues);
        } finally {
            executor.shutdown();
        }
    }
}
