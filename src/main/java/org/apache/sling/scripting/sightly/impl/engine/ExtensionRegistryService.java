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
package org.apache.sling.scripting.sightly.impl.engine;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.sling.scripting.sightly.extension.RuntimeExtension;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

/**
 * Aggregator for all runtime extensions.
 */
@Component(service = ExtensionRegistryService.class)
public class ExtensionRegistryService {

    private volatile Map<String, RuntimeExtension> mapping = new HashMap<>();
    private final Map<String, TreeSet<RuntimeExtensionReference>> extensions = new HashMap<>();

    public Map<String, RuntimeExtension> extensions() {
        return mapping;
    }

    @Reference(
            policy = ReferencePolicy.DYNAMIC,
            service = RuntimeExtension.class,
            cardinality = ReferenceCardinality.MULTIPLE
            )
    @SuppressWarnings("unused")
    protected void bindExtensionService(ServiceReference<RuntimeExtension> serviceReference, RuntimeExtension runtimeExtension) {
        final RuntimeExtensionReference rer = new RuntimeExtensionReference(serviceReference, runtimeExtension);
        synchronized (extensions) {
            final Set<RuntimeExtensionReference> namedExtensions = extensions.computeIfAbsent(rer.getName(), key -> new TreeSet<>());
            if (namedExtensions.add(rer)) {
                mapping = getRuntimeExtensions();
            }
        }
    }

    @SuppressWarnings("unused")
    protected void unbindExtensionService(ServiceReference<RuntimeExtension> serviceReference) {
        final RuntimeExtensionReference rer = new RuntimeExtensionReference(serviceReference, null);
        synchronized (extensions) {
            final Set<RuntimeExtensionReference> namedExtensions = extensions.get(rer.getName());
            if (namedExtensions != null && namedExtensions.remove(rer)) {
                if (namedExtensions.isEmpty()) {
                    extensions.remove(rer.getName());
                }
                mapping = getRuntimeExtensions();
            }
        }
    }

    private Map<String, RuntimeExtension> getRuntimeExtensions() {
        HashMap<String, RuntimeExtension> replacement = new HashMap<>();
        for (Map.Entry<String, TreeSet<RuntimeExtensionReference>> entry : extensions.entrySet()) {
            replacement.put(entry.getKey(), entry.getValue().last().getService());
        }
        return Collections.unmodifiableMap(replacement);
    }


}
