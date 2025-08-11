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

import javax.script.Bindings;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.adapter.Adaptable;
import org.apache.sling.api.adapter.AdapterFactory;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.scripting.SlingBindings;
import org.apache.sling.scripting.sightly.impl.engine.bundled.BundledUnitManagerImpl;
import org.apache.sling.scripting.sightly.impl.engine.compiled.SlingHTLMasterCompiler;
import org.apache.sling.scripting.sightly.impl.engine.extension.use.testmodels.AbstractModel;
import org.apache.sling.scripting.sightly.impl.engine.extension.use.testmodels.EnumModel;
import org.apache.sling.scripting.sightly.impl.engine.extension.use.testmodels.InterfaceModel;
import org.apache.sling.scripting.sightly.impl.engine.extension.use.testmodels.MockRenderUnit;
import org.apache.sling.scripting.sightly.impl.engine.extension.use.testmodels.OtherModel;
import org.apache.sling.scripting.sightly.impl.engine.extension.use.testmodels.PojoModel;
import org.apache.sling.scripting.sightly.impl.engine.extension.use.testmodels.PojoUseModel;
import org.apache.sling.scripting.sightly.impl.engine.extension.use.testmodels.ResourceModel;
import org.apache.sling.scripting.sightly.impl.engine.extension.use.testmodels.ResourceModel2;
import org.apache.sling.scripting.sightly.impl.engine.extension.use.testmodels.SlingHttpServletRequestModel;
import org.apache.sling.scripting.sightly.impl.engine.extension.use.testmodels.SlingJakartaHttpServletRequestModel;
import org.apache.sling.scripting.sightly.render.RenderContext;
import org.apache.sling.scripting.sightly.render.RenderUnit;
import org.apache.sling.scripting.sightly.use.ProviderOutcome;
import org.apache.sling.testing.mock.osgi.MockBundle;
import org.apache.sling.testing.mock.osgi.MockOsgi;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.BundleEvent;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 *
 */
public class JavaUseProviderTest {

    @Rule
    public SlingContext context = new SlingContext();

    private JavaUseProvider provider;
    private SlingBindings arguments;

    private RenderContext renderContext;

    private SlingBindings requestBindings;

    private BundledUnitManagerImpl mockBundledUnitManager;

    @Before
    public void setUp() throws PersistenceException {
        mockBundledUnitManager =
                context.registerService(BundledUnitManagerImpl.class, Mockito.mock(BundledUnitManagerImpl.class));
        Mockito.when(mockBundledUnitManager.getBundledRenderUnitClassloader(any(Bindings.class)))
                .thenReturn(getClass().getClassLoader());
        provider = context.registerInjectActivateService(JavaUseProvider.class);
        assertNotNull(provider);

        arguments = new SlingBindings();
        assertNotNull(arguments);
        // for coverage of request attribute juggling before and after provide
        arguments.put("key1", "value1");
        arguments.put("key2", "value2");
        context.jakartaRequest().setAttribute("key1", "requestValue1");

        ResourceResolver rr = context.resourceResolver();
        context.currentResource(rr.create(rr.getResource("/"), "node1", Map.of()));

        renderContext = Mockito.mock(RenderContext.class);
        requestBindings = (SlingBindings) context.jakartaRequest().getAttribute(SlingBindings.class.getName());
        assertNotNull(requestBindings);
        Mockito.when(renderContext.getBindings()).thenReturn(requestBindings);
    }

    /**
     * Test method for {@link org.apache.sling.scripting.sightly.impl.engine.extension.use.JavaUseProvider#provide(java.lang.String, org.apache.sling.scripting.sightly.render.RenderContext, javax.script.Bindings)}.
     */
    @Test
    public void testProvideWithInvalidClassName() {
        ProviderOutcome outcome = provider.provide("Not Valid", renderContext, arguments);
        assertTrue(outcome.isFailure());
    }

    @Test
    public void testProvideWithNullBundledRenderUnitClassLoader() {
        Mockito.when(mockBundledUnitManager.getBundledRenderUnitClassloader(any(Bindings.class)))
                .thenReturn(null);

        ProviderOutcome outcome =
                provider.provide(SlingJakartaHttpServletRequestModel.class.getName(), renderContext, arguments);
        assertTrue(outcome.isFailure());
    }

    @Test
    public void testProvideWithCaughtException() {
        Mockito.when(mockBundledUnitManager.getBundledRenderUnitClassloader(any(Bindings.class)))
                .thenThrow(RuntimeException.class);

        ProviderOutcome outcome =
                provider.provide(SlingJakartaHttpServletRequestModel.class.getName(), renderContext, arguments);
        assertTrue(outcome.isFailure());
    }

