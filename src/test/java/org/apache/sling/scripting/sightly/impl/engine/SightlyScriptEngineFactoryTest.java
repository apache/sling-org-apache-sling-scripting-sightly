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

import javax.script.ScriptEngineFactory;
import javax.script.SimpleScriptContext;

import org.apache.sling.scripting.sightly.impl.engine.bundled.BundledUnitManagerImpl;
import org.apache.sling.scripting.sightly.impl.engine.runtime.RenderContextImpl;
import org.apache.sling.scripting.sightly.render.RenderContext;
import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class SightlyScriptEngineFactoryTest {

    @Rule
    public OsgiContext context = new OsgiContext();

    @Test
    public void testLegacyBooleanCastingFalse() {
        ExtensionRegistryService extensionRegistryService = mock(ExtensionRegistryService.class);
        context.registerService(BundledUnitManagerImpl.class, mock(BundledUnitManagerImpl.class));
        context.registerService(ExtensionRegistryService.class, extensionRegistryService);
        context.registerInjectActivateService(new SightlyEngineConfiguration(), "legacyBooleanCasting", false);
        context.registerInjectActivateService(new SightlyScriptEngineFactory());

        SightlyScriptEngineFactory factory = (SightlyScriptEngineFactory) context.getService(ScriptEngineFactory.class);
        assertNotNull(factory);
        RenderContext renderContext =
                new RenderContextImpl(factory.getConfiguration(), extensionRegistryService, new SimpleScriptContext());
        assertTrue(renderContext.getObjectModel().toBoolean("false"));
    }

    @Test
    public void testLegacyBooleanCasting() {
        ExtensionRegistryService extensionRegistryService = mock(ExtensionRegistryService.class);
        context.registerService(BundledUnitManagerImpl.class, mock(BundledUnitManagerImpl.class));
        context.registerService(ExtensionRegistryService.class, extensionRegistryService);
        context.registerInjectActivateService(new SightlyEngineConfiguration(), "legacyBooleanCasting", true);
        context.registerInjectActivateService(new SightlyScriptEngineFactory());

        SightlyScriptEngineFactory factory = (SightlyScriptEngineFactory) context.getService(ScriptEngineFactory.class);
        assertNotNull(factory);
        RenderContext renderContext =
                new RenderContextImpl(factory.getConfiguration(), extensionRegistryService, new SimpleScriptContext());
        assertFalse(renderContext.getObjectModel().toBoolean("false"));
    }
}
