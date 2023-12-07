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

import org.apache.sling.scripting.sightly.extension.RuntimeExtension;
import org.jetbrains.annotations.NotNull;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.util.converter.Converters;

class RuntimeExtensionReference implements Comparable<RuntimeExtensionReference> {
    private final int priority;
    private final String name;
    private final ServiceReference<RuntimeExtension> serviceReference;
    private final RuntimeExtension runtimeExtension;

    RuntimeExtensionReference(ServiceReference<RuntimeExtension> serviceReference, RuntimeExtension runtimeExtension) {
        this.serviceReference = serviceReference;
        this.runtimeExtension = runtimeExtension;
        final Object ranking = serviceReference.getProperty(Constants.SERVICE_RANKING);
        if ( ranking instanceof Integer ) {
            this.priority = (Integer)ranking;
        } else {
            this.priority = 0;
        }
        this.name = Converters.standardConverter().convert(serviceReference.getProperty(RuntimeExtension.NAME)).defaultValue("").to(String.class);
    }

    @Override
    public int compareTo(@NotNull RuntimeExtensionReference other) {
        if (equals(other)) {
            return 0;
        }
        if (name.equals(other.name)) {
            return priority - other.priority;
        }
        return name.compareTo(other.name);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof RuntimeExtensionReference) {
            RuntimeExtensionReference other = (RuntimeExtensionReference) obj;
            return serviceReference.equals(other.serviceReference);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return serviceReference.hashCode();
    }

    public String getName() {
        return name;
    }

    ServiceReference<RuntimeExtension> getServiceReference() {
        return serviceReference;
    }

    RuntimeExtension getService() {
        return runtimeExtension;
    }
}
