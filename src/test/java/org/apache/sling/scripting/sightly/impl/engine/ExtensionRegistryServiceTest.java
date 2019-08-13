/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 ~ Licensed to the Apache Software Foundation (ASF) under one
 ~ or more contributor license agreements.  See the NOTICE file
 ~ distributed with this work for additional information
 ~ regarding copyright ownership.  The ASF licenses this file
 ~ to you under the Apache License, Version 2.0 (the
 ~ "License"); you may not use this file except in compliance
 ~ with the License.  You may obtain a copy of the License at
 ~
 ~   http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing,
 ~ software distributed under the License is distributed on an
 ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 ~ KIND, either express or implied.  See the License for the
 ~ specific language governing permissions and limitations
 ~ under the License.
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
package org.apache.sling.scripting.sightly.impl.engine;

import java.util.Hashtable;
import java.util.Map;

import org.apache.sling.scripting.sightly.extension.RuntimeExtension;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

public class ExtensionRegistryServiceTest {

    @Rule
    public final SlingContext slingContext = new SlingContext();

    @Before
    public void before() {
        slingContext.registerInjectActivateService(new ExtensionRegistryService());
    }

    @Test
    public void testRegistryForExtensionRanking() throws Exception {
        RuntimeExtension extension1 = mock(RuntimeExtension.class);
        ServiceRegistration<RuntimeExtension> registration1 = slingContext.bundleContext().registerService(
                RuntimeExtension.class,
                extension1,
                new Hashtable<String, Object>() {{
                    put(RuntimeExtension.NAME, "test");
                }}
        );


        RuntimeExtension extension2 = mock(RuntimeExtension.class);
        ServiceRegistration<RuntimeExtension> registration2 = slingContext.bundleContext().registerService(
                RuntimeExtension.class,
                extension2,
                new Hashtable<String, Object>() {{
                    put(Constants.SERVICE_RANKING, 2);
                    put(RuntimeExtension.NAME, "test");
                }}
        );

        ExtensionRegistryService registryService = slingContext.getService(ExtensionRegistryService.class);
        assertNotNull("The ExtensionRegistryService should have been registered.", registryService);

        assertEquals(1, registryService.extensions().size());
        assertEquals(extension2, registryService.extensions().get("test"));

        registration2.unregister();
        assertEquals(1, registryService.extensions().size());
        assertEquals(extension1, registryService.extensions().get("test"));

        registration1.unregister();
        assertEquals(0, registryService.extensions().size());
    }


}