    @Test
    public void testProvideForBundleHeaderDeclaredSlingModel() {
        simulateBundleStartedWithSlingModelClasses();

        // sling model with javax servlet adaptable
        ProviderOutcome outcome =
                provider.provide(SlingHttpServletRequestModel.class.getName(), renderContext, arguments);
        assertTrue(outcome.isSuccess());

        // sling model with jakarta servlet adaptable
        outcome = provider.provide(SlingJakartaHttpServletRequestModel.class.getName(), renderContext, arguments);
        assertTrue(outcome.isSuccess());

        // sling model with resource adaptable
        outcome = provider.provide(ResourceModel.class.getName(), renderContext, arguments);
        assertTrue(outcome.isSuccess());

        // sling model that throws exception should fail
        outcome = provider.provide(ResourceModel2.class.getName(), renderContext, arguments);
        assertTrue(outcome.isFailure());

        // sling model with other adaptable should fail
        outcome = provider.provide(OtherModel.class.getName(), renderContext, arguments);
        assertTrue(outcome.isFailure());

        // sling model with adaptable argument
        arguments.put("adaptable", context.jakartaRequest());
        outcome = provider.provide(SlingJakartaHttpServletRequestModel.class.getName(), renderContext, arguments);
        assertTrue(outcome.isSuccess());

        // sling model with adaptable argument of the wrong type
        arguments.put("adaptable", Mockito.mock(Adaptable.class));
        outcome = provider.provide(OtherModel.class.getName(), renderContext, arguments);
        assertTrue(outcome.isFailure());
    }

    @Test
    public void testProvideForBundleHeaderDeclaredSlingModelWithoutBindingValues() {
        // use a bindings that doesn't resolve anything
        SlingBindings mockSlingBindings = Mockito.mock(SlingBindings.class);
        Mockito.when(renderContext.getBindings()).thenReturn(mockSlingBindings);

        simulateBundleStartedWithSlingModelClasses();

        // sling model with javax servlet adaptable
        ProviderOutcome outcome =
                provider.provide(SlingHttpServletRequestModel.class.getName(), renderContext, arguments);
        assertTrue(outcome.isFailure());

        // sling model with jakarta servlet adaptable
        outcome = provider.provide(SlingJakartaHttpServletRequestModel.class.getName(), renderContext, arguments);
        assertTrue(outcome.isFailure());

        // sling model with resource adaptable
        outcome = provider.provide(ResourceModel.class.getName(), renderContext, arguments);
        assertTrue(outcome.isFailure());
    }

    @Test
    public void testProvideForOsgiService() {
        Mockito.when(mockBundledUnitManager.getServiceForBundledRenderUnit(
                        any(Bindings.class), eq(SlingJakartaHttpServletRequestModel.class)))
                .thenReturn(new SlingJakartaHttpServletRequestModel());

        ProviderOutcome outcome =
                provider.provide(SlingJakartaHttpServletRequestModel.class.getName(), renderContext, arguments);
        assertTrue(outcome.isSuccess());
    }

    @Test
    public void testProvideWithRelativeClassName() {
        // null RenderUnit fails
        ProviderOutcome outcome =
                provider.provide(SlingJakartaHttpServletRequestModel.class.getSimpleName(), renderContext, arguments);
        assertTrue(outcome.isFailure());

        // simulate non-null RenderUnit
        RenderUnit mockRenderUnit = new MockRenderUnit();
        Mockito.when(mockBundledUnitManager.getRenderUnit(requestBindings)).thenReturn(mockRenderUnit);
        outcome = provider.provide(SlingJakartaHttpServletRequestModel.class.getSimpleName(), renderContext, arguments);
        assertTrue(outcome.isSuccess());
    }

    @Test
    public void testProvideForModelAdaptedFromAdaptable() {
        registerTestModelAdapterManagers();

        // sling model with javax servlet adaptable
        ProviderOutcome outcome =
                provider.provide(SlingHttpServletRequestModel.class.getName(), renderContext, arguments);
        assertTrue(outcome.isSuccess());

        // sling model with jakarta servlet adaptable
        outcome = provider.provide(SlingJakartaHttpServletRequestModel.class.getName(), renderContext, arguments);
        assertTrue(outcome.isSuccess());

        // sling model with resource adaptable
        outcome = provider.provide(ResourceModel.class.getName(), renderContext, arguments);
        assertTrue(outcome.isSuccess());

        // sling model with adaptable argument
        arguments.put("adaptable", context.jakartaRequest());
        outcome = provider.provide(SlingJakartaHttpServletRequestModel.class.getName(), renderContext, arguments);
        assertTrue(outcome.isSuccess());
    }

    @Test
    public void testProvideForPojo() {
        // clear the field so we don't even consider that path
        ReflectionTools.setFieldWithReflection(provider, "modelFactory", null);

        // use a bindings that doesn't resolve anything
        SlingBindings mockSlingBindings = Mockito.mock(SlingBindings.class);
        Mockito.when(renderContext.getBindings()).thenReturn(mockSlingBindings);

        // interface
        ProviderOutcome outcome = provider.provide(InterfaceModel.class.getName(), renderContext, arguments);
        assertTrue(outcome.isFailure());
        assertTrue(outcome.getCause() instanceof IllegalArgumentException);

        // abstract class
        outcome = provider.provide(AbstractModel.class.getName(), renderContext, arguments);
        assertTrue(outcome.isFailure());
        assertTrue(outcome.getCause() instanceof IllegalArgumentException);

        // enum
        outcome = provider.provide(EnumModel.class.getName(), renderContext, arguments);
        assertTrue(outcome.isSuccess());

        // pojo
        outcome = provider.provide(PojoModel.class.getName(), renderContext, arguments);
        assertTrue(outcome.isSuccess());

        // pojo that implements Use
        outcome = provider.provide(PojoUseModel.class.getName(), renderContext, arguments);
        assertTrue(outcome.isSuccess());
    }

