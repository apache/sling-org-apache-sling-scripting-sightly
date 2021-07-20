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
package org.apache.sling.scripting.sightly.impl.engine.extension;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.scripting.sightly.SightlyException;
import org.apache.sling.scripting.sightly.extension.RuntimeExtension;
import org.apache.sling.scripting.sightly.render.RenderContext;
import org.apache.sling.scripting.sightly.render.RuntimeObjectModel;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
        service = RuntimeExtension.class,
        property = {
                RuntimeExtension.NAME + "=" + RuntimeExtension.FORMAT
        }
)
public class FormatFilterExtension implements RuntimeExtension {

    protected static final String FORMAT_OPTION = "format";
    protected static final String TYPE_OPTION = "type";
    protected static final String LOCALE_OPTION = "locale";
    protected static final String TIMEZONE_OPTION = "timezone";

    protected static final String DATE_FORMAT_TYPE = "date";
    protected static final String NUMBER_FORMAT_TYPE = "number";
    protected static final String STRING_FORMAT_TYPE = "string";

    private static final Logger LOG = LoggerFactory.getLogger(FormatFilterExtension.class);
    private static final Pattern PLACEHOLDER_REGEX = Pattern.compile("\\{\\d+}");

    @Override
    public Object call(final RenderContext renderContext, Object... arguments) {
        ExtensionUtils.checkArgumentCount(RuntimeExtension.FORMAT, arguments, 2);
        RuntimeObjectModel runtimeObjectModel = renderContext.getObjectModel();
        String source = runtimeObjectModel.toString(arguments[0]);
        Map<String, Object> options = (Map<String, Object>) arguments[1];

        String formattingType = runtimeObjectModel.toString(options.get(TYPE_OPTION));
        Object formatObject = options.get(FORMAT_OPTION);
        boolean hasPlaceHolders = PLACEHOLDER_REGEX.matcher(source).find();
        if (STRING_FORMAT_TYPE.equals(formattingType)) {
            return getFormattedString(runtimeObjectModel, source, formatObject);
        } else if (DATE_FORMAT_TYPE.equals(formattingType) || (!hasPlaceHolders && runtimeObjectModel.isDate(formatObject))) {
            return getDateFormattedString(runtimeObjectModel, source, options, formatObject);
        } else if (NUMBER_FORMAT_TYPE.equals(formattingType) || (!hasPlaceHolders && runtimeObjectModel.isNumber(formatObject))) {
            return getNumberFormattedString(runtimeObjectModel, source, options, formatObject);
        }
        if (hasPlaceHolders) {
            return getFormattedString(runtimeObjectModel, source, formatObject);
        }

        try {
            // try to parse as DateTimeFormatter
            // TODO: check if this is dead code
            DateTimeFormatter.ofPattern(source);
            return getDateFormattedString(runtimeObjectModel, source, options, formatObject);
        } catch (IllegalArgumentException ex) {
            LOG.trace("Not a datetime format: {}", source, ex);
        }
        try {
            // for this too, but such is life
            new DecimalFormat(source);
            return getNumberFormattedString(runtimeObjectModel, source, options, formatObject);
        } catch (IllegalArgumentException e) {
            // ignore
        }
        return getFormattedString(runtimeObjectModel, source, formatObject);
    }

    private Object getFormattedString(RuntimeObjectModel runtimeObjectModel, String source, Object formatObject) {
        Object[] params = decodeParams(runtimeObjectModel, formatObject);
        return formatString(runtimeObjectModel, source, params);
    }

    private String getNumberFormattedString(RuntimeObjectModel runtimeObjectModel, String source, Map<String, Object> options,
                                            Object formatObject) {
        Locale locale = getLocale(runtimeObjectModel, options);
        return formatNumber(source, runtimeObjectModel.toNumber(formatObject), locale);
    }

    private String getDateFormattedString(RuntimeObjectModel runtimeObjectModel, String source, Map<String, Object> options,
                                          Object formatObject) {
        return getDateFormattedString(runtimeObjectModel, source, options, formatObject, null);
    }

    private String getDateFormattedString(RuntimeObjectModel runtimeObjectModel, String source, Map<String, Object> options,
                                          Object formatObject, DateTimeFormatter formatter) {
        Locale locale = getLocale(runtimeObjectModel, options);
        TimeZone timezone = getTimezone(runtimeObjectModel, options);
        return formatDate(source, runtimeObjectModel.toDate(formatObject), locale, timezone, formatter);
    }

