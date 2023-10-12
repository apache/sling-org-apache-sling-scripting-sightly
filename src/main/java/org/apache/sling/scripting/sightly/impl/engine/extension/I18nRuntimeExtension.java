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
package org.apache.sling.scripting.sightly.impl.engine.extension;

import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import javax.script.Bindings;

import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.scripting.SlingScriptHelper;
import org.apache.sling.i18n.ResourceBundleProvider;
import org.apache.sling.scripting.sightly.extension.RuntimeExtension;
import org.apache.sling.scripting.sightly.engine.extension.i18n.I18nBasenameProvider;
import org.apache.sling.scripting.sightly.impl.utils.BindingsUtils;
import org.apache.sling.scripting.sightly.render.RenderContext;
import org.apache.sling.scripting.sightly.render.RuntimeObjectModel;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
        service = RuntimeExtension.class,
        property = {
                RuntimeExtension.NAME + "=" + RuntimeExtension.I18N
        }
)
public class I18nRuntimeExtension implements RuntimeExtension {

    private static final Logger LOG = LoggerFactory.getLogger(I18nRuntimeExtension.class);

    @Reference(cardinality = ReferenceCardinality.OPTIONAL)
    private I18nBasenameProvider i18nBasenameProvider;

    @Override
    public Object call(final RenderContext renderContext, Object... arguments) {
        ExtensionUtils.checkArgumentCount(RuntimeExtension.I18N, arguments, 2);
        RuntimeObjectModel runtimeObjectModel = renderContext.getObjectModel();
        String text = runtimeObjectModel.toString(arguments[0]);
        Map<String, Object> options = (Map<String, Object>) arguments[1];
        String locale = runtimeObjectModel.toString(options.get("locale"));
        String hint = runtimeObjectModel.toString(options.get("hint"));
        String basename = getBasename(renderContext, options);
        final Bindings bindings = renderContext.getBindings();
        return get(bindings, text, locale, basename, hint);
    }

    private String getBasename(RenderContext renderContext, Map<String, Object> options) {
        RuntimeObjectModel runtimeObjectModel = renderContext.getObjectModel();
        String basename = runtimeObjectModel.toString(options.get("basename"));
        if (StringUtils.isEmpty(basename) && i18nBasenameProvider != null) {
            return i18nBasenameProvider.getBasename(renderContext);
        }
        return basename;
    }

    private String get(final Bindings bindings, String text, String locale, String basename, String hint) {

        final SlingScriptHelper slingScriptHelper = BindingsUtils.getHelper(bindings);
        final SlingHttpServletRequest request = BindingsUtils.getRequest(bindings);
        final ResourceBundleProvider resourceBundleProvider = slingScriptHelper.getService(ResourceBundleProvider.class);
        if (resourceBundleProvider != null) {
            String key = text;
            if (StringUtils.isNotEmpty(hint)) {
                key += " ((" + hint + "))";
            }
            if (StringUtils.isEmpty(locale)) {
                Enumeration<Locale> requestLocales = request.getLocales();
                while (requestLocales.hasMoreElements()) {
                    Locale l = requestLocales.nextElement();
                    String translation = getTranslation(resourceBundleProvider, basename, key, l);
                    if (translation != null) {
                        return translation;
                    }
                }
            } else {
                try {
                    Locale l = LocaleUtils.toLocale(locale);
                    String translation = getTranslation(resourceBundleProvider, basename, key, l);
                    if (translation != null) {
                        return translation;
                    }
                } catch (IllegalArgumentException e) {
                    LOG.warn("Invalid locale detected: {}.", locale);
                    return text;
                }

            }
        }
        LOG.warn("No translation found for string '{}' using expression provided locale '{}' or default locale '{}'",
                text, locale, request.getLocale().getLanguage());
        return text;
    }

    private String getTranslation(ResourceBundleProvider resourceBundleProvider, String basename, String key, Locale locale) {
        ResourceBundle resourceBundle;
        if (StringUtils.isNotEmpty(basename)) {
            resourceBundle = resourceBundleProvider.getResourceBundle(basename, locale);
        } else {
            resourceBundle = resourceBundleProvider.getResourceBundle(locale);
        }
        if (resourceBundle != null && resourceBundle.containsKey(key)) {
            return resourceBundle.getString(key);
        }
        return null;
    }
}
