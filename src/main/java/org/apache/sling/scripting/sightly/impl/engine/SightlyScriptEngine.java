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
package org.apache.sling.scripting.sightly.impl.engine;

import java.io.Reader;
import java.io.StringReader;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptException;

import org.apache.sling.scripting.api.AbstractSlingScriptEngine;
import org.apache.sling.scripting.api.ScriptNameAware;
import org.apache.sling.scripting.api.resource.ScriptingResourceResolverProvider;
import org.apache.sling.scripting.bundle.tracker.BundledRenderUnit;
import org.apache.sling.scripting.sightly.SightlyException;
import org.apache.sling.scripting.sightly.compiler.CompilationResult;
import org.apache.sling.scripting.sightly.compiler.CompilationUnit;
import org.apache.sling.scripting.sightly.compiler.CompilerMessage;
import org.apache.sling.scripting.sightly.compiler.SightlyCompiler;
import org.apache.sling.scripting.sightly.impl.engine.compiled.SourceIdentifier;
import org.apache.sling.scripting.sightly.impl.utils.BindingsUtils;
import org.apache.sling.scripting.sightly.java.compiler.GlobalShadowCheckBackendCompiler;
import org.apache.sling.scripting.sightly.java.compiler.JavaClassBackendCompiler;
import org.apache.sling.scripting.sightly.java.compiler.JavaEscapeUtils;
import org.apache.sling.scripting.sightly.java.compiler.JavaImportsAnalyzer;
import org.apache.sling.scripting.sightly.impl.engine.compiled.SlingHTLMasterCompiler;
import org.apache.sling.scripting.sightly.render.RenderUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The HTL Script engine
 */
public class SightlyScriptEngine extends AbstractSlingScriptEngine implements Compilable {

    private static final Logger LOGGER = LoggerFactory.getLogger(SightlyScriptEngine.class);

    private SlingHTLMasterCompiler slingHTLMasterCompiler;
    private ExtensionRegistryService extensionRegistryService;

    SightlyScriptEngine(SightlyScriptEngineFactory factory, ExtensionRegistryService extensionRegistryService, SlingHTLMasterCompiler slingHTLMasterCompiler) {
        super(factory);
        this.extensionRegistryService = extensionRegistryService;
        this.slingHTLMasterCompiler = slingHTLMasterCompiler;
    }

    @Override
    public CompiledScript compile(String script) throws ScriptException {
        return compile(new StringReader(script));
    }

    @Override
    public CompiledScript compile(final Reader script) throws ScriptException {
        if (slingHTLMasterCompiler != null) {
            return slingHTLMasterCompiler.compileHTLScript(this, script, null);
        }
        throw new ScriptException("Missing compilation support.");
    }

    @Override
    public Object eval(Reader reader, ScriptContext scriptContext) throws ScriptException {
        checkArguments(reader, scriptContext);
        try {
            SightlyCompiledScript compiledScript = null;
            Bindings bindings = scriptContext.getBindings(ScriptContext.ENGINE_SCOPE);
            Object bundledRenderUnit = bindings.get(BundledRenderUnit.VARIABLE);
            Object renderUnit = null;
            if (bundledRenderUnit instanceof BundledRenderUnit) {
                renderUnit = ((BundledRenderUnit) bundledRenderUnit).getUnit();
                if (renderUnit instanceof RenderUnit) {
                    compiledScript = new SightlyCompiledScript(this, (RenderUnit) renderUnit);
                    return compiledScript.eval(scriptContext);
                }
            }
            if (slingHTLMasterCompiler != null) {
                compiledScript = slingHTLMasterCompiler.compileHTLScript(this, reader, scriptContext);
                if (compiledScript != null) {
                    return compiledScript.eval(scriptContext);
                }
            }
        } catch (Exception e) {
            throw new ScriptException(e);
        }
        LOGGER.warn("Did not find a compilable or executable unit.");
        return null;
    }

    public ExtensionRegistryService getExtensionRegistryService() {
        return extensionRegistryService;
    }

    private void checkArguments(Reader reader, ScriptContext scriptContext) {
        if (reader == null) {
            throw new NullPointerException("Reader cannot be null");
        }
        if (scriptContext == null) {
            throw new NullPointerException("ScriptContext cannot be null");
        }
    }


}
