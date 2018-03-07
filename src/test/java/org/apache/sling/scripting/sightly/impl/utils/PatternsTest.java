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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import org.junit.Test;

import static org.junit.Assert.fail;

public class PatternsTest {

    private static final int REGEX_MATCH_TIMEOUT = 3;

    @Test
    public void testJavaPackagePattern() {

        Object[] inputs = new Object[]{
                "package org.apache.sling.scripting.sightly.impl.utils; ", true,
                "package org.apache.sling.scripting.sightly.impl_utils; ", true,
                "package org.apache.sling.scripting.sightly.impl_utils;", true,
                "package org.apache.sling.scripting.sightly.impl.utils;", true,
                "package $org.apache.sling.scripting.sightly.impl.utils;", true,
                "package _org.apache.sling.scripting.sightly.impl.utils;", true,
                "package org.apa_che.sling.scripting.sightly.impl.utils;", true,
                "package org.ap$che.sling.scripting.sightly.impl.utils;", true,
                "package org.ap4che.$sling._scripting.sightly.impl.utils;", true,
                "package 1org.apache.sling.scripting.sightly.impl.utils;", false,
                "package org.1apache.sling.scripting.sightly.impl.utils;", false,
                "package\torg.apache.sling.scripting.sightly.impl.utils;\t", true,
                "package org.apache.sling.scripting.sightly.impl.utils ; ", true,
        };
        testPattern(Patterns.JAVA_PACKAGE_DECLARATION, inputs);
        // pattern from org.apache.sling.scripting.sightly.impl.engine.SightlyJavaCompilerService before SLING-7523
        // Pattern PACKAGE_DECL_PATTERN = Pattern.compile("(\\s*)package\\s+([a-zA-Z_$][a-zA-Z\\d_$]*\\.?)+;");
        // testPattern(PACKAGE_DECL_PATTERN, inputs);
    }

    @Test
    public void testJavaClassNamePattern() {

        Object[] inputs = new Object[]{
                "org.apache.sling.scripting.sightly.impl.utils.PatternsTest", true,
                "$org.apache.sling.scripting.sightly.impl.utils.PatternsTest", true,
                "_org.apache.sling.scripting.sightly.impl.utils.PatternsTest", true,
                "PatternsTest", true,
                "PatternsTest2", true,
                "PatternsTest ", false,
                "1PatternsTest", false,
                "$", true,
                "_", true,
                "$_", true,
                "$_1", true,
                "package_info", false,
                "org.apache.sling.scripting.sightly.impl.utils.patternsTest", false,
                "a.js", false,
                "org.aspectj.weaver.patterns" +
                        ".HasThisTypePatternTriedToSneakInSomeGenericOrParameterizedTypePatternMatchingStuffAnywhereVisitor", true,
                "org.aspectj.weaver.patterns" +
                        ".HasThisTypePatternTriedToSneakInSomeGenericOrParameterizedTypePatternMatchingStuffAnywhereVisitor ", false,
                "org.aspectj.weaver.patterns" +
                        ".HasThisTypePatternTriedToSneakInSomeGeneric Or ParameterizedTypePatternMatchingStuffAnywhereVisitor ", false,
        };
        testPattern(Patterns.JAVA_CLASS_NAME, inputs);
    }

    private void testPattern(Pattern pattern, Object[] inputs) {
        StringBuilder errors = new StringBuilder();
        ExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();
        for (int i = 0; i < inputs.length - 1; i += 2) {
            String input = (String) inputs[i];
            boolean expectedMatch = (Boolean) inputs[i + 1];
            Future<Boolean> future = EXECUTOR_SERVICE.submit(new PatternCallable(pattern, input));
            try {
                if (expectedMatch != future.get(REGEX_MATCH_TIMEOUT, TimeUnit.SECONDS)) {
                    errors.append(
                        String.format(
                                "Pattern '%s' %s '%s'.", pattern, expectedMatch ? "was expected to match" : "was not expected to match", input
                        )
                    ).append("\n");
                }
            } catch (TimeoutException e) {
                errors.append(
                        String.format(
                                "Pattern '%s' is susceptible to catastrophic backtracking for input '%s'.",
                                Patterns.JAVA_PACKAGE_DECLARATION.pattern(),
                                input
                        )
                ).append("\n");
                future.cancel(true);
                EXECUTOR_SERVICE.shutdownNow();
                EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();
            } catch (Exception e) {
                errors.append(
                        String.format(
                                "Unable to test pattern '%s' with input '%s': %s.",
                                Patterns.JAVA_PACKAGE_DECLARATION.pattern(),
                                input,
                                e.getMessage()
                        )
                ).append("\n");
            }
        }
        if (errors.length() > 0) {
            fail("\n" + errors.toString());
        }
    }

    class PatternCallable implements Callable<Boolean> {

        private Pattern pattern;
        private String toMatch;

        PatternCallable(Pattern pattern, String toMatch) {
            this.pattern = pattern;
            this.toMatch = toMatch;
        }

        @Override
        public Boolean call() {
            return pattern.matcher(toMatch).matches();
        }
    }
}
