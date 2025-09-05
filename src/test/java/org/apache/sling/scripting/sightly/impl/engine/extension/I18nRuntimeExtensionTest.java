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

import java.util.Locale;
import java.util.Map;

import org.apache.sling.api.scripting.SlingBindings;
import org.apache.sling.api.scripting.SlingScriptHelper;
import org.apache.sling.i18n.ResourceBundleProvider;
import org.apache.sling.scripting.sightly.SightlyException;
import org.apache.sling.scripting.sightly.render.AbstractRuntimeObjectModel;
import org.apache.sling.scripting.sightly.render.RenderContext;
import org.apache.sling.scripting.sightly.render.RuntimeObjectModel;
import org.apache.sling.testing.mock.sling.MockResourceBundle;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
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
public class I18nRuntimeExtensionTest {

    @Rule
    public SlingContext context = new SlingContext();

    private I18nRuntimeExtension extension;
    private RenderContext renderContext;

    private SlingBindings requestBindings;

    @Before
    public void setUp() {
        extension = context.registerInjectActivateService(I18nRuntimeExtension.class);
        assertNotNull(extension);

        renderContext = Mockito.mock(RenderContext.class);
        RuntimeObjectModel runtimeObjModel = new AbstractRuntimeObjectModel() {};
        Mockito.when(renderContext.getObjectModel()).thenReturn(runtimeObjModel);

        requestBindings = (SlingBindings) context.jakartaRequest().getAttribute(SlingBindings.class.getName());
        assertNotNull(requestBindings);
        Mockito.when(renderContext.getBindings()).thenReturn(requestBindings);
    }

    /**
     * Test method for {@link org.apache.sling.scripting.sightly.impl.engine.extension.I18nRuntimeExtension#call(org.apache.sling.scripting.sightly.render.RenderContext, java.lang.Object[])}.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCall() {
        // wrong number or arguments
        assertThrows(SightlyException.class, () -> extension.call(renderContext));

        // no translation
        assertEquals("key1", extension.call(renderContext, "key1", Map.of("basename", "test.Resource")));

        // fill in some values
        Locale defaultLocale = context.jakartaRequest().getLocale();
        MockResourceBundle mockRB1 =
                (MockResourceBundle) context.jakartaRequest().getResourceBundle("test.Resource", defaultLocale);
        mockRB1.put("key1", "translatedValue1");
        MockResourceBundle mockRB2 =
                (MockResourceBundle) context.jakartaRequest().getResourceBundle(defaultLocale);
        mockRB2.put("key1", "translatedValue2");
        mockRB2.put("key1 ((myhint1))", "translatedHint2");

        // has translation
        assertEquals("translatedValue2", extension.call(renderContext, "key1", Map.of()));
        // with hint
        assertEquals("translatedHint2", extension.call(renderContext, "key1", Map.of("hint", "myhint1")));
        // with valid locale
        assertEquals(
                "translatedValue2",
                extension.call(renderContext, "key1", Map.of("locale", defaultLocale.toLanguageTag())));
        // with valid locale and no matching key
        assertEquals(
                "invalid1", extension.call(renderContext, "invalid1", Map.of("locale", defaultLocale.toLanguageTag())));
        // with invalid locale
        assertEquals("key1", extension.call(renderContext, "key1", Map.of("locale", "invalid1")));

        //  with basename
        assertEquals("translatedValue1", extension.call(renderContext, "key1", Map.of("basename", "test.Resource")));

        // with basename that does not exist
        assertEquals("key1", extension.call(renderContext, "key1", Map.of("basename", "test2.Resource")));

        // with null resourceBundleProvider
        requestBindings = Mockito.spy(requestBindings);
        Mockito.when(renderContext.getBindings()).thenReturn(requestBindings);
        SlingScriptHelper mockScriptHelper = Mockito.mock(SlingScriptHelper.class);
        Mockito.when(mockScriptHelper.getService(any(Class.class))).thenReturn(null);
        Mockito.when(requestBindings.get(SlingBindings.SLING)).thenReturn(mockScriptHelper);
        assertEquals("key1", extension.call(renderContext, "key1", Map.of()));

        // with null resourceBundle
        ResourceBundleProvider mockRBP = Mockito.mock(ResourceBundleProvider.class);
        Mockito.when(mockScriptHelper.getService(any(Class.class))).thenReturn(mockRBP);
        assertEquals("key1", extension.call(renderContext, "key1", Map.of()));
    }
}
