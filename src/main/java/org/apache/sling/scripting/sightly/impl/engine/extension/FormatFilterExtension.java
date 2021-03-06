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

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
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

@Component(
        service = RuntimeExtension.class,
        property = {
                RuntimeExtension.NAME + "=" + RuntimeExtension.FORMAT
        }
)
public class FormatFilterExtension implements RuntimeExtension {

    private static final Pattern PLACEHOLDER_REGEX = Pattern.compile("\\{\\d+}");
    private static final String FORMAT_OPTION = "format";
    private static final String TYPE_OPTION = "type";
    private static final String LOCALE_OPTION = "locale";
    private static final String TIMEZONE_OPTION = "timezone";

    private static final String DATE_FORMAT_TYPE = "date";
    private static final String NUMBER_FORMAT_TYPE = "number";
    private static final String STRING_FORMAT_TYPE = "string";

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
            // somebody will hate me for this
            new SimpleDateFormat(source);
            return getDateFormattedString(runtimeObjectModel, source, options, formatObject);
        } catch (IllegalArgumentException e) {
            // ignore
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
        Locale locale = getLocale(runtimeObjectModel, options);
        TimeZone timezone = getTimezone(runtimeObjectModel, options);
        return formatDate(source, runtimeObjectModel.toDate(formatObject), locale, timezone);
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

    private int getPredefinedFormattingStyleFromValue(String value) {
        switch (value.toLowerCase(Locale.ROOT)) {
            case "default":
                return DateFormat.DEFAULT;
            case "short":
                return DateFormat.SHORT;
            case "medium":
                return DateFormat.MEDIUM;
            case "long":
                return DateFormat.LONG;
            case "full":
                return DateFormat.FULL;
            default:
                return -1;
        }
    }

    private String formatDate(String format, Date date, Locale locale, TimeZone timezone) {
        if (date == null) {
            return null;
        }
        try {
            final DateFormat formatter;
            int formattingStyle = getPredefinedFormattingStyleFromValue(format);
            if (formattingStyle != -1) {
                if (locale != null) {
                    formatter = DateFormat.getDateInstance(formattingStyle, locale);
                } else {
                    formatter = DateFormat.getDateInstance(formattingStyle);
                }
            } else {
                if (locale != null) {
                    formatter = new SimpleDateFormat(format, locale);
                } else {
                    formatter = new SimpleDateFormat(format);
                }
            }
            if (timezone != null) {
                formatter.setTimeZone(timezone);
            }
            return formatter.format(date);
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