    @Test
    public void testProvideWithSlingHTLMasterCompiler() {
        // to bypass the first code path
        Mockito.when(mockBundledUnitManager.getBundledRenderUnitClassloader(any(Bindings.class)))
                .thenReturn(null);

        // simulate the SlingHTLMasterCompiler service
        SlingHTLMasterCompiler mockSlingHTLMasterCompiler = Mockito.mock(SlingHTLMasterCompiler.class);
        context.registerService(SlingHTLMasterCompiler.class, mockSlingHTLMasterCompiler);

        // re-create to make sure the injected SlingHTLMasterCompiler field is set
        provider = context.registerInjectActivateService(JavaUseProvider.class);
        assertNotNull(provider);

        // try non-Use resource backed path
        Mockito.when(mockSlingHTLMasterCompiler.getResourceBackedUseObject(renderContext, PojoModel.class.getName()))
                .thenReturn(new PojoModel());
        ProviderOutcome outcome = provider.provide(PojoModel.class.getName(), renderContext, arguments);
        assertTrue(outcome.isSuccess());

        // try Use resource backed path
        Mockito.when(mockSlingHTLMasterCompiler.getResourceBackedUseObject(renderContext, PojoUseModel.class.getName()))
                .thenReturn(new PojoUseModel());
        outcome = provider.provide(PojoUseModel.class.getName(), renderContext, arguments);
        assertTrue(outcome.isSuccess());

        // try classloader loaded path
        Mockito.when(mockSlingHTLMasterCompiler.getResourceBackedUseObject(renderContext, PojoUseModel.class.getName()))
                .thenReturn(null);
        Mockito.when(mockSlingHTLMasterCompiler.getClassLoader())
                .thenReturn(getClass().getClassLoader());
        outcome = provider.provide(PojoUseModel.class.getName(), renderContext, arguments);
        assertTrue(outcome.isSuccess());

        // try failing classloader loaded path
        Mockito.when(mockSlingHTLMasterCompiler.getResourceBackedUseObject(renderContext, "package1.Invalid"))
                .thenReturn(null);
        outcome = provider.provide("package1.Invalid", renderContext, arguments);
        assertTrue(outcome.isFailure());

        // try no sling script helper path
        SlingBindings mockSlingBindings = Mockito.mock(SlingBindings.class);
        Mockito.when(renderContext.getBindings()).thenReturn(mockSlingBindings);
        outcome = provider.provide("package1.Invalid", renderContext, arguments);
        assertTrue(outcome.isFailure());
    }

    @SuppressWarnings("deprecation")
    private void registerTestModelAdapterManagers() {
        context.registerService(
                AdapterFactory.class,
                new TestModelsAdapterFactory(),
                Map.of(
                        AdapterFactory.ADAPTER_CLASSES,
                        new String[] {SlingHttpServletRequestModel.class.getName()},
                        AdapterFactory.ADAPTABLE_CLASSES,
                        new String[] {org.apache.sling.api.SlingHttpServletRequest.class.getName()}));
        context.registerService(
                AdapterFactory.class,
                new TestModelsAdapterFactory(),
                Map.of(
                        AdapterFactory.ADAPTER_CLASSES,
                        new String[] {SlingJakartaHttpServletRequestModel.class.getName()},
                        AdapterFactory.ADAPTABLE_CLASSES,
                        new String[] {SlingJakartaHttpServletRequest.class.getName()}));
        context.registerService(
                AdapterFactory.class,
                new TestModelsAdapterFactory(),
                Map.of(
                        AdapterFactory.ADAPTER_CLASSES,
                        new String[] {ResourceModel.class.getName()},
                        AdapterFactory.ADAPTABLE_CLASSES,
                        new String[] {Resource.class.getName()}));
    }

    private void simulateBundleStartedWithSlingModelClasses() {
        MockBundle mockBundle = (MockBundle) context.bundleContext().getBundle();
        mockBundle.setHeaders(Map.of(
                "Sling-Model-Classes",
                String.join(
                        ",",
                        SlingHttpServletRequestModel.class.getName(),
                        SlingJakartaHttpServletRequestModel.class.getName(),
                        ResourceModel.class.getName(),
                        OtherModel.class.getName(),
                        ResourceModel2.class.getName())));
        MockOsgi.sendBundleEvent(context.bundleContext(), new BundleEvent(BundleEvent.STARTED, mockBundle));
    }

    private static class TestModelsAdapterFactory implements AdapterFactory {
        @Override
        public <T> T getAdapter(final Object adaptable, final Class<T> type) {
            try {
                return type.getDeclaredConstructor().newInstance();
            } catch (InstantiationException
                    | IllegalAccessException
                    | IllegalArgumentException
                    | InvocationTargetException
                    | NoSuchMethodException
                    | SecurityException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
