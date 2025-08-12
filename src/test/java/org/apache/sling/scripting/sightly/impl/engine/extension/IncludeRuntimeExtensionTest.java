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

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.scripting.SlingBindings;
import org.apache.sling.api.scripting.SlingScriptHelper;
import org.apache.sling.api.servlets.ServletResolver;
import org.apache.sling.scripting.sightly.SightlyException;
import org.apache.sling.scripting.sightly.render.AbstractRuntimeObjectModel;
import org.apache.sling.scripting.sightly.render.RenderContext;
import org.apache.sling.scripting.sightly.render.RuntimeObjectModel;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 *
 */
public class IncludeRuntimeExtensionTest {

    @Rule
    public SlingContext context = new SlingContext();

    private IncludeRuntimeExtension extension;
    private RenderContext renderContext;
    private SlingBindings requestBindings;

    private SlingScriptHelper mockScriptHelper;

    @Before
    public void setUp() throws PersistenceException {
        extension = context.registerInjectActivateService(IncludeRuntimeExtension.class);
        assertNotNull(extension);

        renderContext = Mockito.mock(RenderContext.class);
        RuntimeObjectModel runtimeObjModel = new AbstractRuntimeObjectModel() {};
        Mockito.when(renderContext.getObjectModel()).thenReturn(runtimeObjModel);

        requestBindings = (SlingBindings) context.jakartaRequest().getAttribute(SlingBindings.class.getName());
        assertNotNull(requestBindings);

        requestBindings = Mockito.spy(requestBindings);
        mockScriptHelper = Mockito.mock(SlingScriptHelper.class);
        Mockito.when(requestBindings.get(SlingBindings.SLING)).thenReturn(mockScriptHelper);
        Mockito.when(renderContext.getBindings()).thenReturn(requestBindings);

        ResourceResolver rr = context.resourceResolver();
        context.currentResource(rr.create(rr.getResource("/"), "node1", Map.of()));
    }

    /**
     * Test method for {@link org.apache.sling.scripting.sightly.impl.engine.extension.IncludeRuntimeExtension#call(org.apache.sling.scripting.sightly.render.RenderContext, java.lang.Object[])}.
     */
    @SuppressWarnings("deprecation")
    @Test
    public void testCall() throws ServletException, IOException, javax.servlet.ServletException {
        // wrong number or arguments
        assertThrows(SightlyException.class, () -> extension.call(renderContext));

        Map<String, Object> arguments = new HashMap<>();
        // Sling ServletResolver service is unavailable
        assertThrows(SightlyException.class, () -> extension.call(renderContext, "page.html", arguments));

        ServletResolver mockServletResolver = Mockito.mock(ServletResolver.class);
        Mockito.when(mockScriptHelper.getService(ServletResolver.class)).thenReturn(mockServletResolver);

        // empty script
        assertThrows(SightlyException.class, () -> extension.call(renderContext, "", arguments));

        // Failed to locate script
        assertThrows(SightlyException.class, () -> extension.call(renderContext, "page.html", arguments));

        // jakarta Servlet resource
        Servlet mockJakartaServlet = Mockito.mock(Servlet.class);
        Mockito.doAnswer(invocation -> {
                    invocation.getArgument(1, ServletResponse.class).getWriter().print("hello");
                    return null;
                })
                .when(mockJakartaServlet)
                .service(any(ServletRequest.class), any(ServletResponse.class));
        Mockito.when(mockServletResolver.resolve(any(Resource.class), eq("page.html")))
                .thenReturn(mockJakartaServlet);
        Object result = extension.call(renderContext, "page.html", arguments);
        assertEquals("hello", result);

        // jakarta Servlet throws exception during service
        Mockito.doThrow(ServletException.class)
                .when(mockJakartaServlet)
                .service(any(ServletRequest.class), any(ServletResponse.class));
        assertThrows(SightlyException.class, () -> extension.call(renderContext, "page.html", arguments));

        // javax Servlet resource
        javax.servlet.Servlet mockJavaxServlet = Mockito.mock(javax.servlet.Servlet.class);
        Mockito.doAnswer(invocation -> {
                    invocation
                            .getArgument(1, javax.servlet.ServletResponse.class)
                            .getWriter()
                            .print("hello2");
                    return null;
                })
                .when(mockJavaxServlet)
                .service(any(javax.servlet.ServletRequest.class), any(javax.servlet.ServletResponse.class));
        Mockito.when(mockServletResolver.resolveServlet(any(Resource.class), eq("page2.html")))
                .thenReturn(mockJavaxServlet);
        result = extension.call(renderContext, "page2.html", arguments);
        assertEquals("hello2", result);

        // javax Servlet throws exception during service
        Mockito.doThrow(javax.servlet.ServletException.class)
                .when(mockJavaxServlet)
                .service(any(javax.servlet.ServletRequest.class), any(javax.servlet.ServletResponse.class));
        assertThrows(SightlyException.class, () -> extension.call(renderContext, "page2.html", arguments));

        // with prepend / append path arguments
        Mockito.doAnswer(invocation -> {
                    invocation.getArgument(1, ServletResponse.class).getWriter().print("hello3");
                    return null;
                })
                .when(mockJakartaServlet)
                .service(any(ServletRequest.class), any(ServletResponse.class));
        Mockito.when(mockServletResolver.resolve(any(Resource.class), eq("prepend1/page3.html/append1")))
                .thenReturn(mockJakartaServlet);
        arguments.put("prependPath", "prepend1");
        arguments.put("appendPath", "append1");
        result = extension.call(renderContext, "page3.html", arguments);
        assertEquals("hello3", result);

        // path with leading + trailing slashes
        result = extension.call(renderContext, "/page3.html/", arguments);
        assertEquals("hello3", result);

        // args with leading + trailing slashes
        arguments.put("prependPath", "prepend1/");
        arguments.put("appendPath", "/append1");
        result = extension.call(renderContext, "page3.html", arguments);
        assertEquals("hello3", result);
    }
}
