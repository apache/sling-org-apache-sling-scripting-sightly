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

import java.util.Map;

import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.scripting.LazyBindings;
import org.apache.sling.api.scripting.SlingBindings;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 *
 */
public class BindingsUtilsTest {

    @Rule
    public SlingContext context = new SlingContext();

    private SlingBindings slingBindings;

    @Before
    public void setUp() {
        slingBindings = (SlingBindings) context.jakartaRequest().getAttribute(SlingBindings.class.getName());
        assertNotNull(slingBindings);
    }

    /**
     * Test method for {@link org.apache.sling.scripting.sightly.impl.utils.BindingsUtils#getResource(javax.script.Bindings)}.
     */
    @Test
    public void testGetResource() throws PersistenceException {
        ResourceResolver rr = context.resourceResolver();
        context.currentResource(rr.create(rr.getResource("/"), "node1", Map.of()));
        assertNotNull(BindingsUtils.getResource(slingBindings));
    }

    /**
     * Test method for {@link org.apache.sling.scripting.sightly.impl.utils.BindingsUtils#getRequest(javax.script.Bindings)}.
     * @deprecated use {@link #testGetJakartaRequest()} instead
     */
    @Deprecated(since = "2.0.0-1.4.0")
    @Test
    public void testGetRequest() {
        assertNotNull(BindingsUtils.getRequest(slingBindings));
    }

    /**
     * Test method for {@link org.apache.sling.scripting.sightly.impl.utils.BindingsUtils#getResponse(javax.script.Bindings)}.
     * @deprecated use {@link #testGetJakartaResponse()} instead
     */
    @Deprecated(since = "2.0.0-1.4.0")
    @Test
    public void testGetResponse() {
        assertNotNull(BindingsUtils.getResponse(slingBindings));
    }

    /**
     * Test method for {@link org.apache.sling.scripting.sightly.impl.utils.BindingsUtils#getJakartaRequest(javax.script.Bindings)}.
     */
    @Test
    public void testGetJakartaRequest() {
        assertNotNull(BindingsUtils.getJakartaRequest(slingBindings));
    }

    /**
     * Test method for {@link org.apache.sling.scripting.sightly.impl.utils.BindingsUtils#getJakartaResponse(javax.script.Bindings)}.
     */
    @Test
    public void testGetJakartaResponse() {
        assertNotNull(BindingsUtils.getJakartaResponse(slingBindings));
    }

    /**
     * Test method for {@link org.apache.sling.scripting.sightly.impl.utils.BindingsUtils#getHelper(javax.script.Bindings)}.
     */
    @Test
    public void testGetHelper() {
        assertNotNull(BindingsUtils.getHelper(slingBindings));
    }

    /**
     * Test method for {@link org.apache.sling.scripting.sightly.impl.utils.BindingsUtils#merge(javax.script.Bindings, javax.script.Bindings)}.
     */
    @Test
    public void testMerge() {
        LazyBindings otherBindings = new LazyBindings();
        otherBindings.put("key1", "value1");
        Bindings merged = BindingsUtils.merge(slingBindings, otherBindings);
        assertEquals("value1", merged.get("key1"));
    }
}
