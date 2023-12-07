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
package org.apache.sling.scripting.sightly.impl.engine.compiled;

import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.scripting.sightly.java.compiler.JavaEscapeUtils;
import org.apache.sling.scripting.sightly.java.compiler.JavaImportsAnalyzer;

/**
 * This custom imports analyser makes sure that no import statements are generated for repository-based use objects, since these are
 * not compiled ahead of the HTL scripts.
 */
class SlingJavaImportsAnalyser implements JavaImportsAnalyzer {

    private ResourceResolverFactory factory;

    public SlingJavaImportsAnalyser(final ResourceResolverFactory factory) {
        this.factory = factory;
    }

    @Override
    public boolean allowImport(final String importedClass) {
        for (final String searchPath : this.factory.getSearchPath()) {
            final String subPackage = JavaEscapeUtils.makeJavaPackage(searchPath);
            if (importedClass.startsWith(subPackage)) {
                return false;
            }
        }
        return true;
    }
}
