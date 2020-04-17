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
package org.apache.sling.scripting.sightly.impl.engine.extension.use;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import javax.script.Bindings;
import javax.servlet.ServletRequest;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.adapter.Adaptable;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.scripting.SlingScriptHelper;
import org.apache.sling.models.factory.ModelFactory;
import org.apache.sling.scripting.sightly.impl.engine.bundled.BundledUnitManagerImpl;
import org.apache.sling.scripting.sightly.impl.engine.compiled.SlingHTLMasterCompiler;
import org.apache.sling.scripting.sightly.impl.utils.BindingsUtils;
import org.apache.sling.scripting.sightly.impl.utils.Patterns;
import org.apache.sling.scripting.sightly.pojo.Use;
import org.apache.sling.scripting.sightly.render.RenderContext;
import org.apache.sling.scripting.sightly.render.RenderUnit;
import org.apache.sling.scripting.sightly.use.ProviderOutcome;
import org.apache.sling.scripting.sightly.use.UseProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
        service = UseProvider.class,
        configurationPid = "org.apache.sling.scripting.sightly.impl.engine.extension.use.JavaUseProvider",
        property = {
                Constants.SERVICE_RANKING + ":Integer=90"
        }
)
public class JavaUseProvider implements UseProvider {

    @interface Configuration {

        @AttributeDefinition(
                name = "Service Ranking",
                description = "The Service Ranking value acts as the priority with which this Use Provider is queried to return an " +
                        "Use-object. A higher value represents a higher priority."
        )
        int service_ranking() default 90;

    }

