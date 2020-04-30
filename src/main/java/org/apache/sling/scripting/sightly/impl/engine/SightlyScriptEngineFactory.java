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

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;

import org.apache.sling.scripting.api.AbstractScriptEngineFactory;
import org.apache.sling.scripting.sightly.impl.engine.bundled.BundledUnitManagerImpl;
import org.apache.sling.scripting.sightly.impl.engine.compiled.SlingHTLMasterCompiler;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;

/**
 * HTL template engine factory
 */
@Component(
        service = ScriptEngineFactory.class,
        property = {
                "extensions=html",
                "names=htl",
                "names=HTL",
                Constants.SERVICE_DESCRIPTION + "=HTL Templating Engine",
                "compatible.javax.script.name=sly"
        }
)
public class SightlyScriptEngineFactory extends AbstractScriptEngineFactory {

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policyOption = ReferencePolicyOption.GREEDY)
    private SlingHTLMasterCompiler slingHTLMasterCompiler;

    @Reference
    private BundledUnitManagerImpl bundledUnitManager;

    @Reference
    private ExtensionRegistryService extensionRegistryService;

    public final static String SHORT_NAME = "sightly";
    public final static String EXTENSION = "html";

    private final static String LANGUAGE_NAME = "The HTL Templating Language";
    private final static String LANGUAGE_VERSION = "1.4";

    public SightlyScriptEngineFactory() {
        setNames("htl", "HTL", SHORT_NAME);
        setExtensions(EXTENSION);
    }

    @Override
    public String getLanguageName() {
        return LANGUAGE_NAME;
    }

    @Override
    public String getLanguageVersion() {
        return LANGUAGE_VERSION;
    }

    @Override
    public ScriptEngine getScriptEngine() {
        return new SightlyScriptEngine(this, extensionRegistryService, slingHTLMasterCompiler, bundledUnitManager);
    }
}
