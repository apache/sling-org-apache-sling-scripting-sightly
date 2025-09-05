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
package org.apache.sling.scripting.sightly.impl.engine.extension;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.apache.sling.api.request.RequestDispatcherOptions;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.scripting.SlingBindings;
import org.apache.sling.scripting.sightly.SightlyException;
import org.apache.sling.scripting.sightly.render.AbstractRuntimeObjectModel;
import org.apache.sling.scripting.sightly.render.RenderContext;
import org.apache.sling.scripting.sightly.render.RuntimeObjectModel;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.apache.sling.testing.mock.sling.servlet.MockJakartaRequestDispatcherFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;

/**
 *
 */
public class ResourceRuntimeExtensionTest {

    @Rule
    public SlingContext context = new SlingContext();

    private ResourceRuntimeExtension extension;
    private RenderContext renderContext;
    private SlingBindings requestBindings;

    @Before
    public void setUp() throws PersistenceException {
        extension = context.registerInjectActivateService(ResourceRuntimeExtension.class);
        assertNotNull(extension);

        renderContext = Mockito.mock(RenderContext.class);
        RuntimeObjectModel runtimeObjModel = new AbstractRuntimeObjectModel() {};
        Mockito.when(renderContext.getObjectModel()).thenReturn(runtimeObjModel);

        requestBindings = (SlingBindings) context.jakartaRequest().getAttribute(SlingBindings.class.getName());
        assertNotNull(requestBindings);
        Mockito.when(renderContext.getBindings()).thenReturn(requestBindings);

        ResourceResolver rr = context.resourceResolver();
        context.currentResource(rr.create(rr.getResource("/"), "node1", Map.of()));
    }

    /**
     * Test method for {@link org.apache.sling.scripting.sightly.impl.engine.extension.ResourceRuntimeExtension#call(org.apache.sling.scripting.sightly.render.RenderContext, java.lang.Object[])}.
     */
    @Test
    public void testCall() throws ServletException, IOException {
        // wrong number or arguments
        assertThrows(SightlyException.class, () -> extension.call(renderContext));

        // mock the request dispatcher
        MockJakartaRequestDispatcherFactory mockDispatchFactory =
                Mockito.mock(MockJakartaRequestDispatcherFactory.class);
        context.jakartaRequest().setRequestDispatcherFactory(mockDispatchFactory);

        Map<String, Object> arguments = new HashMap<>();
        // null RequestDispatcher
        assertThrows(SightlyException.class, () -> extension.call(renderContext, "page.html", arguments));

        // not null RequestDispatcher with string path
        RequestDispatcher mockPageRequestDispatcher = Mockito.mock(RequestDispatcher.class);
        Mockito.doAnswer(invocation -> {
                    invocation.getArgument(1, ServletResponse.class).getWriter().print("hello");
                    return null;
                })
                .when(mockPageRequestDispatcher)
                .include(any(ServletRequest.class), any(ServletResponse.class));
        Mockito.when(mockDispatchFactory.getRequestDispatcher(any(Resource.class), any(RequestDispatcherOptions.class)))
                .thenReturn(mockPageRequestDispatcher);
        Object result = extension.call(renderContext, "page.html", arguments);
        assertEquals("hello", result);

        // with Resource path
        Resource pageResource = context.resourceResolver().resolve("/node1/page.html");
        result = extension.call(renderContext, pageResource, arguments);
        assertEquals("hello", result);

        // with includePath that resolves to null, should skip include the resource itself
        //   due to RecursionTooDeepException guard
        result = extension.call(renderContext, "../..", arguments);
        assertEquals("", result);
        // same with resourceType that is not different from current resource
        arguments.put("resourceType", "nt:unstructured");
        result = extension.call(renderContext, "../..", arguments);
        assertEquals("", result);

        // with includePath that resolves to null, should include the resource itself
        //   with the different selectors
        arguments.clear();
        arguments.put("addSelectors", "tidy");
        result = extension.call(renderContext, "../..", arguments);
        assertEquals("hello", result);
        //  should include the resource itself with the different resourceType
        arguments.remove("addSelectors");
        arguments.put("resourceType", "nt:folder");
        result = extension.call(renderContext, "../..", arguments);
        assertEquals("hello", result);

        // with includedResource that resolves to a resource
        result = extension.call(renderContext, "/node1", arguments);
        assertEquals("hello", result);

        // with various options
        arguments.put("resourceType", "nt:folder");
        arguments.put("path", "page");
        arguments.put("prependPath", "/node1/");
        arguments.put("appendPath", "/child.selector0.html");
        arguments.put("selectors", "selector1.selector2");
        arguments.put("removeSelectors", "selector1");
        arguments.put("addSelectors", "selector3");
        arguments.put("requestAttributes", Map.of("attr1", "value1"));
        result = extension.call(renderContext, "", arguments);
        assertEquals("hello", result);

        // same with alternate argument types
        arguments.put("prependPath", "node1");
        arguments.put("appendPath", "child.html");
        arguments.put("removeSelectors", new String[] {"selector1", ""});
        arguments.put("addSelectors", new String[] {"selector3", ""});
        result = extension.call(renderContext, "", arguments);
        assertEquals("hello", result);

        // same with alternate argument type #2
        arguments.put("removeSelectors", new Object());
        arguments.put("addSelectors", new Object());
        result = extension.call(renderContext, "", arguments);
        assertEquals("hello", result);

        // same with alternate argument type #3
        arguments.put("prependPath", null);
        arguments.put("appendPath", null);
        arguments.put("removeSelectors", null);
        arguments.put("addSelectors", null);
        arguments.put("resourceType", "");
        result = extension.call(renderContext, "", arguments);
        assertEquals("hello", result);
    }
}
