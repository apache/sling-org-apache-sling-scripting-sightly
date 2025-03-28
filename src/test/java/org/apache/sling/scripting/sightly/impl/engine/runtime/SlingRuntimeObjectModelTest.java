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
package org.apache.sling.scripting.sightly.impl.engine.runtime;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.sling.api.adapter.Adaptable;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import static org.junit.Assert.*;

public class SlingRuntimeObjectModelTest {

    private static final String VALUE_MAP_VALUE = "ValueMap value";
    private static final String METHOD_VALUE = "Method value";
    private SlingRuntimeObjectModel slingRuntimeObjectModel = new SlingRuntimeObjectModel();

    @Test
    public void getPropertyFromAdaptableWithField() {
        assertEquals(
                "Expected public fields to have priority over ValueMap adaptable's properties.",
                FieldTestMockAdaptable.test,
                slingRuntimeObjectModel.getProperty(new FieldTestMockAdaptable(), "test"));
    }

    @Test
    public void getPropertyFromAdaptableWithMethod() {
        assertEquals(
                "Expected public methods to have priority over ValueMap adaptable's properties.",
                METHOD_VALUE,
                slingRuntimeObjectModel.getProperty(new MethodTestMockAdaptable(), "test"));
    }

    @Test
    public void getPropertyFromAdaptable() {
        assertEquals(
                "Expected to solve property from ValueMap returned by an adaptable.",
                VALUE_MAP_VALUE,
                slingRuntimeObjectModel.getProperty(new AdaptableTestMock(), "test"));
    }

    @Test
    public void nullChecks() {
        assertNull(slingRuntimeObjectModel.getProperty(null, null));
        assertNull(slingRuntimeObjectModel.getProperty(this, null));
        assertNull(slingRuntimeObjectModel.getProperty(this, ""));
    }

    @Test
    public void testToBooleanLegacy() {
        SlingRuntimeObjectModel runtimeObjectModel = new SlingRuntimeObjectModel(true);
        assertFalse(runtimeObjectModel.toBoolean(null));
        assertFalse(runtimeObjectModel.toBoolean(0));
        assertTrue(runtimeObjectModel.toBoolean(123456));
        assertFalse(runtimeObjectModel.toBoolean(""));
        assertFalse(runtimeObjectModel.toBoolean(false));
        assertFalse(runtimeObjectModel.toBoolean(Boolean.FALSE));
        assertFalse(runtimeObjectModel.toBoolean(new int[0]));
        assertFalse(runtimeObjectModel.toBoolean("FalSe"));
        assertFalse(runtimeObjectModel.toBoolean("false"));
        assertFalse(runtimeObjectModel.toBoolean("FALSE"));
        assertTrue(runtimeObjectModel.toBoolean("true"));
        assertTrue(runtimeObjectModel.toBoolean("TRUE"));
        assertTrue(runtimeObjectModel.toBoolean("TrUE"));
        Integer[] testArray = new Integer[] {1, 2, 3};
        int[] testPrimitiveArray = new int[] {1, 2, 3};
        List testList = Arrays.asList(testArray);
        assertTrue(runtimeObjectModel.toBoolean(testArray));
        assertTrue(runtimeObjectModel.toBoolean(testPrimitiveArray));
        assertFalse(runtimeObjectModel.toBoolean(new Integer[] {}));
        assertTrue(runtimeObjectModel.toBoolean(testList));
        assertFalse(runtimeObjectModel.toBoolean(Collections.emptyList()));
        Map<String, Integer> map = new HashMap<String, Integer>() {
            {
                put("one", 1);
                put("two", 2);
            }
        };
        assertTrue(runtimeObjectModel.toBoolean(map));
        assertFalse(runtimeObjectModel.toBoolean(Collections.EMPTY_MAP));
        assertTrue(runtimeObjectModel.toBoolean(testList.iterator()));
        assertFalse(runtimeObjectModel.toBoolean(Collections.EMPTY_LIST.iterator()));
        assertTrue(runtimeObjectModel.toBoolean(new Bag<>(testArray)));
        assertFalse(runtimeObjectModel.toBoolean(new Bag<>(new Integer[] {})));
        assertTrue(runtimeObjectModel.toBoolean(new Date()));

        assertFalse(runtimeObjectModel.toBoolean(Optional.empty()));
        assertFalse(runtimeObjectModel.toBoolean(Optional.of("")));
        assertFalse(runtimeObjectModel.toBoolean(Optional.of(false)));
        assertFalse(runtimeObjectModel.toBoolean(Optional.ofNullable(null)));
        assertTrue(runtimeObjectModel.toBoolean(Optional.of(true)));
        assertTrue(runtimeObjectModel.toBoolean(Optional.of("pass")));
        assertTrue(runtimeObjectModel.toBoolean(Optional.of(1)));
        assertTrue(runtimeObjectModel.toBoolean(new Object()));
        Map<String, String> map2 = new HashMap<String, String>() {
            @Override
            public String toString() {
                return null;
            }
        };
        assertFalse(runtimeObjectModel.toBoolean(map2));
        map2.put("one", "entry");
        assertTrue(runtimeObjectModel.toBoolean(map2));
    }