    private Locale getLocale(RuntimeObjectModel runtimeObjectModel, Map<String, Object> options) {
        String localeOption = null;
        if (options.containsKey(LOCALE_OPTION)) {
            localeOption = runtimeObjectModel.toString(options.get(LOCALE_OPTION));
        }
        if (StringUtils.isNotBlank(localeOption)) {
            return LocaleUtils.toLocale(localeOption);
        }
        return null;
    }

    private TimeZone getTimezone(RuntimeObjectModel runtimeObjectModel, Map<String, Object> options) {
        if ( options.containsKey(TIMEZONE_OPTION)) {
            return TimeZone.getTimeZone(runtimeObjectModel.toString(options.get(TIMEZONE_OPTION)));
        } else {
            Object formatObject = options.get(FORMAT_OPTION);
            if (formatObject instanceof Calendar) {
                return ((Calendar)formatObject).getTimeZone();
            }
            return TimeZone.getDefault();
        }
    }

    private Object[] decodeParams(RuntimeObjectModel runtimeObjectModel, Object paramObj) {
        if (paramObj == null) {
            return null;
        }
        if (runtimeObjectModel.isCollection(paramObj)) {
            return runtimeObjectModel.toCollection(paramObj).toArray();
        }
        return new Object[] {paramObj};
    }

    private String formatString(RuntimeObjectModel runtimeObjectModel, String source, Object[] params) {
        if (params == null) {
            return null;
        }
        Matcher matcher = PLACEHOLDER_REGEX.matcher(source);
        StringBuilder builder = new StringBuilder();
        int lastPos = 0;
        boolean matched = true;
        while (matched) {
            matched = matcher.find();
            if (matched) {
                String group = matcher.group();
                int paramIndex = Integer.parseInt(group.substring(1, group.length() - 1));
                String replacement = toString(runtimeObjectModel, params, paramIndex);
                int matchStart = matcher.start();
                int matchEnd = matcher.end();
                builder.append(source, lastPos, matchStart).append(replacement);
                lastPos = matchEnd;
            }
        }
        builder.append(source, lastPos, source.length());
        return builder.toString();
    }

    private String toString(RuntimeObjectModel runtimeObjectModel, Object[] params, int index) {
        if (index >= 0 && index < params.length) {
            return runtimeObjectModel.toString(params[index]);
        }
        return "";
    }

    private FormatStyle getPredefinedFormattingStyleFromValue(String value) {
        switch (value.toLowerCase(Locale.ROOT)) {
            case "default":
            case "medium":
                return FormatStyle.MEDIUM;
            case "short":
                return FormatStyle.SHORT;
            case "long":
                return FormatStyle.LONG;
            case "full":
                return FormatStyle.FULL;
            default:
                return null;
        }
    }

    private String formatDate(String format, Date date, Locale locale, TimeZone timezone, DateTimeFormatter formatter) {
        if (date == null) {
            return null;
        }
        try {
            if (formatter == null) {
                FormatStyle formattingStyle = getPredefinedFormattingStyleFromValue(format);
                if (formattingStyle != null) {
                    formatter = DateTimeFormatter.ofLocalizedDate(formattingStyle);
                } else {
                    formatter = DateTimeFormatter.ofPattern(format);
                }
            }
            if (locale != null) {
                formatter = formatter.withLocale(locale);
            }
            return formatter.format(timezone != null ? date.toInstant().atZone(timezone.toZoneId()) : date.toInstant());
        } catch (Exception e) {
            String error = String.format("Error during formatting of date %s with format %s, locale %s and timezone %s", date, format, locale, timezone);
            throw new SightlyException( error, e);
        }
    }

    private String formatNumber(String format, Number number, Locale locale) {
        if (number == null) {
            return null;
        }
        try {
            NumberFormat formatter;
            if (locale != null) {
                formatter = new DecimalFormat(format, new DecimalFormatSymbols(locale));
            } else {
                formatter = new DecimalFormat(format);
            }
            return formatter.format(number);
        } catch (Exception e) {
            String error = String.format("Error during formatting of number %s with format %s and locale %s", number, format, locale);
            throw new SightlyException( error, e);
        }
    }
}
