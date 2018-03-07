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
package org.apache.sling.scripting.sightly.impl.utils;

import java.util.regex.Pattern;

public final class Patterns {

    private static final String JAVA_IDENTIFIER_REGEX = "[\\p{L}\\p{Sc}_][\\p{L}\\p{N}\\p{Sc}_]*";

    private static final String JAVA_SIMPLE_CLASS_NAME_REGEX = "[\\p{Lu}\\p{Sc}_][\\p{L}\\p{N}\\p{Sc}_]*";

    private Patterns() {}

    /**
     * Pattern matching valid Java package declarations, according to
     * <a href="https://docs.oracle.com/javase/specs/jls/se7/html/index.html">The Java® Language Specification, 7th edition</a>.
     */
    public static final Pattern JAVA_PACKAGE_DECLARATION =
            Pattern.compile("\\s*package\\s+" + JAVA_IDENTIFIER_REGEX + "(\\." + JAVA_IDENTIFIER_REGEX + ")*\\s*;\\s*");

    /**
     * Pattern matching Java class names (simple or fully qualified), according to
     * <a href="https://docs.oracle.com/javase/specs/jls/se7/html/index.html">The Java® Language Specification, 7th edition</a>.
     */
    public static final Pattern JAVA_CLASS_NAME =
            Pattern.compile("(" + JAVA_IDENTIFIER_REGEX + "\\.{1})*(" + JAVA_SIMPLE_CLASS_NAME_REGEX + ")");

}
