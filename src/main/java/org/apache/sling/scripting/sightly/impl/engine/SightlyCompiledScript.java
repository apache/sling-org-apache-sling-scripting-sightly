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

import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;

import java.io.PrintWriter;

import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.scripting.LazyBindings;
import org.apache.sling.api.scripting.SlingBindings;
import org.apache.sling.scripting.sightly.SightlyException;
import org.apache.sling.scripting.sightly.impl.engine.runtime.RenderContextImpl;
import org.apache.sling.scripting.sightly.render.RenderContext;
import org.apache.sling.scripting.sightly.render.RenderUnit;

public class SightlyCompiledScript extends CompiledScript {

    private final SightlyScriptEngine scriptEngine;
    private final RenderUnit renderUnit;

    public SightlyCompiledScript(SightlyScriptEngine scriptEngine, RenderUnit renderUnit) {
        this.scriptEngine = scriptEngine;
        this.renderUnit = renderUnit;
    }

    @Override
    public Object eval(ScriptContext context) {
        Bindings bindings = context.getBindings(ScriptContext.ENGINE_SCOPE);
        SlingBindings slingBindings = new SlingBindings();
        slingBindings.putAll(bindings);
        SlingJakartaHttpServletRequest request = slingBindings.getJakartaRequest();
        if (request == null) {
            throw new SightlyException("Missing SlingJakartaHttpServletRequest from ScriptContext.");
        }
        Object oldBindings = request.getAttribute(SlingBindings.class.getName());
        try {
            request.setAttribute(SlingBindings.class.getName(), slingBindings);
            RenderContext renderContext = new RenderContextImpl(
                    scriptEngine.getConfiguration(), scriptEngine.getExtensionRegistryService(), context);
            PrintWriter out = new PrintWriter(context.getWriter());
            renderUnit.render(out, renderContext, new LazyBindings());
        } finally {
            request.setAttribute(SlingBindings.class.getName(), oldBindings);
        }
        return null;
    }

    @Override
    public ScriptEngine getEngine() {
        return scriptEngine;
    }

    public RenderUnit getRenderUnit() {
        return renderUnit;
    }
}
