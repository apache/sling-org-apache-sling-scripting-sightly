/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.apache.sling.scripting.sightly.impl.utils;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.observation.ExternalResourceChangeListener;
import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.api.resource.observation.ResourceChangeListener;
import org.apache.sling.api.scripting.SlingScriptHelper;
import org.apache.sling.scripting.api.resource.ScriptingResourceResolverProvider;
import org.apache.sling.scripting.sightly.engine.ResourceResolution;
import org.apache.sling.scripting.sightly.impl.engine.SightlyEngineConfiguration;
import org.apache.sling.scripting.sightly.render.RenderContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

@Component(
        service = {
                ScriptDependencyResolver.class,
        },
        property = {
                // listen to changes to all search paths
                ResourceChangeListener.PATHS + "=.",
                ResourceChangeListener.CHANGES + "=" + ResourceChangeListener.CHANGE_ADDED,
                ResourceChangeListener.CHANGES + "=" + ResourceChangeListener.CHANGE_CHANGED,
                ResourceChangeListener.CHANGES + "=" + ResourceChangeListener.CHANGE_REMOVED,
        }
)
public class ScriptDependencyResolver implements ResourceChangeListener, ExternalResourceChangeListener, BundleListener {

    public static final String BUNDLED_SCRIPTS_REQUIREMENT = "osgi.extender;filter:=\"(&(osgi.extender=sling.scripting)(version>=1.0.0)(!" +
            "(version>=2.0.0)))\"";

    @Reference
    private SightlyEngineConfiguration sightlyEngineConfiguration;

    @Reference
    private ScriptingResourceResolverProvider scriptingResourceResolverProvider;

    // not used, however we want this component to restart if the RRF is reconfigured
    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    private Map<String, String> resolutionCache = new Cache(0);
    private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock(true);
    private final Lock readLock = rwl.readLock();
    private final Lock writeLock = rwl.writeLock();

    private ServiceRegistration<ResourceChangeListener> resourceChangeListenerServiceRegistration;

    private boolean cacheEnabled = false;

    private static final String NOT_FOUND_MARKER = "#NOT_FOUND#";

    @SuppressWarnings("squid:S1149")
    @Activate
    private void activate(ComponentContext componentContext) {
        int cacheSize = sightlyEngineConfiguration.getScriptResolutionCacheSize();
        if (cacheSize < 1024) {
            cacheEnabled = false;
            resolutionCache = new Cache(0);
        } else {
            cacheEnabled = true;
            resolutionCache = new Cache(cacheSize);
        }
        if (cacheEnabled) {
            componentContext.getBundleContext().addBundleListener(this);
            Dictionary<String, Object> resourceChangeListenerProperties = new Hashtable<>();
            resourceChangeListenerProperties.put(ResourceChangeListener.PATHS, ".");
            resourceChangeListenerProperties.put(ResourceChangeListener.CHANGES, new String[] {
                    ResourceChangeListener.CHANGE_ADDED,
                    ResourceChangeListener.CHANGE_CHANGED,
                    ResourceChangeListener.CHANGE_REMOVED
            });
            resourceChangeListenerServiceRegistration =
                    componentContext.getBundleContext().registerService(ResourceChangeListener.class, this, resourceChangeListenerProperties);
        }
    }

    @Deactivate
    private void deactivate(ComponentContext componentContext) {
        if (resourceChangeListenerServiceRegistration != null) {
            resourceChangeListenerServiceRegistration.unregister();
        }
        componentContext.getBundleContext().removeBundleListener(this);
    }

    public Resource resolveScript(RenderContext renderContext, String scriptIdentifier) {
        SlingHttpServletRequest request = BindingsUtils.getRequest(renderContext.getBindings());
        if (!cacheEnabled) {
            return internalResolveScript(request, renderContext, scriptIdentifier);
        }
        readLock.lock();
        try {
            String cacheKey = request.getResource().getResourceType() + ":" + scriptIdentifier;
            Resource result = null;
            if (!resolutionCache.containsKey(cacheKey)) {
                readLock.unlock();
                writeLock.lock();
                try {
                    result = internalResolveScript(request, renderContext, scriptIdentifier);
                    if (result != null) {
                       resolutionCache.put(cacheKey, result.getPath());
                    } else {
                        resolutionCache.put(cacheKey, NOT_FOUND_MARKER);
                    }
                    readLock.lock();
                } finally {
                    writeLock.unlock();
                }
            } else {
                String scriptPath = resolutionCache.get(cacheKey);
                if (!scriptPath.equals(NOT_FOUND_MARKER)) {
                    result = scriptingResourceResolverProvider.getRequestScopedResourceResolver().getResource(scriptPath);
                }
            }
            return result;
        } finally {
            readLock.unlock();
        }
    }

    private @Nullable Resource internalResolveScript(SlingHttpServletRequest request, RenderContext renderContext,
                                                     String scriptIdentifier) {
        Resource caller =
                ResourceResolution.getResourceForRequest(scriptingResourceResolverProvider.getRequestScopedResourceResolver(),
                        request);
        Resource result = ResourceResolution.getResourceFromSearchPath(caller, scriptIdentifier);
        if (result == null) {
            SlingScriptHelper sling = BindingsUtils.getHelper(renderContext.getBindings());
            if (sling != null) {
                caller = scriptingResourceResolverProvider.getRequestScopedResourceResolver().getResource(sling.getScript().getScriptResource().getPath());
                result = ResourceResolution.getResourceFromSearchPath(caller, scriptIdentifier);
            }
        }
        return result;
    }

    @Override
    public void onChange(@NotNull List<ResourceChange> changes) {
        // we won't be specific about the changes; wipe the whole cache
        writeLock.lock();
        try {
            resolutionCache.clear();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void bundleChanged(BundleEvent event) {
        // we won't be specific about the changes; wipe the whole cache
        Dictionary<String, String> bundleHeaders = event.getBundle().getHeaders();
        String requireCapabilityHeader = bundleHeaders.get("Require-Capability");
        if (StringUtils.isNotEmpty(requireCapabilityHeader) && requireCapabilityHeader.contains(BUNDLED_SCRIPTS_REQUIREMENT)) {
            writeLock.lock();
            try {
                resolutionCache.clear();
            } finally {
                writeLock.unlock();
            }
        }
    }

    private static class Cache extends LinkedHashMap<String, String> {

        private final int cacheSize;

        public Cache(int cacheSize) {
            super();
            this.cacheSize = cacheSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > cacheSize;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Cache) {
                Cache other = (Cache) o;
                return super.equals(o) && cacheSize == other.cacheSize;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), cacheSize);
        }
    }

}
