/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 ******************************************************************************/
package org.apache.sling.scripting.sightly.impl.engine.runtime;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import org.apache.sling.api.adapter.Adaptable;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.scripting.sightly.impl.engine.SightlyEngineConfiguration;
import org.apache.sling.scripting.sightly.render.AbstractRuntimeObjectModel;

public class SlingRuntimeObjectModel extends AbstractRuntimeObjectModel {

    private final boolean legacyToBoolean;
    private static final String EMPTY_STRING = "";

    SlingRuntimeObjectModel() {
        this(SightlyEngineConfiguration.LEGACY_BOOLEAN_CASTING_DEFAULT);
    }

    SlingRuntimeObjectModel(boolean legacyToBoolean) {
        this.legacyToBoolean = legacyToBoolean;
    }


    @Override
    public boolean toBoolean(Object object) {
        if (legacyToBoolean) {
            if (object == null) {
                return false;
            }

            if (object instanceof Number) {
                Number number = (Number) object;
                return number.doubleValue() != 0.0;
            }

            String s = object.toString();
            if (s != null) {
                s = s.trim();
            }
            if (EMPTY_STRING.equals(s)) {
                return false;
            } else if ("true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s)) {
                return Boolean.parseBoolean(s);
            }

            if (object instanceof Collection) {
                return !((Collection) object).isEmpty();
            }

            if (object instanceof Map) {
                return !((Map) object).isEmpty();
            }

            if (object instanceof Iterable<?>) {
                return ((Iterable<?>) object).iterator().hasNext();
            }

            if (object instanceof Iterator<?>) {
                return ((Iterator<?>) object).hasNext();
            }

            if (object instanceof Optional) {
                return toBoolean(((Optional) object).orElse(false));
            }

            if (object.getClass().isArray()) {
                return Array.getLength(object) > 0;
            }

            return true;
        }
        return super.toBoolean(object);
    }

    @Override
    protected Object getProperty(Object target, Object propertyObj) {
        if (target == null || propertyObj == null) {
            return null;
        }
        Object result = super.getProperty(target, propertyObj);
        if (result == null && target instanceof Adaptable) {
            ValueMap valueMap = ((Adaptable) target).adaptTo(ValueMap.class);
            if (valueMap != null) {
                String property = toString(propertyObj);
                result = valueMap.get(property);
            }
        }
        return result;
    }

}
