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
package org.apache.sling.scripting.sightly.impl.engine.bundled;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.commons.compiler.source.JavaEscapeHelper;
import org.apache.sling.scripting.api.CachedScript;
import org.apache.sling.scripting.api.ScriptCache;
import org.apache.sling.scripting.bundle.tracker.BundledRenderUnit;
import org.apache.sling.scripting.bundle.tracker.ResourceType;
import org.apache.sling.scripting.bundle.tracker.TypeProvider;
import org.apache.sling.scripting.core.ScriptNameAwareReader;
import org.apache.sling.scripting.sightly.impl.engine.SightlyCompiledScript;
import org.apache.sling.scripting.sightly.impl.engine.SightlyScriptEngine;
import org.apache.sling.scripting.sightly.impl.utils.BindingsUtils;
import org.apache.sling.scripting.sightly.render.RenderUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@code BundledUnitManager} is an optional service, which is made available only if the {@link
 * org.apache.sling.scripting.bundle.tracker} APIs are available. This service allows various components to work with {@link
 * org.apache.sling.scripting.bundle.tracker.BundledRenderUnit} instance and perform dependency resolution based on their availability in
 * the {@link Bindings} maps passed to the HTL Script Engine.
 */
@Component(
        service = {}
        /*
         * this component will register itself as a service only if the org.apache.sling.scripting.bundle.tracker API is present
         */
        )
