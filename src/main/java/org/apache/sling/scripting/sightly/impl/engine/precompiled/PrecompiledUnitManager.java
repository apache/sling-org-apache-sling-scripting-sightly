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
package org.apache.sling.scripting.sightly.impl.engine.precompiled;

import javax.script.Bindings;
import javax.script.ScriptContext;

import org.apache.sling.scripting.bundle.tracker.BundledRenderUnit;
import org.apache.sling.scripting.sightly.impl.engine.SightlyCompiledScript;
import org.apache.sling.scripting.sightly.impl.engine.SightlyScriptEngine;
import org.apache.sling.scripting.sightly.render.RenderUnit;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
        immediate = true,
        service = {}
        /*
         * this component will register itself as a service only if the org.apache.sling.scripting.bundle.tracker API is present
         */
)
public class PrecompiledUnitManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(PrecompiledUnitManager.class);

    private final ServiceRegistration<?> serviceRegistration;

    @Activate
    public PrecompiledUnitManager(BundleContext bundleContext) {
        serviceRegistration = register(bundleContext);
    }

    @Deactivate
    public void deactivate() {
        if (serviceRegistration != null) {
            serviceRegistration.unregister();
        }
    }


    /**
     * Provides support for evaluating precompiled HTL scripts passed through the {@code scriptContext}. This feature works only when the
     * {@link org.apache.sling.scripting.bundle.tracker.BundledRenderUnit} API is deployed to the platform as well.
     *
     * @param sightlyScriptEngine the HTL script engine providing access to the HTL runtime
     * @param scriptContext       the script context
     * @return an instance of the compiled script, if a precompiled {@link RenderUnit} was present in the {@link ScriptContext}, {@code
     * null} otherwise
     */
    public SightlyCompiledScript evaluate(SightlyScriptEngine sightlyScriptEngine, ScriptContext scriptContext) {
        Bindings bindings = scriptContext.getBindings(ScriptContext.ENGINE_SCOPE);
        Object bundledRenderUnit = bindings.get(BundledRenderUnit.VARIABLE);
        if (bundledRenderUnit instanceof BundledRenderUnit) {
            Object renderUnit = ((BundledRenderUnit) bundledRenderUnit).getUnit();
            if (renderUnit instanceof RenderUnit) {
                return new SightlyCompiledScript(sightlyScriptEngine, (RenderUnit) renderUnit);
            }
        }
        return null;
    }

    public Object getBundledRenderUnitDependency(Bindings bindings, String identifier) {
        Object bru = bindings.get(BundledRenderUnit.VARIABLE);
        if (bru instanceof BundledRenderUnit) {
            BundledRenderUnit bundledRenderUnit = (BundledRenderUnit) bru;
            return bundledRenderUnit.getService(identifier);
        }
        return null;
    }

    private ServiceRegistration<?> register(BundleContext bundleContext) {
        try {
            PrecompiledUnitManager.class.getClassLoader().loadClass("org.apache.sling.scripting.bundle.tracker.BundledRenderUnit");
            return bundleContext.registerService(PrecompiledUnitManager.class, this, null);
        } catch (Throwable e) {
            LOGGER.info("No support for precompiled scripts.");
        }
        return null;
    }
}
