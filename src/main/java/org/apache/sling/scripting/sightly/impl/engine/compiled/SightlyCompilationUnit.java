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

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.apache.sling.commons.compiler.CompilationUnit;

class SightlyCompilationUnit implements CompilationUnit {

    private String fqcn;
    private String sourceCode;

    SightlyCompilationUnit(String sourceCode, String fqcn) {
        this.sourceCode = sourceCode;
        this.fqcn = fqcn;
    }

    @Override
    public Reader getSource() throws IOException {
        return new InputStreamReader(IOUtils.toInputStream(sourceCode, "UTF-8"), StandardCharsets.UTF_8);
    }

    @Override
    public String getMainClassName() {
        return fqcn;
    }

    @Override
    public long getLastModified() {
        return System.currentTimeMillis();
    }
}
