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
package org.apache.sling.scripting.sightly.impl.engine;

import java.util.Map;

import jakarta.servlet.Servlet;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.scripting.sightly.engine.ResourceResolution;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.apache.sling.testing.mock.sling.servlet.MockSlingJakartaHttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

/**
 *
 */
public class ResourceResolutionTest {

    @Rule
    public final SlingContext slingContext = new SlingContext();

    /**
     * Test method for {@link org.apache.sling.scripting.sightly.engine.ResourceResolution#getResourceFromSearchPath(org.apache.sling.api.resource.Resource, java.lang.String)}.
     */
    @Test
    public void testGetResourceFromSearchPath() throws PersistenceException {
        // both args null
        assertNull(ResourceResolution.getResourceFromSearchPath(null, null));

        // baseResource not null, path is null
        ResourceResolver rr = slingContext.resourceResolver();
        Resource baseResource = rr.resolve("/");
        assertNull(ResourceResolution.getResourceFromSearchPath(baseResource, null));

        // both args are not null - absolute path to not existing resource
        String path = "/node1";
        assertNull(ResourceResolution.getResourceFromSearchPath(baseResource, path));

        // absolute path to existing resource outside of the search path
        rr.create(rr.getResource("/"), "node1", Map.of());
        assertThrows(
                UnsupportedOperationException.class,
                () -> ResourceResolution.getResourceFromSearchPath(baseResource, path));

        // absolute path to existing resource within of the search path
        Resource mockScriptResource = createMockScriptResource(rr);
        Resource resourceFromSearchPath =
                ResourceResolution.getResourceFromSearchPath(baseResource, mockScriptResource.getPath());
        assertNotNull(resourceFromSearchPath);
        assertSame(resourceFromSearchPath.getPath(), mockScriptResource.getPath());

        // relative path to not existing script resource
        resourceFromSearchPath = ResourceResolution.getResourceFromSearchPath(baseResource, "sling:invalid");
        assertNull(resourceFromSearchPath);

        // relative path to not existing script resource
        resourceFromSearchPath = ResourceResolution.getResourceFromSearchPath(baseResource, "sling:nonexisting");
        assertEquals(resourceFromSearchPath.getPath(), mockScriptResource.getPath());

        // basePath is nt:file
        Resource fileResource = rr.create(rr.getResource("/"), "file1", Map.of("jcr:primaryType", "nt:file"));
        resourceFromSearchPath = ResourceResolution.getResourceFromSearchPath(fileResource, "sling:nonexisting");
        assertEquals(resourceFromSearchPath.getPath(), mockScriptResource.getPath());

        // basePath adaptable to jakarta servlet
        Resource mockResource = Mockito.mock(Resource.class);
        Mockito.when(mockResource.getResourceResolver()).thenReturn(rr);
        Mockito.when(mockResource.getParent()).thenReturn(baseResource);
        Servlet mockJakartaServlet = Mockito.mock(Servlet.class);
        Mockito.when(mockResource.adaptTo(Servlet.class)).thenReturn(mockJakartaServlet);
        resourceFromSearchPath = ResourceResolution.getResourceFromSearchPath(mockResource, "sling:nonexisting");
        assertEquals(resourceFromSearchPath.getPath(), mockScriptResource.getPath());

        // basePath adaptable to javax servlet
        javax.servlet.Servlet mockJavaxServlet = Mockito.mock(javax.servlet.Servlet.class);
        Mockito.when(mockResource.adaptTo(Servlet.class)).thenReturn(null);
        Mockito.when(mockResource.adaptTo(javax.servlet.Servlet.class)).thenReturn(mockJavaxServlet);
        resourceFromSearchPath = ResourceResolution.getResourceFromSearchPath(mockResource, "sling:nonexisting");
        assertEquals(resourceFromSearchPath.getPath(), mockScriptResource.getPath());
    }

    /**
     * Test method for {@link org.apache.sling.scripting.sightly.engine.ResourceResolution#getResourceForRequest(org.apache.sling.api.resource.ResourceResolver, org.apache.sling.api.SlingHttpServletRequest)}.
     * @deprecated use {@link #testGetResourceForRequestResourceResolverSlingJakartaHttpServletRequest()} instead
     */
    @Deprecated(since = "2.0.0")
    @Test
    public void testGetResourceForRequestResourceResolverSlingHttpServletRequest() throws PersistenceException {
        ResourceResolver rr = slingContext.resourceResolver();
        slingContext.currentResource(rr.resolve("/node1"));
        org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest request = slingContext.request();

        // handling null params
        assertNull(ResourceResolution.getResourceForRequest(null, request));
        assertNull(ResourceResolution.getResourceForRequest(rr, (org.apache.sling.api.SlingHttpServletRequest) null));

        // handing not existing script resource
        Resource resourceForRequest = ResourceResolution.getResourceForRequest(rr, request);
        assertNull(resourceForRequest);

        // handing existing script resource
        Resource scriptResource = createMockScriptResource(rr);
        resourceForRequest = ResourceResolution.getResourceForRequest(rr, request);
        assertNotNull(resourceForRequest);
        assertEquals(resourceForRequest.getPath(), scriptResource.getPath());
    }

    /**
     * Test method for {@link org.apache.sling.scripting.sightly.engine.ResourceResolution#getResourceForRequest(org.apache.sling.api.resource.ResourceResolver, org.apache.sling.api.SlingJakartaHttpServletRequest)}.
     */
    @Test
    public void testGetResourceForRequestResourceResolverSlingJakartaHttpServletRequest() throws PersistenceException {
        ResourceResolver rr = slingContext.resourceResolver();
        slingContext.currentResource(rr.resolve("/node1"));
        MockSlingJakartaHttpServletRequest request = slingContext.jakartaRequest();

        // handling null params
        assertNull(ResourceResolution.getResourceForJakartaRequest(null, request));
        assertNull(ResourceResolution.getResourceForJakartaRequest(rr, (SlingJakartaHttpServletRequest) null));

        // handing not existing script resource
        Resource resourceForRequest = ResourceResolution.getResourceForJakartaRequest(rr, request);
        assertNull(resourceForRequest);

        // handing existing script resource
        Resource scriptResource = createMockScriptResource(rr);
        resourceForRequest = ResourceResolution.getResourceForJakartaRequest(rr, request);
        assertNotNull(resourceForRequest);
        assertEquals(resourceForRequest.getPath(), scriptResource.getPath());

        // handing existing script resource with empty resource type
        Resource mockResource = Mockito.mock(Resource.class);
        Mockito.when(mockResource.getResourceType()).thenReturn("");
        slingContext.currentResource(mockResource);
        resourceForRequest = ResourceResolution.getResourceForJakartaRequest(rr, request);
        assertNull(resourceForRequest);
    }

    protected @NotNull Resource createMockScriptResource(@NotNull ResourceResolver rr) throws PersistenceException {
        Resource libResource = rr.create(rr.getResource("/"), "libs", Map.of());
        return rr.create(libResource, "sling:nonexisting", Map.of());
    }
}