    @Test
    public void testToBooleanLegacyFalse() {
        SlingRuntimeObjectModel runtimeObjectModel = new SlingRuntimeObjectModel(false);
        assertFalse(runtimeObjectModel.toBoolean(null));
        assertFalse(runtimeObjectModel.toBoolean(0));
        assertTrue(runtimeObjectModel.toBoolean(123456));
        assertFalse(runtimeObjectModel.toBoolean(""));
        assertFalse(runtimeObjectModel.toBoolean(false));
        assertFalse(runtimeObjectModel.toBoolean(Boolean.FALSE));
        assertFalse(runtimeObjectModel.toBoolean(new int[0]));
        assertTrue(runtimeObjectModel.toBoolean("FalSe"));
        assertTrue(runtimeObjectModel.toBoolean("false"));
        assertTrue(runtimeObjectModel.toBoolean("FALSE"));
        assertTrue(runtimeObjectModel.toBoolean("true"));
        assertTrue(runtimeObjectModel.toBoolean("TRUE"));
        assertTrue(runtimeObjectModel.toBoolean("TrUE"));
        Integer[] testArray = new Integer[] {1, 2, 3};
        int[] testPrimitiveArray = new int[] {1, 2, 3};
        List testList = Arrays.asList(testArray);
        assertTrue(runtimeObjectModel.toBoolean(testArray));
        assertTrue(runtimeObjectModel.toBoolean(testPrimitiveArray));
        assertFalse(runtimeObjectModel.toBoolean(new Integer[] {}));
        assertTrue(runtimeObjectModel.toBoolean(testList));
        assertFalse(runtimeObjectModel.toBoolean(Collections.emptyList()));
        Map<String, Integer> map = new HashMap<String, Integer>() {
            {
                put("one", 1);
                put("two", 2);
            }
        };
        assertTrue(runtimeObjectModel.toBoolean(map));
        assertFalse(runtimeObjectModel.toBoolean(Collections.EMPTY_MAP));
        assertTrue(runtimeObjectModel.toBoolean(testList.iterator()));
        assertFalse(runtimeObjectModel.toBoolean(Collections.EMPTY_LIST.iterator()));
        assertTrue(runtimeObjectModel.toBoolean(new Bag<>(testArray)));
        assertFalse(runtimeObjectModel.toBoolean(new Bag<>(new Integer[] {})));
        assertTrue(runtimeObjectModel.toBoolean(new Date()));

        assertFalse(runtimeObjectModel.toBoolean(Optional.empty()));
        assertFalse(runtimeObjectModel.toBoolean(Optional.of("")));
        assertFalse(runtimeObjectModel.toBoolean(Optional.of(false)));
        assertFalse(runtimeObjectModel.toBoolean(Optional.ofNullable(null)));
        assertTrue(runtimeObjectModel.toBoolean(Optional.of(true)));
        assertTrue(runtimeObjectModel.toBoolean(Optional.of("pass")));
        assertTrue(runtimeObjectModel.toBoolean(Optional.of(1)));
        assertTrue(runtimeObjectModel.toBoolean(new Object()));
        Map<String, String> map2 = new HashMap<String, String>() {
            @Override
            public String toString() {
                return null;
            }
        };
        assertFalse(runtimeObjectModel.toBoolean(map2));
        map2.put("one", "entry");
        assertTrue(runtimeObjectModel.toBoolean(map2));
    }

    private abstract class MockAdaptable implements Adaptable {

        ValueMap getValueMap() {
            return new ValueMapDecorator(new HashMap<String, Object>() {
                {
                    put("test", VALUE_MAP_VALUE);
                }
            });
        }

        @Nullable
        @Override
        public <AdapterType> AdapterType adaptTo(@NotNull Class<AdapterType> aClass) {
            if (aClass == ValueMap.class) {
                return (AdapterType) getValueMap();
            }
            return null;
        }
    }

    public class FieldTestMockAdaptable extends MockAdaptable {

        public static final String test = "Field value";
    }

    public class MethodTestMockAdaptable extends MockAdaptable {
        public String getTest() {
            return METHOD_VALUE;
        }
    }

    public class AdaptableTestMock extends MockAdaptable {}

    private class Bag<T> implements Iterable<T> {

        private T[] backingArray;

        public Bag(T[] array) {
            this.backingArray = array;
        }

        @Override
        public Iterator<T> iterator() {
            return new Iterator<T>() {

                int index = 0;

                @Override
                public boolean hasNext() {
                    return index < backingArray.length;
                }

                @Override
                public T next() {
                    return backingArray[index++];
                }

                @Override
                public void remove() {}
            };
        }
    }
}
