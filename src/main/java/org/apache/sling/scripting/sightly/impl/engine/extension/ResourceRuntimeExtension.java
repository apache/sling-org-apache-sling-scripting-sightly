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
package org.apache.sling.scripting.sightly.impl.engine.extension;

import javax.script.Bindings;
import javax.servlet.RequestDispatcher;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestDispatcherOptions;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.SyntheticResource;
import org.apache.sling.scripting.sightly.SightlyException;
import org.apache.sling.scripting.sightly.extension.RuntimeExtension;
import org.apache.sling.scripting.sightly.impl.utils.BindingsUtils;
import org.apache.sling.scripting.sightly.render.RenderContext;
import org.apache.sling.scripting.sightly.render.RuntimeObjectModel;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runtime support for including resources in a HTL script through {@code data-sly-resource}.
 */
@Component(
        service = RuntimeExtension.class,
        property = {RuntimeExtension.NAME + "=" + RuntimeExtension.RESOURCE})
public class ResourceRuntimeExtension implements RuntimeExtension {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceRuntimeExtension.class);

    private static final String OPTION_RESOURCE_TYPE = "resourceType";
    private static final String OPTION_PATH = "path";
    private static final String OPTION_PREPEND_PATH = "prependPath";
    private static final String OPTION_APPEND_PATH = "appendPath";
    private static final String OPTION_SELECTORS = "selectors";
    private static final String OPTION_REMOVE_SELECTORS = "removeSelectors";
    private static final String OPTION_ADD_SELECTORS = "addSelectors";
    private static final String OPTION_REQUEST_ATTRIBUTES = "requestAttributes";

    @Override
    public Object call(final RenderContext renderContext, Object... arguments) {
        ExtensionUtils.checkArgumentCount(RuntimeExtension.RESOURCE, arguments, 2);
        return provideResource(renderContext, arguments[0], (Map<String, Object>) arguments[1]);
    }

    private String provideResource(final RenderContext renderContext, Object pathObj, Map<String, Object> options) {
        Map<String, Object> opts = new HashMap<>(options);
        final Bindings bindings = renderContext.getBindings();
        SlingHttpServletRequest request = BindingsUtils.getRequest(bindings);
        Map originalAttributes =
                ExtensionUtils.setRequestAttributes(request, (Map) options.remove(OPTION_REQUEST_ATTRIBUTES));
        RuntimeObjectModel runtimeObjectModel = renderContext.getObjectModel();
        StringWriter writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        if (pathObj instanceof Resource) {
            Resource includedResource = (Resource) pathObj;
            RequestDispatcherOptions requestDispatcherOptions =
                    handleDispatcherOptions(request, new LinkedHashSet<String>(), opts, runtimeObjectModel);
            includeResource(request, bindings, printWriter, includedResource, requestDispatcherOptions);
        } else {
            String includePath = runtimeObjectModel.toString(pathObj);
            // build path completely
            includePath = buildPath(includePath, options, request.getResource());
            if (includePath != null) {
                // check if path identifies an existing resource
                Resource includedResource = request.getResourceResolver().getResource(includePath);
                PathInfo pathInfo;
                if (includedResource != null) {
                    RequestDispatcherOptions requestDispatcherOptions =
                            handleDispatcherOptions(request, new LinkedHashSet<String>(), opts, runtimeObjectModel);
                    includeResource(request, bindings, printWriter, includedResource, requestDispatcherOptions);
                } else {
                    // analyse path and decompose potential selectors from the path
                    pathInfo = new PathInfo(includePath);
                    RequestDispatcherOptions requestDispatcherOptions =
                            handleDispatcherOptions(request, pathInfo.selectors, opts, runtimeObjectModel);
                    includeResource(request, bindings, printWriter, pathInfo.path, requestDispatcherOptions);
                }
            } else {
                // use the current resource
                RequestDispatcherOptions requestDispatcherOptions =
                        handleDispatcherOptions(request, new LinkedHashSet<String>(), opts, runtimeObjectModel);
                includeResource(request, bindings, printWriter, request.getResource(), requestDispatcherOptions);
            }
        }
        ExtensionUtils.setRequestAttributes(request, originalAttributes);
        return writer.toString();
    }

    private RequestDispatcherOptions handleDispatcherOptions(
            SlingHttpServletRequest request,
            Set<String> selectors,
            Map<String, Object> options,
            RuntimeObjectModel runtimeObjectModel) {
        RequestDispatcherOptions requestDispatcherOptions = new RequestDispatcherOptions();
        if (selectors.isEmpty()) {
            selectors.addAll(Arrays.asList(request.getRequestPathInfo().getSelectors()));
        }
        requestDispatcherOptions.setAddSelectors(getSelectorString(selectors));
        requestDispatcherOptions.setReplaceSelectors("");
        if (options.containsKey(OPTION_SELECTORS)) {
            Object selectorsObject = getAndRemoveOption(options, OPTION_SELECTORS);
            selectors.clear();
            addSelectors(selectors, selectorsObject, runtimeObjectModel);
            requestDispatcherOptions.setAddSelectors(getSelectorString(selectors));
            requestDispatcherOptions.setReplaceSelectors("");
        }
        if (options.containsKey(OPTION_ADD_SELECTORS)) {
            Object selectorsObject = getAndRemoveOption(options, OPTION_ADD_SELECTORS);
            addSelectors(selectors, selectorsObject, runtimeObjectModel);
            requestDispatcherOptions.setAddSelectors(getSelectorString(selectors));
            requestDispatcherOptions.setReplaceSelectors("");
        }
        if (options.containsKey(OPTION_REMOVE_SELECTORS)) {
            Object selectorsObject = getAndRemoveOption(options, OPTION_REMOVE_SELECTORS);
            if (selectorsObject instanceof String) {
                String selectorString = (String) selectorsObject;
                String[] parts = selectorString.split("\\.");
                for (String s : parts) {
                    selectors.remove(s);
                }
            } else if (selectorsObject instanceof Object[]) {
                for (Object s : (Object[]) selectorsObject) {
                    String selector = runtimeObjectModel.toString(s);
                    if (StringUtils.isNotEmpty(selector)) {
                        selectors.remove(selector);
                    }
                }
            } else if (selectorsObject == null) {
                selectors.clear();
            }
            String selectorString = getSelectorString(selectors);
            if (StringUtils.isEmpty(selectorString)) {
                requestDispatcherOptions.setReplaceSelectors("");
            } else {
                requestDispatcherOptions.setAddSelectors(getSelectorString(selectors));
                requestDispatcherOptions.setReplaceSelectors("");
            }
        }
        if (options.containsKey(OPTION_RESOURCE_TYPE)) {
            String resourceType = runtimeObjectModel.toString(getAndRemoveOption(options, OPTION_RESOURCE_TYPE));
            if (StringUtils.isNotEmpty(resourceType)) {
                requestDispatcherOptions.setForceResourceType(resourceType);
            }
        }
        return requestDispatcherOptions;
    }

    private void addSelectors(Set<String> selectors, Object selectorsObject, RuntimeObjectModel runtimeObjectModel) {
        if (selectorsObject instanceof String) {
            String selectorString = (String) selectorsObject;
            String[] parts = selectorString.split("\\.");
            selectors.addAll(Arrays.asList(parts));
        } else if (selectorsObject instanceof Object[]) {
            for (Object s : (Object[]) selectorsObject) {
                String selector = runtimeObjectModel.toString(s);
                if (StringUtils.isNotEmpty(selector)) {
                    selectors.add(selector);
                }
            }
        }
    }

    private String buildPath(String path, Map<String, Object> options, Resource currentResource) {
        String prependPath = getOption(OPTION_PREPEND_PATH, options, StringUtils.EMPTY);
        if (prependPath == null) {
            prependPath = StringUtils.EMPTY;
        }
        if (StringUtils.isNotEmpty(prependPath)) {
            if (!prependPath.startsWith("/")) {
                prependPath = "/" + prependPath;
            }
            if (!prependPath.endsWith("/")) {
                prependPath += "/";
            }
        }
        path = getOption(OPTION_PATH, options, StringUtils.isNotEmpty(path) ? path : StringUtils.EMPTY);
        String appendPath = getOption(OPTION_APPEND_PATH, options, StringUtils.EMPTY);
        if (appendPath == null) {
            appendPath = StringUtils.EMPTY;
        }
        if (StringUtils.isNotEmpty(appendPath)) {
            if (!appendPath.startsWith("/")) {
                appendPath = "/" + appendPath;
            }
        }
        String finalPath = prependPath + path + appendPath;
        if (!finalPath.startsWith("/")) {
            finalPath = currentResource.getPath() + "/" + finalPath;
        }
        return ResourceUtil.normalize(finalPath);
    }

    private String getOption(String option, Map<String, Object> options, String defaultValue) {
        if (options.containsKey(option)) {
            return (String) options.get(option);
        }
        return defaultValue;
    }

    private Object getAndRemoveOption(Map<String, Object> options, String property) {
        return options.remove(property);
    }

    private String getSelectorString(Set<String> selectors) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (String s : selectors) {
            sb.append(s);
            if (i < selectors.size() - 1) {
                sb.append(".");
                i++;
            }
        }
        String selectorString = sb.toString();
        return StringUtils.isNotEmpty(selectorString) ? selectorString : null;
    }

    private void includeResource(
            SlingHttpServletRequest request,
            final Bindings bindings,
            PrintWriter out,
            String path,
            RequestDispatcherOptions requestDispatcherOptions) {
        if (StringUtils.isEmpty(path)) {
            throw new SightlyException("Resource path cannot be empty");
        } else {
            Resource includeRes = request.getResourceResolver().resolve(path);
            if (ResourceUtil.isNonExistingResource(includeRes)) {
                String resourceType = request.getResource().getResourceType();
                if (requestDispatcherOptions.containsKey(RequestDispatcherOptions.OPT_FORCE_RESOURCE_TYPE)) {
                    resourceType = requestDispatcherOptions.getForceResourceType();
                }
                includeRes = new SyntheticResource(request.getResourceResolver(), path, resourceType);
            }
            includeResource(request, bindings, out, includeRes, requestDispatcherOptions);
        }
    }

    private void includeResource(
            SlingHttpServletRequest request,
            final Bindings bindings,
            PrintWriter out,
            Resource includeRes,
            RequestDispatcherOptions requestDispatcherOptions) {
        if (includeRes == null) {
            throw new SightlyException("Resource cannot be null");
        } else {
            if (request.getResource().getPath().equals(includeRes.getPath())) {
                String requestSelectorString = request.getRequestPathInfo().getSelectorString();
                String requestDispatcherAddSelectors = requestDispatcherOptions.getAddSelectors();
                if ((requestSelectorString == null
                                ? requestDispatcherAddSelectors == null
                                : requestSelectorString.equals(requestDispatcherAddSelectors))
                        && StringUtils.EMPTY.equals(requestDispatcherOptions.getReplaceSelectors())
                        && (requestDispatcherOptions.getForceResourceType() == null
                                || requestDispatcherOptions
                                        .getForceResourceType()
                                        .equals(request.getResource().getResourceType()))) {
                    LOGGER.warn(
                            "Will not include resource {} since this will lead to a {} exception.",
                            includeRes.getPath(),
                            "org.apache.sling.api.request.RecursionTooDeepException");
                    return;
                }
            }
            SlingHttpServletResponse customResponse =
                    new PrintWriterResponseWrapper(out, BindingsUtils.getResponse(bindings));
            RequestDispatcher dispatcher = request.getRequestDispatcher(includeRes, requestDispatcherOptions);
            try {
                if (dispatcher != null) {
                    dispatcher.include(request, customResponse);
                } else {
                    throw new SightlyException("Failed to include resource " + includeRes.getPath());
                }
            } catch (Exception e) {
                throw new SightlyException("Failed to include resource " + includeRes.getPath(), e);
            }
        }
    }

    private class PathInfo {
        private String path;
        private Set<String> selectors;

        PathInfo(String path) {
            selectors = getSelectorsFromPath(path);
            if (selectors.isEmpty()) {
                this.path = path;
            } else {
                String selectorString = getSelectorString(selectors);
                this.path = path.replace("." + selectorString, "");
            }
        }

        private Set<String> getSelectorsFromPath(String path) {
            Set<String> selectors = new LinkedHashSet<>();
            if (path != null) {
                String processingPath = path;
                int lastSlashPos = path.lastIndexOf('/');
                if (lastSlashPos > -1) {
                    processingPath = path.substring(lastSlashPos + 1, path.length());
                }
                int dotPos = processingPath.indexOf('.');
                if (dotPos > -1) {
                    int lastDotPos = processingPath.lastIndexOf('.');
                    // We're expecting selectors only when an extension is also present. If there's
                    // one dot it means we only have the extension
                    if (lastDotPos > dotPos) {
                        String selectorString = processingPath.substring(dotPos + 1, lastDotPos);
                        String[] selectorParts = selectorString.split("\\.");
                        selectors.addAll(Arrays.asList(selectorParts));
                    }
                }
            }
            return selectors;
        }
    }
}
