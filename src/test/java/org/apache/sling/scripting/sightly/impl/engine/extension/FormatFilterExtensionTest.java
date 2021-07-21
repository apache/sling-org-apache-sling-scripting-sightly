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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.script.Bindings;
import javax.script.SimpleBindings;

import org.apache.sling.scripting.sightly.SightlyException;
import org.apache.sling.scripting.sightly.render.AbstractRuntimeObjectModel;
import org.apache.sling.scripting.sightly.render.RenderContext;
import org.apache.sling.scripting.sightly.render.RuntimeObjectModel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class FormatFilterExtensionTest {

    private final RenderContext renderContext = new RenderContext() {
        @Override public RuntimeObjectModel getObjectModel() {
            return new AbstractRuntimeObjectModel() {
            };
        }

        @Override public Bindings getBindings() {
            return new SimpleBindings();
        }

        @Override public Object call(String s, Object... objects) {
            return null;
        }
    };
    private final FormatFilterExtension subject = new FormatFilterExtension();
    private final Date testDate = Date.from(LocalDateTime.of(1918, 12, 1, 0, 0, 0, 0)
        .atZone(ZoneId.of("UTC"))
        .toInstant());

    @Test
    public void testDateFormatNull() {
        assertNull(subject.call(renderContext, "default", new HashMap<String, Object>() {{
            put(FormatFilterExtension.TYPE_OPTION, "date");
            put(FormatFilterExtension.FORMAT, null);
        }}));
    }

    @Test
    public void testDateFormatNoDateObject() {
        assertNull(subject.call(renderContext, "yyyy-MM-dd", Collections.singletonMap(FormatFilterExtension.FORMAT, new Object())));
    }

    @Test(expected = SightlyException.class)
    public void testDateFormatFalseFormat() {
        assertDate(null, "yT", null, null);
    }


    @Test
    public void testDateFormatWithUTC() {
        assertDate("1918-12-01 00:00:00.000Z", "yyyy-MM-dd HH:mm:ss.SSSXXX", "UTC", null);
    }

    @Test
    public void testDateFormatWithZoneOffset() {
        assertDate("1918-12-01 02:00:00.000+02:00", "yyyy-MM-dd HH:mm:ss.SSSXXX", "GMT+02:00", null);
    }

    @Test
    public void testDateFormatWithZoneOffsetRFC822() {
        assertDate("1918-12-01 02:00:00.000+0200", "yyyy-MM-dd HH:mm:ss.SSSZ", "GMT+02:00", null);
    }

    @Test
    public void testDateFormatWithZoneName() {
        assertDate("1918-12-01 02:00:00.000(GMT+02:00)", "yyyy-MM-dd HH:mm:ss.SSS(z)", "GMT+02:00", null);
    }

    /**
     * When using jdk9 or newer, make sure to set the {@code java.locale.providers = COMPAT,SPI}
     * @see <a href="https://docs.oracle.com/javase/9/docs/api/java/util/spi/LocaleServiceProvider.html">LocaleServiceProvider</a>
     */
    @Test
    public void testDateFormatWithEscapedCharacters() {
        assertDate("01 December '18 12:00 AM; day in year: 335; week in year: 49",
            "dd MMMM ''yy hh:mm a; 'day in year': D; 'week in year': w",
            "UTC",
            null);
    }

    /**
     * When using jdk9 or newer, make sure to set the {@code java.locale.providers = COMPAT,SPI}
     * @see <a href="https://docs.oracle.com/javase/9/docs/api/java/util/spi/LocaleServiceProvider.html">LocaleServiceProvider</a>
     */
    @Test
    public void testDateFormatWithLocale() {
        assertDate("Sonntag, 1 Dez 1918", "EEEE, d MMM y", "UTC", "de");
    }

    /**
     * When using jdk9 or newer, make sure to set the {@code java.locale.providers = COMPAT,SPI}
     * @see <a href="https://docs.oracle.com/javase/9/docs/api/java/util/spi/LocaleServiceProvider.html">LocaleServiceProvider</a>
     */
    @Test
    public void testDateFormatWithFormatStyleShort() {
        assertDate("01/12/18", "short", "GMT+02:00", "fr");
    }

    @Test
    public void testDateFormatWithFormatStyleMedium() {
        assertDate("1 déc. 1918", "medium", "GMT+02:00", "fr");
    }

    @Test
    public void testDateFormatWithFormatStyleDefault() {
        assertDate("1 déc. 1918", "default", "GMT+02:00", "fr");
    }

    @Test
    public void testDateFormatWithFormatStyleLong() {
        assertDate("1 décembre 1918", "long", "GMT+02:00", "fr");
    }

    @Test
    public void testDateFormatWithFormatStyleFull() {
        assertDate("dimanche 1 décembre 1918", "full", "GMT+02:00", "fr");
    }

    private void assertDate(String expected, String format, String timezone, String locale) {
        Map<String, Object> options = new HashMap<>();
        options.put(FormatFilterExtension.FORMAT, testDate);
        if (timezone != null) {
            options.put(FormatFilterExtension.TIMEZONE_OPTION, timezone);
        }
        if (locale != null) {
            options.put(FormatFilterExtension.LOCALE_OPTION, locale);
        }
        assertEquals(expected, subject.call(renderContext, format, options));
    }
}