public class BundledUnitManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(BundledUnitManager.class);

    private final ServiceRegistration<?> serviceRegistration;

    @Reference
    private ScriptEngineManager scriptEngineManager;

    @Reference
    private ScriptCache scriptCache;

    @Activate
    public BundledUnitManager(BundleContext bundleContext) {
        serviceRegistration = register(bundleContext);
    }

    @Deactivate
    public void deactivate() {
        if (serviceRegistration != null) {
            serviceRegistration.unregister();
        }
    }


    /**
     * Given a {@link Bindings} map, this method will check if the {@code bindings} contain a value for the {@link
     * BundledRenderUnit#VARIABLE} property and return the object provided by {@link BundledRenderUnit#getUnit()} if this is an instance of
     * a {@link RenderUnit}.
     *
     * @param bindings the bindings passed initially to the HTL Script Engine
     * @return a {@link RenderUnit} if one is found, {@code null} otherwise
     */
    @Nullable
    public RenderUnit getRenderUnit(@NotNull Bindings bindings) {
        BundledRenderUnit bundledRenderUnit = getBundledRenderUnit(bindings);
        if (bundledRenderUnit != null) {
            Object renderUnit = bundledRenderUnit.getUnit();
            if (renderUnit instanceof RenderUnit) {
                return (RenderUnit) renderUnit;
            }
        }
        return null;
    }

    /**
     * <p>
     * Given a {@link Bindings} map, this method will check if the {@code bindings} contain a value for the {@link
     * BundledRenderUnit#VARIABLE} property and if the object provided by {@link BundledRenderUnit#getUnit()} is an instance of a {@link
     * RenderUnit}. If so, this service will try to locate another {@link RenderUnit} based on the passed {@code identifier} and the
     * coordinates of the {@link RenderUnit} found in the {@code bindings} map.
     * </p>
     * <p>
     * This method is suited for finding template libraries (collections of templates provided by the same {@link RenderUnit}).
     * </p>
     *
     * @param bindings   the bindings passed initially to the HTL Script Engine
     * @param identifier the identifier of the {@link RenderUnit} that has to be retrieved and returned
     * @return a {@link RenderUnit} if one is found, {@code null} otherwise
     */
    @Nullable
    public RenderUnit getRenderUnit(@NotNull Bindings bindings, @NotNull String identifier) {
        BundledRenderUnit bundledRenderUnit = getBundledRenderUnit(bindings);
        Resource currentResource = BindingsUtils.getResource(bindings);
        if (currentResource != null && bundledRenderUnit != null) {
            boolean absolute = identifier.charAt(0) == '/';
            for (TypeProvider provider : bundledRenderUnit.getTypeProviders()) {
                for (ResourceType type : provider.getBundledRenderUnitCapability().getResourceTypes()) {
                    StringBuilder renderUnitIdentifier = new StringBuilder(identifier);
                    if (!absolute) {
                        renderUnitIdentifier = renderUnitIdentifier.insert(0, type.toString() + "/");
                    }
                    if (provider.isPrecompiled()) {
                        String classResourcePath = renderUnitIdentifier.toString();
                        if (classResourcePath.startsWith("/")) {
                            classResourcePath = classResourcePath.substring(1);
                        }
                        String className = JavaEscapeHelper.makeJavaPackage(classResourcePath);
                        try {
                            Class<?> clazz = provider.getBundle().loadClass(className);
                            if (clazz.getSuperclass() == RenderUnit.class) {
                                return (RenderUnit) clazz.getDeclaredConstructor().newInstance();
                            }
                        } catch (RuntimeException e) {
                            throw e;
                        } catch (Exception ignored) {
                            // do nothing here
                        }
                    } else {
                        String scriptResourcePath = renderUnitIdentifier.toString();
                        if (scriptResourcePath.startsWith("/")) {
                            scriptResourcePath = scriptResourcePath.substring(1);
                        }
                        URL bundledScriptURL = provider.getBundle().getEntry("javax.script" + "/" + scriptResourcePath);
                        if (bundledScriptURL != null) {
                            try {
                                SightlyScriptEngine sightlyScriptEngine = (SightlyScriptEngine) scriptEngineManager.getEngineByName(
                                        "htl");
                                if (sightlyScriptEngine != null) {
                                    CachedScript cachedScript = scriptCache.getScript(bundledScriptURL.toExternalForm());
                                    if (cachedScript != null) {
                                        return ((SightlyCompiledScript) cachedScript.getCompiledScript()).getRenderUnit();
                                    } else {
                                        final String finalRenderUnitIdentifier = renderUnitIdentifier.toString();
                                        try (ScriptNameAwareReader reader =
                                                     new ScriptNameAwareReader(new InputStreamReader(bundledScriptURL.openStream(),
                                                             StandardCharsets.UTF_8),
                                                             finalRenderUnitIdentifier)) {
                                            SightlyCompiledScript compiledScript =
                                                    (SightlyCompiledScript) sightlyScriptEngine.compile(reader);
                                            scriptCache.putScript(new CachedScript() {
                                                @Override
                                                public String getScriptPath() {
                                                    return bundledScriptURL.toExternalForm();
                                                }

                                                @Override
                                                public CompiledScript getCompiledScript() {
                                                    return compiledScript;
                                                }
                                            });
                                            return compiledScript.getRenderUnit();
                                        }
                                    }
                                }
                            } catch (IOException | ScriptException ignored) {

                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * <p>
     * Given a {@link Bindings} map, this method will check if the {@code bindings} contain a value for the {@link
     * BundledRenderUnit#VARIABLE} property and if the object provided by {@link BundledRenderUnit#getUnit()} is an instance of a {@link
     * RenderUnit}. If so, this service will return the {@link ClassLoader} of the {@link org.osgi.framework.Bundle} providing the {@link
     * BundledRenderUnit}.</p>
     *
     * @param bindings the bindings passed initially to the HTL Script Engine
     * @return the {@link BundledRenderUnit}'s classloader if one is found, {@code null} otherwise
     */
    @Nullable
    public ClassLoader getBundledRenderUnitClassloader(Bindings bindings) {
        Object bru = bindings.get(BundledRenderUnit.VARIABLE);
        if (bru instanceof BundledRenderUnit) {
            BundledRenderUnit bundledRenderUnit = (BundledRenderUnit) bru;
            return bundledRenderUnit.getBundle().adapt(BundleWiring.class).getClassLoader();
        }
        return null;
    }

    /**
     * <p>
     * Given a {@link Bindings} map, this method will check if the {@code bindings} contain a value for the {@link
     * BundledRenderUnit#VARIABLE} property and if the object provided by {@link BundledRenderUnit#getUnit()} is an instance of a {@link
     * RenderUnit}. If so, this service will try to get a reference to a service of type {@code clazz} and return the service object. The
     * service will be retrieved using the bundle context of the {@link BundledRenderUnit} found in the {@code bindings} map.</p>
     *
     * @param bindings the bindings passed initially to the HTL Script Engine
     * @param clazz    the class identifying the type of the service
     * @param <T>      the service type
     * @return a service object, if one is found, {@code null} otherwise
     */
    @Nullable
    public <T> T getServiceForBundledRenderUnit(Bindings bindings, Class<T> clazz) {
        Object bru = bindings.get(BundledRenderUnit.VARIABLE);
        if (bru instanceof BundledRenderUnit) {
            BundledRenderUnit bundledRenderUnit = (BundledRenderUnit) bru;
            return bundledRenderUnit.getService(clazz.getName());
        }
        return null;
    }

    private ServiceRegistration<?> register(BundleContext bundleContext) {
        try {
            BundledUnitManager.class.getClassLoader().loadClass("org.apache.sling.scripting.bundle.tracker.BundledRenderUnit");
            return bundleContext.registerService(BundledUnitManager.class, this, null);
        } catch (ClassNotFoundException e) {
            LOGGER.info("No support for bundled RenderUnits.");
        }
        return null;
    }

    @Nullable
    private BundledRenderUnit getBundledRenderUnit(Bindings bindings) {
        Object bru = bindings.get(BundledRenderUnit.VARIABLE);
        if (bru instanceof BundledRenderUnit) {
            return (BundledRenderUnit) bru;
        }
        return null;
    }

}
