/*******************************************************************************
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
 ******************************************************************************/
package org.apache.sling.scripting.sightly.impl.engine.extension;

import com.google.common.collect.ImmutableMap;
import org.apache.sling.api.scripting.SlingBindings;
import org.apache.sling.i18n.ResourceBundleProvider;
import org.apache.sling.scripting.sightly.SightlyException;
import org.apache.sling.scripting.sightly.engine.extension.i18n.I18nBasenameProvider;
import org.apache.sling.scripting.sightly.render.AbstractRuntimeObjectModel;
import org.apache.sling.scripting.sightly.render.RenderContext;
import org.apache.sling.scripting.sightly.render.RuntimeObjectModel;
import org.apache.sling.testing.mock.sling.MockResourceBundle;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.script.Bindings;
import javax.script.SimpleBindings;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class I18nRuntimeExtensionTest {

    @Rule
    public final SlingContext slingContext = new SlingContext();


    public static final String BASENAME = "test-basename";
    public static final String KEY = "test-key";
    public static final String DEFAULT_VALUE = "test-value";
    public static final String BASENAME_VALUE = "test-basename-value";

    private RenderContext renderContext;
    private I18nRuntimeExtension subject;

    @Before
    public void before() {
        setupResourceBundles();
        renderContext = setupRenderContext();
    }

    @NotNull
    private RenderContext setupRenderContext() {
        return new RenderContext() {
            @Override
            public RuntimeObjectModel getObjectModel() {
                return new AbstractRuntimeObjectModel() {
                };
            }

            @Override
            public Bindings getBindings() {
                Bindings bindings = new SimpleBindings();
                bindings.put(SlingBindings.SLING, slingContext.slingScriptHelper());
                bindings.put(SlingBindings.REQUEST, slingContext.request());
                return bindings;
            }

            @Override
            public Object call(String s, Object... objects) {
                return null;
            }
        };
    }

    private void setupResourceBundles() {
        ResourceBundleProvider resourceBundleProvider = slingContext.getService(ResourceBundleProvider.class);
        MockResourceBundle defaultResourceBundle = (MockResourceBundle) resourceBundleProvider.getResourceBundle(slingContext.request().getLocale());
        MockResourceBundle basenameResourceBundle = (MockResourceBundle) resourceBundleProvider.getResourceBundle(BASENAME, slingContext.request().getLocale());
        defaultResourceBundle.put(KEY, DEFAULT_VALUE);
        basenameResourceBundle.put(KEY, BASENAME_VALUE);
    }


    @Test
    public void testNonExistingKey() {
        subject = slingContext.registerInjectActivateService(new I18nRuntimeExtension());
        String key = "non-existing";
        assertEquals("Key should not change when it does not exist", key, subject.call(renderContext, key, Collections.emptyMap()));
    }

    @Test(expected = SightlyException.class)
    public void testMissingArguments() {
        subject = slingContext.registerInjectActivateService(new I18nRuntimeExtension());
        subject.call(renderContext, "fails");
    }

    @Test
    public void testDefaultValue() {
        subject = slingContext.registerInjectActivateService(new I18nRuntimeExtension());
        assertEquals(DEFAULT_VALUE, subject.call(renderContext, KEY, Collections.emptyMap()));
    }

    @Test
    public void testBasenameValue() {
        subject = slingContext.registerInjectActivateService(new I18nRuntimeExtension());
        assertEquals(BASENAME_VALUE, subject.call(renderContext, KEY, ImmutableMap.of("basename", BASENAME)));
    }

    @Test
    public void testBasenameThroughProviderValue() {
        slingContext.registerService(I18nBasenameProvider.class, new MockI18nBasenameProvider(BASENAME));
        subject = slingContext.registerInjectActivateService(new I18nRuntimeExtension());
        assertEquals(BASENAME_VALUE, subject.call(renderContext, KEY, Collections.emptyMap()));
    }

    @Test
    public void testEmptyBasenameThroughProviderValue() {
        slingContext.registerService(I18nBasenameProvider.class, new MockI18nBasenameProvider(null));
        subject = slingContext.registerInjectActivateService(new I18nRuntimeExtension());
        assertEquals(DEFAULT_VALUE, subject.call(renderContext, KEY, Collections.emptyMap()));
    }

    static class MockI18nBasenameProvider implements I18nBasenameProvider {

        private final String basename;

        public MockI18nBasenameProvider(String basename) {
            this.basename = basename;
        }

        @Override
        public @Nullable String getBasename(RenderContext renderContext) {
            return basename;
        }
    }


}
