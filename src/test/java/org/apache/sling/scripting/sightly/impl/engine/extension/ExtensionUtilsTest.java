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

import java.util.HashMap;
import java.util.Map;

import org.apache.sling.scripting.sightly.SightlyException;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.apache.sling.testing.mock.sling.servlet.MockSlingJakartaHttpServletRequest;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class ExtensionUtilsTest {

    @Rule
    public SlingContext context = new SlingContext();

    /**
     * Test method for {@link org.apache.sling.scripting.sightly.impl.engine.extension.ExtensionUtils#checkArgumentCount(java.lang.String, java.lang.Object[], int)}.
     */
    @Test
    public void testCheckArgumentCount() {
        ExtensionUtils.checkArgumentCount("test", new Object[0], 0);
        assertThrows(SightlyException.class, () -> ExtensionUtils.checkArgumentCount("test", new Object[0], 2));
    }

    /**
     * Test method for {@link org.apache.sling.scripting.sightly.impl.engine.extension.ExtensionUtils#setRequestAttributes(org.apache.sling.api.SlingJakartaHttpServletRequest, java.util.Map)}.
     */
    @Test
    public void testSetRequestAttributes() {
        MockSlingJakartaHttpServletRequest jakartaRequest = context.jakartaRequest();
        jakartaRequest.setAttribute("key1", "value1");

        Map<String, Object> attrs = new HashMap<>();
        attrs.put("key1", "newvalue1");
        attrs.put("key2", null);

        // either param is null does nothing
        assertTrue(ExtensionUtils.setRequestAttributes(null, null).isEmpty());
        assertTrue(ExtensionUtils.setRequestAttributes(null, attrs).isEmpty());
        assertTrue(ExtensionUtils.setRequestAttributes(jakartaRequest, null).isEmpty());

        Map<String, Object> requestAttributes = ExtensionUtils.setRequestAttributes(jakartaRequest, attrs);
        assertEquals(2, requestAttributes.size());
        assertEquals("value1", requestAttributes.get("key1"));
        assertNull(requestAttributes.get("key2"));
    }
}
