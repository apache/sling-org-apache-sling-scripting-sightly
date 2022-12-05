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
package org.apache.sling.scripting.sightly.engine.extension.i18n;

import org.apache.sling.scripting.sightly.render.RenderContext;
import org.jetbrains.annotations.Nullable;
import org.osgi.annotation.versioning.ConsumerType;

@ConsumerType
public interface I18nBasenameProvider {

    /**
     * Provides a way to define a basename that is not passed as parameter on the i18n runtime extension
     *
     * @param renderContext the renderContext passed initially to the HTL Script Engine
     * @return the basename if one is found, {@code null} otherwise
     */
    @Nullable String getBasename(RenderContext renderContext);

}
