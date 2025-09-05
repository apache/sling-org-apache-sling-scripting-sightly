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

import java.io.IOException;
import java.io.PrintWriter;

import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertSame;

/**
 * @deprecated use {@link PrintWriterJakartaResponseWrapperTest} instead
 */
@Deprecated(since = "2.0.0-1.4.0")
public class PrintWriterResponseWrapperTest {

    @Rule
    public SlingContext context = new SlingContext();

    /**
     * Test method for {@link org.apache.sling.scripting.sightly.impl.engine.extension.PrintWriterResponseWrapper#getWriter()}.
     */
    @Test
    public void testGetWriter() throws IOException {
        MockSlingHttpServletResponse response = context.response();
        PrintWriter pw = new PrintWriter(response.getOutputStream());
        PrintWriterResponseWrapper wrapper = new PrintWriterResponseWrapper(pw, response);
        assertSame(wrapper.getWriter(), pw);
    }
}
