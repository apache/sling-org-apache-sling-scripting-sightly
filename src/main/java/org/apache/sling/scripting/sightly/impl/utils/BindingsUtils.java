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
package org.apache.sling.scripting.sightly.impl.utils;

import javax.script.Bindings;

import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.scripting.LazyBindings;
import org.apache.sling.api.scripting.SlingBindings;
import org.apache.sling.api.scripting.SlingScriptHelper;

/**
 * {@code BindingsUtils} provides helper methods for retrieving commonly used objects from a {@link javax.script.Bindings} map.
 */
public class BindingsUtils {

    private BindingsUtils() {
        // to hide public ctor
    }

    /**
     * Retrieves the {@link Resource} from a {@link Bindings} map.
     *
     * @param bindings the bindings map
     * @return the {@link Resource} if found, {@code null} otherwise
     */
    public static Resource getResource(Bindings bindings) {
        return (Resource) bindings.get(SlingBindings.RESOURCE);
    }

    /**
     * Retrieves the {@link org.apache.sling.api.SlingHttpServletRequest} from a {@link Bindings} map.
     *
     * @param bindings the bindings maps
     * @return the {@link org.apache.sling.api.SlingHttpServletRequest} if found, {@code null} otherwise
     * @deprecated use {@link #getJakartaRequest(Bindings)} instead
     */
    @Deprecated(since = "2.0.0-1.4.0")
    public static org.apache.sling.api.SlingHttpServletRequest getRequest(Bindings bindings) {
        return (org.apache.sling.api.SlingHttpServletRequest) bindings.get(SlingBindings.REQUEST);
    }

    /**
     * Retrieves the {@link org.apache.sling.api.SlingHttpServletResponse} from a {@link Bindings} map.
     *
     * @param bindings the bindings maps
     * @return the {@link org.apache.sling.api.SlingHttpServletResponse} if found, {@code null} otherwise
     * @deprecated use {@link #getJakartaResponse(Bindings)} instead
     */
    @Deprecated(since = "2.0.0-1.4.0")
    public static org.apache.sling.api.SlingHttpServletResponse getResponse(Bindings bindings) {
        return (org.apache.sling.api.SlingHttpServletResponse) bindings.get(SlingBindings.RESPONSE);
    }

    /**
     * Retrieves the {@link SlingJakartaHttpServletRequest} from a {@link Bindings} map.
     *
     * @param bindings the bindings maps
     * @return the {@link SlingJakartaHttpServletRequest} if found, {@code null} otherwise
     */
    public static SlingJakartaHttpServletRequest getJakartaRequest(Bindings bindings) {
        return (SlingJakartaHttpServletRequest) bindings.get(SlingBindings.JAKARTA_REQUEST);
    }

    /**
     * Retrieves the {@link SlingJakartaHttpServletResponse} from a {@link Bindings} map.
     *
     * @param bindings the bindings maps
     * @return the {@link SlingJakartaHttpServletResponse} if found, {@code null} otherwise
     */
    public static SlingJakartaHttpServletResponse getJakartaResponse(Bindings bindings) {
        return (SlingJakartaHttpServletResponse) bindings.get(SlingBindings.JAKARTA_RESPONSE);
    }

    /**
     * Retrieves the {@link SlingScriptHelper} from a {@link Bindings} map.
     *
     * @param bindings the bindings map
     * @return the {@link SlingScriptHelper} if found, {@code null} otherwise
     */
    public static SlingScriptHelper getHelper(Bindings bindings) {
        return (SlingScriptHelper) bindings.get(SlingBindings.SLING);
    }

    /**
     * Combine two bindings objects. Priority goes to latter bindings.
     *
     * @param former first map of bindings
     * @param latter second map of bindings, which can override the fist one
     * @return the merging of the two maps
     */
    public static Bindings merge(Bindings former, Bindings latter) {
        Bindings bindings = new LazyBindings();
        bindings.putAll(former);
        bindings.putAll(latter);
        return bindings;
    }
}
