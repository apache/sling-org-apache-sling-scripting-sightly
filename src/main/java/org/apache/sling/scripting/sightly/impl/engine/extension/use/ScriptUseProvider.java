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
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.scripting.SlingBindings;
import org.apache.sling.api.scripting.SlingScript;
import org.apache.sling.scripting.core.ScriptNameAwareReader;
import org.apache.sling.scripting.sightly.SightlyException;
import org.apache.sling.scripting.sightly.impl.engine.SightlyScriptEngineFactory;
import org.apache.sling.scripting.sightly.impl.engine.bundled.BundledUnitManagerImpl;
import org.apache.sling.scripting.sightly.impl.utils.BindingsUtils;
import org.apache.sling.scripting.sightly.impl.utils.ScriptDependencyResolver;
import org.apache.sling.scripting.sightly.render.RenderContext;
import org.apache.sling.scripting.sightly.use.ProviderOutcome;
import org.apache.sling.scripting.sightly.use.UseProvider;
import org.apache.sling.scripting.spi.bundle.BundledRenderUnit;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Use provider that interprets the identifier as a script path, and runs the respective script using a script engine that matches the
 * script extension.
 * <p>
 * This provider returns a non-failure outcome only if the evaluated script actually returns something. For more details check the
 * implementation of the {@link SlingScript#eval(SlingBindings)} method for the available script engines from your platform.
 */
@Component(
        service = UseProvider.class,
        configurationPid = "org.apache.sling.scripting.sightly.impl.engine.extension.use.ScriptUseProvider",
        property = {Constants.SERVICE_RANKING + ":Integer=0"})
public class ScriptUseProvider implements UseProvider {

    @interface Configuration {

        @AttributeDefinition(
                name = "Service Ranking",
                description =
                        "The Service Ranking value acts as the priority with which this Use Provider is queried to return an "
                                + "Use-object. A higher value represents a higher priority.")
        int service_ranking() default 0;
    }

    private static final Logger log = LoggerFactory.getLogger(ScriptUseProvider.class);

    private final AtomicReference<ScriptEngineManager> engineManagerRef = new AtomicReference<>();
    private final BundleContext bundleContext;
    private final BundledUnitManagerImpl bundledUnitManager;
    protected final ScriptDependencyResolver scriptDependencyResolver;

    private ServiceReference<ScriptEngineManager> scriptEngineManagerServiceReference;

    @Activate
    public ScriptUseProvider(
            BundleContext bundleContext,
            @Reference BundledUnitManagerImpl bundledUnitManager,
            @Reference ScriptDependencyResolver scriptDependencyResolver) {
        this.bundleContext = bundleContext;
        this.bundledUnitManager = bundledUnitManager;
        this.scriptDependencyResolver = scriptDependencyResolver;
    }

    @Deactivate
    protected void deactivate() {
        if (scriptEngineManagerServiceReference != null) {
            bundleContext.ungetService(scriptEngineManagerServiceReference);
        }
    }

    @Override
    public ProviderOutcome provide(String scriptName, RenderContext renderContext, Bindings arguments) {
        Bindings globalBindings = renderContext.getBindings();
        Bindings bindings = BindingsUtils.merge(globalBindings, arguments);
        String extension = scriptExtension(scriptName);
        if (extension == null || extension.equals(SightlyScriptEngineFactory.EXTENSION)) {
            return ProviderOutcome.failure();
        }
        URL script = bundledUnitManager.getScript(bindings, scriptName);
        if (script != null) {
            String scriptUrlAsString = script.toExternalForm();
            bindings.remove(BundledRenderUnit.VARIABLE);
            bindings.put(ScriptEngine.FILENAME, scriptUrlAsString);
            ScriptEngineManager scriptEngineManager = getScriptEngineManager();
            if (scriptEngineManager == null) {
                return ProviderOutcome.failure(new SightlyException("Failed to obtain a ScriptEngineManager."));
            }
            ScriptEngine scriptEngine = scriptEngineManager.getEngineByExtension(extension);
            if (scriptEngine != null) {
                try (ScriptNameAwareReader reader = new ScriptNameAwareReader(
                        new InputStreamReader(script.openStream(), StandardCharsets.UTF_8), scriptUrlAsString)) {
                    if (scriptEngine instanceof Compilable) {
                        Compilable compilableScriptEngine = (Compilable) scriptEngine;
                        CompiledScript compiledScript = compilableScriptEngine.compile(reader);
                        return ProviderOutcome.notNullOrFailure(compiledScript.eval(bindings));
                    } else {
                        return ProviderOutcome.notNullOrFailure(scriptEngine.eval(reader, bindings));
                    }
                } catch (Exception e) {
                    return ProviderOutcome.failure(e);
                }
            }
        }
        Resource scriptResource = scriptDependencyResolver.resolveScript(renderContext, scriptName);
        if (scriptResource == null) {
            log.debug("Path does not match an existing resource: {}", scriptName);
            return ProviderOutcome.failure();
        }
        return evalScript(scriptResource, bindings);
    }

    private ProviderOutcome evalScript(Resource scriptResource, Bindings bindings) {
        SlingScript slingScript = scriptResource.adaptTo(SlingScript.class);
        if (slingScript == null) {
            return ProviderOutcome.failure();
        }
        SlingBindings slingBindings = new SlingBindings();
        slingBindings.putAll(bindings);
        Object scriptEval = slingScript.eval(slingBindings);
        return ProviderOutcome.notNullOrFailure(scriptEval);
    }

    private String scriptExtension(String path) {
        String extension = StringUtils.substringAfterLast(path, ".");
        if (StringUtils.isEmpty(extension)) {
            extension = null;
        }
        return extension;
    }

    @Nullable
    private ScriptEngineManager getScriptEngineManager() {
        ScriptEngineManager result = engineManagerRef.get();
        if (result == null) {
            ServiceReference<ScriptEngineManager> ref = bundleContext.getServiceReference(ScriptEngineManager.class);
            if (ref != null) {
                ScriptEngineManager mgr = bundleContext.getService(ref);
                if (mgr != null && engineManagerRef.compareAndSet(null, mgr)) {
                    this.scriptEngineManagerServiceReference = ref;
                    return mgr;
                } else if (mgr != null) {
                    // Already initialized by another thread; unget this one
                    bundleContext.ungetService(ref);
                }
            }
            return null;
        }
        return result;
    }
}
