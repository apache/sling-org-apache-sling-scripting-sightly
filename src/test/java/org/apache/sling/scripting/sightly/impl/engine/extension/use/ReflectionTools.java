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
package org.apache.sling.scripting.sightly.impl.engine.extension.use;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.Assert.fail;

/**
 * Utilities to help test private fields
 */
public class ReflectionTools {

    private ReflectionTools() {
        // to hide public ctor
    }

    public static void setFieldWithReflection(Object obj, String fieldName, Object value) {
        try {
            Class<?> clazz = obj.getClass();
            Field field = null;
            do {
                try { // NOSONAR
                    field = clazz.getDeclaredField(fieldName);
                } catch (NoSuchFieldException nsfe) {
                    clazz = clazz.getSuperclass();
                }
            } while (field == null && clazz != null);
            if (field != null) {
                if (Modifier.isStatic(field.getModifiers())) {
                    if (!field.canAccess(null)) {
                        field.setAccessible(true);
                    }
                    field.set(null, value);
                } else {
                    if (!field.canAccess(obj)) {
                        field.setAccessible(true);
                    }
                    field.set(obj, value);
                }
            } else {
                fail("Failed to find field via reflection: " + fieldName);
            }
        } catch (IllegalArgumentException | IllegalAccessException | SecurityException e) {
            fail("Failed to set field via reflection: " + e.getMessage());
        }
    }
}