    private static final String ADAPTABLE = "adaptable";
    private static final Logger LOG = LoggerFactory.getLogger(JavaUseProvider.class);

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policyOption = ReferencePolicyOption.GREEDY)
    private SlingHTLMasterCompiler slingHTLMasterCompiler;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policyOption = ReferencePolicyOption.GREEDY)
    private BundledUnitManagerImpl bundledUnitManager;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policyOption = ReferencePolicyOption.GREEDY)
    private ModelFactory modelFactory;

    @Override
    public ProviderOutcome provide(String identifier, RenderContext renderContext, Bindings arguments) {
        if (!Patterns.JAVA_CLASS_NAME.matcher(identifier).matches()) {
            LOG.debug("Identifier {} does not match a Java class name pattern.", identifier);
            return ProviderOutcome.failure();
        }
        Bindings globalBindings = renderContext.getBindings();
        SlingHttpServletRequest request = BindingsUtils.getRequest(globalBindings);
        Map<String, Object> overrides = setRequestAttributes(request, arguments);

        try {
            Exception failure = null;
            if (bundledUnitManager != null) {
                ClassLoader unitClassLoader = bundledUnitManager.getBundledRenderUnitClassloader(globalBindings);
                if (unitClassLoader != null) {
                    try {
                        String className = identifier;
                        if (className.indexOf('.') < 0) {
                            // the class name is not fully qualified; need to prepend the package name of the current rendering unit
                            RenderUnit renderUnit = bundledUnitManager.getRenderUnit(globalBindings);
                            if (renderUnit != null) {
                                className = renderUnit.getClass().getPackage().getName() + "." + className;
                            }
                        }
                        Class<?> clazz = unitClassLoader.loadClass(className);
                        return loadObject(clazz, cls -> bundledUnitManager.getServiceForBundledRenderUnit(globalBindings, clazz),
                                globalBindings,
                                arguments);
                    } catch (Exception e) {
                        // maybe the class will actually come from the repository
                        failure = e;
                    }
                }
            }
            if (slingHTLMasterCompiler != null) {
                Object result = slingHTLMasterCompiler.getResourceBackedUseObject(renderContext, identifier);
                if (result != null) {
                    if (result instanceof Use) {
                        ((Use) result).init(BindingsUtils.merge(globalBindings, arguments));
                    }
                    return ProviderOutcome.success(result);
                } else {
                    SlingScriptHelper slingScriptHelper = BindingsUtils.getHelper(globalBindings);
                    if (slingScriptHelper != null) {
                        try {
                            Class<?> clazz = slingHTLMasterCompiler.getClassLoader().loadClass(identifier);
                            return loadObject(clazz, slingScriptHelper::getService, globalBindings, arguments);
                        } catch (Exception e) {
                            failure = e;
                        }
                    }
                }

            }
            if (failure != null) {
                return ProviderOutcome.failure(failure);
            }
            return ProviderOutcome.failure();
        } catch (Exception e) {
            // any other exception is an error
            return ProviderOutcome.failure(e);
        } finally {
            resetRequestAttribute(request, overrides);
        }
    }

    private ProviderOutcome loadObject(@NotNull Class<?> cls, @NotNull ServiceLoader serviceLoader, @NotNull Bindings globalBindings,
                                       @NotNull Bindings arguments)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException,
            InstantiationException {
            // OSGi service
            Object result = serviceLoader.getService(cls);
            if (result != null) {
                return ProviderOutcome.success(result);
            }
            // adaptable
            Object adaptableCandidate = arguments.get(ADAPTABLE);
            if (adaptableCandidate instanceof Adaptable) {
                Adaptable adaptable = (Adaptable) adaptableCandidate;
                result = adaptable.adaptTo(cls);
                if (result != null) {
                    return ProviderOutcome.success(result);
                }
            }
            SlingHttpServletRequest request = BindingsUtils.getRequest(globalBindings);
            Resource resource = BindingsUtils.getResource(globalBindings);
            // Sling Model
            if (modelFactory != null && modelFactory.isModelClass(cls)) {
                try {
                    // try to instantiate class via Sling Models (first via request, then via resource)
                    if (request != null && modelFactory.canCreateFromAdaptable(request, cls)) {
                        LOG.debug("Trying to instantiate class {} as Sling Model from request.", cls);
                        return ProviderOutcome.notNullOrFailure(modelFactory.createModel(request, cls));
                    }
                    if (resource != null && modelFactory.canCreateFromAdaptable(resource, cls)) {
                        LOG.debug("Trying to instantiate class {} as Sling Model from resource.", cls);
                        return ProviderOutcome.notNullOrFailure(modelFactory.createModel(resource, cls));
                    }
                    return ProviderOutcome.failure(
                            new IllegalStateException("Could not adapt the given Sling Model from neither request nor resource: " + cls));
                } catch (Exception e) {
                    return ProviderOutcome.failure(e);
                }
            }
            if (request != null) {
                result = request.adaptTo(cls);
            }
            if (result == null && resource != null) {
                result = resource.adaptTo(cls);
            }
            if (result != null) {
                return ProviderOutcome.success(result);
            } else if (cls.isInterface() || Modifier.isAbstract(cls.getModifiers())) {
                LOG.debug("Won't attempt to instantiate an interface or abstract class {}", cls.getName());
                return ProviderOutcome.failure(new IllegalArgumentException(String.format(" %s represents an interface or an abstract " +
                        "class which cannot be instantiated.", cls.getName())));
            } else {
                /*
                 * the object was cached by the class loader but it's not adaptable from {@link Resource} or {@link
                 * SlingHttpServletRequest}; attempt to load it like a regular POJO that optionally could implement {@link Use}
                 */
                result = cls.getDeclaredConstructor().newInstance();
                if (result instanceof Use) {
                    ((Use) result).init(BindingsUtils.merge(globalBindings, arguments));
                }
                return ProviderOutcome.notNullOrFailure(result);
            }
    }

    private Map<String, Object> setRequestAttributes(ServletRequest request, Bindings arguments) {
        Map<String, Object> overrides = new HashMap<>();
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            Object oldValue = request.getAttribute(key);
            if (oldValue != null) {
                overrides.put(key, oldValue);
            } else {
                overrides.put(key, NULL);
            }
            request.setAttribute(key, value);
        }
        return overrides;
    }

    private void resetRequestAttribute(ServletRequest request, Map<String, Object> overrides) {
        for (Map.Entry<String, Object> entry : overrides.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == NULL) {
                request.removeAttribute(key);
            } else {
                request.setAttribute(key, value);
            }
        }
    }

    private static final Object NULL = new Object();

    private interface ServiceLoader {
        @Nullable Object getService(Class<?> cls);
    }
}
