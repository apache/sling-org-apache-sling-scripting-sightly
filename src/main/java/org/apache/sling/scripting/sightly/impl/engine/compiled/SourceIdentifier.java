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
package org.apache.sling.scripting.sightly.impl.engine.compiled;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.scripting.sightly.impl.engine.SightlyEngineConfiguration;
import org.apache.sling.scripting.sightly.java.compiler.ClassInfo;
import org.apache.sling.scripting.sightly.java.compiler.JavaEscapeUtils;

/**
 * Identifies a Java source file based on a {@link Resource}. Depending on the used constructor this class might provide the abstraction
 * for either a Java source file generated for a HTL script or for a HTL {@link Resource}-based Java Use-API Object.
 */
public class SourceIdentifier implements ClassInfo {

    private SightlyEngineConfiguration engineConfiguration;
    private String scriptName;
    private String simpleClassName;
    private String packageName;
    private String fullyQualifiedClassName;

    SourceIdentifier(SightlyEngineConfiguration engineConfiguration, String resourcePath) {
        this.engineConfiguration = engineConfiguration;
        this.scriptName = resourcePath;
    }

    @Override
    public String getSimpleClassName() {
        if (simpleClassName == null) {
            int lastSlashIndex = scriptName.lastIndexOf("/");
            String processingScriptName = scriptName;
            if (scriptName.endsWith(".java")) {
                processingScriptName = scriptName.substring(0, scriptName.length() - 5);
            }
            if (lastSlashIndex != -1) {
                simpleClassName = JavaEscapeUtils.makeJavaPackage(processingScriptName.substring(lastSlashIndex));
            } else {
                simpleClassName = JavaEscapeUtils.makeJavaPackage(processingScriptName);
            }
        }
        return simpleClassName;
    }

    @Override
    public String getPackageName() {
        if (packageName == null) {
            int lastSlashIndex = scriptName.lastIndexOf("/");
            String processingScriptName = scriptName;
            boolean javaFile = scriptName.endsWith(".java");
            if (javaFile) {
                processingScriptName =
                        scriptName.substring(0, scriptName.length() - 5).replaceAll("-", "_");
            }
            if (lastSlashIndex != -1) {
                packageName = JavaEscapeUtils.makeJavaPackage(processingScriptName.substring(0, lastSlashIndex));
            } else {
                packageName = JavaEscapeUtils.makeJavaPackage(processingScriptName);
            }
            if (!javaFile) {
                packageName = engineConfiguration.getBundleSymbolicName() + "." + packageName;
            }
        }
        return packageName;
    }

    @Override
    public String getFullyQualifiedClassName() {
        if (fullyQualifiedClassName == null) {
            fullyQualifiedClassName = getPackageName() + "." + getSimpleClassName();
        }
        return fullyQualifiedClassName;
    }
}
