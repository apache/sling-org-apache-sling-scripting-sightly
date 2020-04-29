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
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.script.Bindings;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.commons.compiler.source.JavaEscapeHelper;
import org.apache.sling.scripting.api.CachedScript;
import org.apache.sling.scripting.api.ScriptCache;
import org.apache.sling.scripting.api.resource.ScriptingResourceResolverProvider;
import org.apache.sling.scripting.core.BundledRenderUnit;
import org.apache.sling.servlets.resolver.bundle.tracker.ResourceType;
import org.apache.sling.servlets.resolver.bundle.tracker.TypeProvider;
import org.apache.sling.scripting.core.ScriptNameAwareReader;
import org.apache.sling.scripting.sightly.engine.BundledUnitManager;
import org.apache.sling.scripting.sightly.impl.engine.SightlyCompiledScript;
import org.apache.sling.scripting.sightly.impl.engine.SightlyScriptEngine;
import org.apache.sling.scripting.sightly.impl.utils.BindingsUtils;
import org.apache.sling.scripting.sightly.render.RenderUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This service allows various components to work with {@link
 * BundledRenderUnit} instance and perform dependency resolution based on their availability in
 * the {@link Bindings} maps passed to the HTL Script Engine.
 */
@Component
public class BundledUnitManagerImpl implements BundledUnitManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(BundledUnitManagerImpl.class);

    @Reference
    private ScriptEngineManager scriptEngineManager;

    @Reference
    private ScriptCache scriptCache;

    @Reference
    private ScriptingResourceResolverProvider scriptingResourceResolverProvider;


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
        List<String> searchPathRelativeLocations = new ArrayList<>();
        if (!identifier.startsWith("/")) {
            ResourceResolver scriptingResourceResolver = scriptingResourceResolverProvider.getRequestScopedResourceResolver();
            for (String searchPath : scriptingResourceResolver.getSearchPath()) {
                searchPathRelativeLocations.add(ResourceUtil.normalize(searchPath + "/" + identifier));
            }
        }
        if (currentResource != null && bundledRenderUnit != null) {
            for (TypeProvider provider : bundledRenderUnit.getTypeProviders()) {
                Set<String> locations = new LinkedHashSet<>();
                if (!identifier.startsWith("/")) {
                    for (ResourceType type : provider.getBundledRenderUnitCapability().getResourceTypes()) {
                        locations.add(getResourceTypeQualifiedPath(identifier, type));
                    }
                    locations.addAll(searchPathRelativeLocations);
                } else {
                    locations.add(identifier);
                }
                for (String renderUnitIdentifier : locations) {
                    String renderUnitBundledPath = renderUnitIdentifier;
                    if (renderUnitBundledPath.startsWith("/")) {
                        renderUnitBundledPath = renderUnitBundledPath.substring(1);
                    }
                    String className = JavaEscapeHelper.makeJavaPackage(renderUnitBundledPath);
                    try {
                        Class<?> clazz = provider.getBundle().loadClass(className);
                        if (clazz.getSuperclass() == RenderUnit.class) {
                            return (RenderUnit) clazz.getDeclaredConstructor().newInstance();
                        }
                    } catch (ClassNotFoundException e) {
                        URL bundledScriptURL = provider.getBundle().getEntry("javax.script" + "/" + renderUnitBundledPath);
                        if (bundledScriptURL != null) {
                            try {
                                SightlyScriptEngine sightlyScriptEngine = (SightlyScriptEngine) scriptEngineManager.getEngineByName(
                                        "htl");
                                if (sightlyScriptEngine != null) {
                                    CachedScript cachedScript = scriptCache.getScript(bundledScriptURL.toExternalForm());
                                    if (cachedScript != null) {
                                        return ((SightlyCompiledScript) cachedScript.getCompiledScript()).getRenderUnit();
                                    } else {
                                        try (ScriptNameAwareReader reader =
                                                     new ScriptNameAwareReader(new InputStreamReader(bundledScriptURL.openStream(),
                                                             StandardCharsets.UTF_8), renderUnitIdentifier)) {
                                            SightlyCompiledScript compiledScript =
                                                    (SightlyCompiledScript) sightlyScriptEngine.compile(reader);
                                            return compiledScript.getRenderUnit();
                                        }
                                    }
                                }
                            } catch (IOException | ScriptException compileException) {
                                throw new IllegalStateException(compileException);
                            }
                        }
                    } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                        throw new IllegalArgumentException(e);
                    }
                }
            }
        }
        return null;
    }

    @Override
    @Nullable
    public URL getScript(Bindings bindings, String identifier) {
        BundledRenderUnit bundledRenderUnit = getBundledRenderUnit(bindings);
        Resource currentResource = BindingsUtils.getResource(bindings);
        if (currentResource != null && bundledRenderUnit != null) {
            for (TypeProvider provider : bundledRenderUnit.getTypeProviders()) {
                for (ResourceType type : provider.getBundledRenderUnitCapability().getResourceTypes()) {
                    String scriptResourcePath = getResourceTypeQualifiedPath(identifier, type);
                    String scriptBundledPath = scriptResourcePath;
                    if (scriptBundledPath.startsWith("/")) {
                        scriptBundledPath = scriptBundledPath.substring(1);
                    }
                    URL bundledScriptURL = provider.getBundle().getEntry("javax.script/" + scriptBundledPath);
                    if (bundledScriptURL != null) {
                        return bundledScriptURL;
                    }
                }
            }
        }
        return null;
    }

    @NotNull
    private String getResourceTypeQualifiedPath(@NotNull String identifier, @NotNull ResourceType type) {
        if (!identifier.startsWith("/")) {
            return type.toString() + "/" + identifier;
        }
        return identifier;
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
    @Override
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

    @Nullable
    private BundledRenderUnit getBundledRenderUnit(Bindings bindings) {
        Object bru = bindings.get(BundledRenderUnit.VARIABLE);
        if (bru instanceof BundledRenderUnit) {
            return (BundledRenderUnit) bru;
        }
        return null;
    }

}
