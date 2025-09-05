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
package org.apache.sling.scripting.sightly.impl.engine.extension.use;

import java.util.Map;

import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.scripting.SlingBindings;
import org.apache.sling.scripting.sightly.render.RenderContext;
import org.apache.sling.scripting.sightly.use.ProviderOutcome;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class ResourceUseProviderTest {

    @Rule
    public SlingContext context = new SlingContext();

    private ResourceUseProvider provider;

    private SlingBindings arguments;
    private RenderContext renderContext;
    private SlingBindings requestBindings;

    @Before
    public void setUp() throws PersistenceException {
        provider = context.registerInjectActivateService(ResourceUseProvider.class);
        assertNotNull(provider);

        arguments = new SlingBindings();

        ResourceResolver rr = context.resourceResolver();
        Resource rootResource = rr.getResource("/");
        Resource node1 = context.currentResource(rr.create(rootResource, "node1", Map.of()));
        rr.create(node1, "child1", Map.of());
        rr.create(rootResource, "node3", Map.of("jcr:primaryType", "sling:nonexisting"));

        renderContext = Mockito.mock(RenderContext.class);
        requestBindings = (SlingBindings) context.jakartaRequest().getAttribute(SlingBindings.class.getName());
        assertNotNull(requestBindings);
        Mockito.when(renderContext.getBindings()).thenReturn(requestBindings);
    }

    /**
     * Test method for {@link org.apache.sling.scripting.sightly.impl.engine.extension.use.ResourceUseProvider#provide(java.lang.String, org.apache.sling.scripting.sightly.render.RenderContext, javax.script.Bindings)}.
     */
    @Test
    public void testProvide() {
        // absolute path that exists
        ProviderOutcome outcome = provider.provide("/node1", renderContext, arguments);
        assertTrue(outcome.isSuccess());

        // absolute path that does not exists
        outcome = provider.provide("/node2", renderContext, arguments);
        assertTrue(outcome.isFailure());

        // absolute path that does exists, but sling:nonexisting type
        outcome = provider.provide("/node3", renderContext, arguments);
        assertTrue(outcome.isFailure());

        // relative path that exists
        outcome = provider.provide("child1", renderContext, arguments);
        assertTrue(outcome.isSuccess());
    }

    @Test
    public void testProvideWithCaughtException() {
        requestBindings = Mockito.spy(requestBindings);
        Mockito.when(renderContext.getBindings()).thenReturn(requestBindings);
        SlingJakartaHttpServletRequest mockJakartaRequest = Mockito.spy(context.jakartaRequest());
        Mockito.when(requestBindings.get(SlingBindings.JAKARTA_REQUEST)).thenReturn(mockJakartaRequest);
        Mockito.when(mockJakartaRequest.getResourceResolver()).thenThrow(IllegalStateException.class);

        ProviderOutcome outcome = provider.provide("/node1", renderContext, arguments);
        assertTrue(outcome.isFailure());
    }
}
