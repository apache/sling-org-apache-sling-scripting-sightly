/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 ~ Licensed to the Apache Software Foundation (ASF) under one
 ~ or more contributor license agreements.  See the NOTICE file
 ~ distributed with this work for additional information
 ~ regarding copyright ownership.  The ASF licenses this file
 ~ to you under the Apache License, Version 2.0 (the
 ~ "License"); you may not use this file except in compliance
 ~ with the License.  You may obtain a copy of the License at
 ~
 ~   http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing,
 ~ software distributed under the License is distributed on an
 ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 ~ KIND, either express or implied.  See the License for the
 ~ specific language governing permissions and limitations
 ~ under the License.
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
package org.apache.sling.scripting.sightly.impl.engine.compiled;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.classloader.ClassLoaderWriter;
import org.apache.sling.commons.compiler.CompilationResult;
import org.apache.sling.commons.compiler.CompilationUnit;
import org.apache.sling.commons.compiler.CompilerMessage;
import org.apache.sling.commons.compiler.JavaCompiler;
import org.apache.sling.commons.compiler.Options;
import org.apache.sling.scripting.api.resource.ScriptingResourceResolverProvider;
import org.apache.sling.scripting.sightly.impl.compiler.MockPojo;
import org.apache.sling.scripting.sightly.impl.engine.ResourceBackedPojoChangeMonitor;
import org.apache.sling.scripting.sightly.impl.engine.SightlyEngineConfiguration;
import org.apache.sling.scripting.sightly.impl.engine.runtime.RenderContextImpl;
import org.apache.sling.scripting.sightly.render.RenderContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.reflect.Whitebox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SlingHTLMasterCompilerTest {

    private SightlyEngineConfiguration sightlyEngineConfiguration;
    private ScriptingResourceResolverProvider scriptingResourceResolverProvider;
    private SlingHTLMasterCompiler compiler;
    private JavaCompiler javaCompiler;
    private ResourceBackedPojoChangeMonitor resourceBackedPojoChangeMonitor;
    private ClassLoaderWriter classLoaderWriter;

    @Before
    public void setUp() {
        sightlyEngineConfiguration = mock(SightlyEngineConfiguration.class);
        when(sightlyEngineConfiguration.getBundleSymbolicName()).thenReturn("org.apache.sling.scripting.sightly");
        when(sightlyEngineConfiguration.getEngineVersion()).thenReturn("1.0.17-SNAPSHOT");
        when(sightlyEngineConfiguration.getScratchFolder()).thenReturn("/org/apache/sling/scripting/sightly");

        ResourceResolver scriptingResourceResolver = mock(ResourceResolver.class);
        scriptingResourceResolverProvider = mock(ScriptingResourceResolverProvider.class);
        when(scriptingResourceResolverProvider.getRequestScopedResourceResolver()).thenReturn(scriptingResourceResolver);
        when(scriptingResourceResolver.getSearchPath()).thenReturn(new String[] {"/apps", "/libs"});

        compiler = spy(new SlingHTLMasterCompiler());
        javaCompiler = mock(JavaCompiler.class);

        resourceBackedPojoChangeMonitor = spy(new ResourceBackedPojoChangeMonitor());

        Whitebox.setInternalState(compiler, "sightlyEngineConfiguration", sightlyEngineConfiguration);
        Whitebox.setInternalState(compiler, "resourceBackedPojoChangeMonitor", resourceBackedPojoChangeMonitor);
        Whitebox.setInternalState(compiler, "javaCompiler", javaCompiler);
        classLoaderWriter = Mockito.mock(ClassLoaderWriter.class);
        ClassLoader classLoader = Mockito.mock(ClassLoader.class);
        when(classLoaderWriter.getClassLoader()).thenReturn(classLoader);
    }

    @After
    public void tearDown() {
        sightlyEngineConfiguration = null;
        compiler = null;
        resourceBackedPojoChangeMonitor = null;
        classLoaderWriter = null;
    }

    @Test
    public void testActivateNoPreviousInfo() {
        SlingHTLMasterCompiler slingHTLMasterCompiler = new SlingHTLMasterCompiler();
        ClassLoaderWriter classLoaderWriter = mock(ClassLoaderWriter.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(classLoaderWriter.getOutputStream(SlingHTLMasterCompiler.SIGHTLY_CONFIG_FILE)).thenReturn(outputStream);
        Whitebox.setInternalState(slingHTLMasterCompiler, "classLoaderWriter", classLoaderWriter);
        Whitebox.setInternalState(slingHTLMasterCompiler, "sightlyEngineConfiguration", sightlyEngineConfiguration);
        Whitebox.setInternalState(slingHTLMasterCompiler, "scriptingResourceResolverProvider", scriptingResourceResolverProvider);
        slingHTLMasterCompiler.activate();
        verify(classLoaderWriter).delete(sightlyEngineConfiguration.getScratchFolder());
        assertEquals("1.0.17-SNAPSHOT", outputStream.toString());
    }

    @Test
    public void testActivateOverPreviousVersion()  {
        SlingHTLMasterCompiler slingHTLMasterCompiler = new SlingHTLMasterCompiler();
        ClassLoaderWriter classLoaderWriter = mock(ClassLoaderWriter.class);
        try {
            when(classLoaderWriter.getInputStream(SlingHTLMasterCompiler.SIGHTLY_CONFIG_FILE))
                    .thenReturn(IOUtils.toInputStream("1.0.16", "UTF-8"));
        } catch (IOException e) {
            fail("IOException while setting tests.");
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(classLoaderWriter.getOutputStream(SlingHTLMasterCompiler.SIGHTLY_CONFIG_FILE)).thenReturn(outputStream);
        when(classLoaderWriter.delete(sightlyEngineConfiguration.getScratchFolder())).thenReturn(true);
        Whitebox.setInternalState(slingHTLMasterCompiler, "classLoaderWriter", classLoaderWriter);
        Whitebox.setInternalState(slingHTLMasterCompiler, "sightlyEngineConfiguration", sightlyEngineConfiguration);
        Whitebox.setInternalState(slingHTLMasterCompiler, "scriptingResourceResolverProvider", scriptingResourceResolverProvider);
        slingHTLMasterCompiler.activate();
        verify(classLoaderWriter).delete(sightlyEngineConfiguration.getScratchFolder());
        assertEquals("1.0.17-SNAPSHOT", outputStream.toString());
    }

    @Test
    public void testActivateOverSameVersion() {
        SlingHTLMasterCompiler slingHTLMasterCompiler = new SlingHTLMasterCompiler();
        ClassLoaderWriter classLoaderWriter = mock(ClassLoaderWriter.class);
        try {
            when(classLoaderWriter.getInputStream(SlingHTLMasterCompiler.SIGHTLY_CONFIG_FILE))
                    .thenReturn(IOUtils.toInputStream("1.0.17-SNAPSHOT", "UTF-8"));
        } catch (IOException e) {
            fail("IOException while setting tests.");
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteArrayOutputStream spyOutputStream = spy(outputStream);
        when(classLoaderWriter.getOutputStream(SlingHTLMasterCompiler.SIGHTLY_CONFIG_FILE)).thenReturn(spyOutputStream);
        Whitebox.setInternalState(slingHTLMasterCompiler, "classLoaderWriter", classLoaderWriter);
        Whitebox.setInternalState(slingHTLMasterCompiler, "sightlyEngineConfiguration", sightlyEngineConfiguration);
        Whitebox.setInternalState(slingHTLMasterCompiler, "scriptingResourceResolverProvider", scriptingResourceResolverProvider);
        slingHTLMasterCompiler.activate();
        verify(classLoaderWriter, never()).delete(sightlyEngineConfiguration.getScratchFolder());
        try {
            verify(spyOutputStream, never()).write(any(byte[].class));
            verify(spyOutputStream, never()).close();
        } catch (IOException e) {
            fail("IOException in test verification.");
        }
    }

    @Test
    public void testDiskCachedUseObject() throws Exception {
        String pojoPath = "/apps/myproject/testcomponents/a/Pojo.java";
        String className = "apps.myproject.testcomponents.a.Pojo";
        scriptingResourceResolverProvider = Mockito.mock(ScriptingResourceResolverProvider.class);
        ResourceResolver resolver = Mockito.mock(ResourceResolver.class);
        when(scriptingResourceResolverProvider.getRequestScopedResourceResolver()).thenReturn(resolver);
        Resource pojoResource = Mockito.mock(Resource.class);
        when(pojoResource.getPath()).thenReturn(pojoPath);
        ResourceMetadata mockMetadata = Mockito.mock(ResourceMetadata.class);
        when(mockMetadata.getModificationTime()).thenReturn(1L);
        when(pojoResource.getResourceMetadata()).thenReturn(mockMetadata);
        when(pojoResource.adaptTo(InputStream.class)).thenReturn(IOUtils.toInputStream("DUMMY", "UTF-8"));
        when(resolver.getResource(pojoPath)).thenReturn(pojoResource);
        when(classLoaderWriter.getLastModified("/apps/myproject/testcomponents/a/Pojo.class")).thenReturn(2L);
        getInstancePojoTest(className);
        /*
         * assuming the compiled class has a last modified date greater than the source, then the compiler should not recompile the Use
         * object
         */
        verify(javaCompiler, never()).compile(any(CompilationUnit[].class), any(Options.class));
    }

    @Test
    public void testObsoleteDiskCachedUseObject() throws Exception {
        String pojoPath = "/apps/myproject/testcomponents/a/Pojo.java";
        String className = "apps.myproject.testcomponents.a.Pojo";
        scriptingResourceResolverProvider = Mockito.mock(ScriptingResourceResolverProvider.class);
        ResourceResolver resolver = Mockito.mock(ResourceResolver.class);
        when(scriptingResourceResolverProvider.getRequestScopedResourceResolver()).thenReturn(resolver);
        Resource pojoResource = Mockito.mock(Resource.class);
        when(pojoResource.getPath()).thenReturn(pojoPath);
        ResourceMetadata mockMetadata = Mockito.mock(ResourceMetadata.class);
        when(mockMetadata.getModificationTime()).thenReturn(2L);
        when(pojoResource.getResourceMetadata()).thenReturn(mockMetadata);
        when(pojoResource.adaptTo(InputStream.class)).thenReturn(IOUtils.toInputStream("DUMMY", "UTF-8"));
        when(resolver.getResource(pojoPath)).thenReturn(pojoResource);
        when(classLoaderWriter.getLastModified("/apps/myproject/testcomponents/a/Pojo.class")).thenReturn(1L);
        getInstancePojoTest(className);
        /*
         * assuming the compiled class has a last modified date greater than the source, then the compiler should not recompile the Use
         * object
         */
        verify(compiler).getResourceBackedUseObject(any(RenderContext.class), anyString());
    }

    @Test
    public void testMemoryCachedUseObject() throws Exception {
        String pojoPath = "/apps/myproject/testcomponents/a/Pojo.java";
        String className = "apps.myproject.testcomponents.a.Pojo";
        scriptingResourceResolverProvider = Mockito.mock(ScriptingResourceResolverProvider.class);
        ResourceResolver resolver = Mockito.mock(ResourceResolver.class);
        when(scriptingResourceResolverProvider.getRequestScopedResourceResolver()).thenReturn(resolver);
        Resource pojoResource = Mockito.mock(Resource.class);
        when(pojoResource.getPath()).thenReturn(pojoPath);
        when(resourceBackedPojoChangeMonitor.getLastModifiedDateForJavaUseObject(pojoPath)).thenReturn(1L);
        when(pojoResource.adaptTo(InputStream.class)).thenReturn(IOUtils.toInputStream("DUMMY", "UTF-8"));
        when(resolver.getResource(pojoPath)).thenReturn(pojoResource);
        when(classLoaderWriter.getLastModified("/apps/myproject/testcomponents/a/Pojo.class")).thenReturn(2L);
        getInstancePojoTest(className);
        /*
         * assuming the compiled class has a last modified date greater than the source, then the compiler should not recompile the Use
         * object
         */
        verify(javaCompiler, never()).compile(any(CompilationUnit[].class), any(Options.class));
    }

    @Test
    public void testObsoleteMemoryCachedUseObject() throws Exception {
        String pojoPath = "/apps/myproject/testcomponents/a/Pojo.java";
        String className = "apps.myproject.testcomponents.a.Pojo";
        scriptingResourceResolverProvider = Mockito.mock(ScriptingResourceResolverProvider.class);
        ResourceResolver resolver = Mockito.mock(ResourceResolver.class);
        when(scriptingResourceResolverProvider.getRequestScopedResourceResolver()).thenReturn(resolver);
        Resource pojoResource = Mockito.mock(Resource.class);
        when(pojoResource.getPath()).thenReturn(pojoPath);
        when(resourceBackedPojoChangeMonitor.getLastModifiedDateForJavaUseObject(pojoPath)).thenReturn(2L);
        when(pojoResource.adaptTo(InputStream.class)).thenReturn(IOUtils.toInputStream("DUMMY", "UTF-8"));
        when(resolver.getResource(pojoPath)).thenReturn(pojoResource);
        when(classLoaderWriter.getLastModified("/apps/myproject/testcomponents/a/Pojo.class")).thenReturn(1L);
        getInstancePojoTest(className);
        /*
         * assuming the compiled class has a last modified date greater than the source, then the compiler should not recompile the Use
         * object
         */
        verify(javaCompiler, times(1)).compile(any(CompilationUnit[].class), any(Options.class));
    }

    @Test
    public void testGetPOJOFromFQCN() {
        Map<String, String> expectedScriptNames = new HashMap<String, String>() {{
            put("/apps/a_b_c/d_e_f/Pojo.java", "apps.a_b_c.d_e_f.Pojo");
            put("/apps/a-b-c/d.e.f/Pojo.java", "apps.a_b_c.d_e_f.Pojo");
            put("/apps/a-b-c/d-e.f/Pojo.java", "apps.a_b_c.d_e_f.Pojo");
            put("/apps/a-b-c/d.e_f/Pojo.java", "apps.a_b_c.d_e_f.Pojo");
            put("/apps/a-b-c/d-e-f/Pojo.java", "apps.a_b_c.d_e_f.Pojo");
            put("/apps/a/b/c/Pojo.java", "apps.a.b.c.Pojo");
        }};
        for (Map.Entry<String, String> scriptEntry : expectedScriptNames.entrySet()) {
            ResourceResolver resolver = Mockito.mock(ResourceResolver.class);
            Resource resource = Mockito.mock(Resource.class);
            when(resource.getPath()).thenReturn(scriptEntry.getKey());
            when(resolver.getResource(scriptEntry.getKey())).thenReturn(resource);
            Resource result = compiler.getPOJOFromFQCN(resolver, scriptEntry.getValue());
            assertNotNull(
                    String.format("ResourceResolver was expected to find resource %s for POJO %s. Got null instead.", scriptEntry.getKey(),
                            scriptEntry.getValue()), result);
            assertEquals(scriptEntry.getKey(), result.getPath());
        }
    }

    private void getInstancePojoTest(String className) throws Exception {
        RenderContextImpl renderContext = Mockito.mock(RenderContextImpl.class);
        CompilationResult compilationResult = Mockito.mock(CompilationResult.class);
        when(compilationResult.getErrors()).thenReturn(new ArrayList<CompilerMessage>());
        when(javaCompiler.compile(Mockito.any(CompilationUnit[].class), Mockito.any(Options.class))).thenReturn(compilationResult);
        when(classLoaderWriter.getClassLoader().loadClass(className)).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) {
                return MockPojo.class;
            }
        });
        Whitebox.setInternalState(compiler, "classLoaderWriter", classLoaderWriter);
        Whitebox.setInternalState(compiler, "javaCompiler", javaCompiler);
        Whitebox.setInternalState(compiler, "scriptingResourceResolverProvider", scriptingResourceResolverProvider);
        Object obj = compiler.getResourceBackedUseObject(renderContext, className);
        assertTrue("Expected to obtain a " + MockPojo.class.getName() + " object.", obj instanceof MockPojo);
    }
}
