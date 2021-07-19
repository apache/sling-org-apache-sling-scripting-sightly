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
package org.apache.sling.scripting.sightly.impl.engine.extension;

import java.util.Arrays;
import java.util.HashMap;

import javax.script.Bindings;
import javax.script.SimpleBindings;

import org.apache.sling.scripting.sightly.render.AbstractRuntimeObjectModel;
import org.apache.sling.scripting.sightly.render.RenderContext;
import org.apache.sling.scripting.sightly.render.RuntimeObjectModel;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import static org.junit.Assert.assertEquals;

public class FormatFilterExtensionTest {

    private final RenderContext renderContext = new RenderContext() {
        @Override public RuntimeObjectModel getObjectModel() {
            return new AbstractRuntimeObjectModel() {
            };
        }

        @Override public Bindings getBindings() {
            return new SimpleBindings();
        }

        @Override public Object call(String s, Object... objects) {
            return null;
        }
    };
    private final FormatFilterExtension subject = new FormatFilterExtension();

    @Test
    public void testSimpleFormat() {
        Object result = subject.call(renderContext,
            "This {0} a {1} format", ImmutableMap.of("format", Arrays.asList("is", "simple")));
        assertEquals("This is a simple format", result);
    }

    @Test
    public void testComplexFormatNoSimplePlaceholderWithLocale() {
        Object result = subject.call(renderContext,
            "This query has {0,plural,zero {# results} one {# result} other {# results}}",
            new HashMap<String, Object>() {{
                put("format", Arrays.asList(7));
                put("locale", "en_US");
            }});
        assertEquals("This query has 7 results", result);
    }

    @Test
    public void testComplexFormatWithSimplePlaceholderNoLocale() {
        Object result = subject.call(renderContext,
            "This {0} has {1,plural,zero {# results} one {# result} other {# results}}",
            ImmutableMap.of("format", Arrays.asList("query", 7)));
        assertEquals("This query has 7 results", result);
    }
}
