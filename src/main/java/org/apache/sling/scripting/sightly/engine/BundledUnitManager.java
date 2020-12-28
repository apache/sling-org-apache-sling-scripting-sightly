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
package org.apache.sling.scripting.sightly.engine;

import java.net.URL;

import javax.script.Bindings;

import org.apache.sling.scripting.api.bundle.BundledRenderUnit;
import org.apache.sling.scripting.sightly.render.RenderUnit;
import org.jetbrains.annotations.Nullable;
import org.osgi.annotation.versioning.ProviderType;

@ProviderType
public interface BundledUnitManager {

    /**
     * <p>
     * Given a {@link Bindings} map, this method will check if the {@code bindings} contain a value for the {@link
     * BundledRenderUnit#VARIABLE} property and if the object provided by {@link BundledRenderUnit#getUnit()} is an instance of a {@link
     * RenderUnit}. If so, this service will return the {@link ClassLoader} of the {@link org.osgi.framework.Bundle} providing the {@link
     * BundledRenderUnit}.</p>
     *
     * @param bindings the bindings passed initially to the HTL Script Engine
     * @return the {@link BundledRenderUnit}'s classloader if one is found, {@code null} otherwise
     */
    @Nullable ClassLoader getBundledRenderUnitClassloader(Bindings bindings);

    /**
     * Given a {@link Bindings} map, this method will check if the {@code bindings} contain a value for the {@link
     * BundledRenderUnit#VARIABLE} property and, if a {@link BundledRenderUnit} is found, attempt to return the URL of dependency that the
     * {@link BundledRenderUnit} needs to load. This will take into account the bundle wirings of the unit's providing bundle (see {@link
     * BundledRenderUnit#getBundle()}).
     *
     * @param bindings   the bindings passed initially to the HTL Script Engine
     * @param identifier the identifier of the dependency that a {@link BundledRenderUnit} from the {@link Bindings} needs to load
     * @return the URL of the {@code identifier} dependency, if one was found
     */
    URL getScript(Bindings bindings, String identifier);

}
