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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.scripting.SlingScriptHelper;
import org.apache.sling.commons.classloader.ClassLoaderWriter;
import org.apache.sling.commons.classloader.DynamicClassLoaderManager;
import org.apache.sling.commons.compiler.JavaCompiler;
import org.apache.sling.commons.compiler.Options;
import org.apache.sling.scripting.api.ScriptNameAware;
import org.apache.sling.scripting.api.resource.ScriptingResourceResolverProvider;
import org.apache.sling.scripting.sightly.SightlyException;
import org.apache.sling.scripting.sightly.compiler.CompilationResult;
import org.apache.sling.scripting.sightly.compiler.CompilationUnit;
import org.apache.sling.scripting.sightly.compiler.CompilerMessage;
import org.apache.sling.scripting.sightly.compiler.SightlyCompiler;
import org.apache.sling.scripting.sightly.impl.engine.ResourceBackedPojoChangeMonitor;
import org.apache.sling.scripting.sightly.impl.engine.SightlyCompiledScript;
import org.apache.sling.scripting.sightly.impl.engine.SightlyEngineConfiguration;
import org.apache.sling.scripting.sightly.impl.engine.SightlyScriptEngine;
import org.apache.sling.scripting.sightly.impl.utils.BindingsUtils;
import org.apache.sling.scripting.sightly.impl.utils.Patterns;
import org.apache.sling.scripting.sightly.impl.utils.ScriptUtils;
import org.apache.sling.scripting.sightly.java.compiler.GlobalShadowCheckBackendCompiler;
import org.apache.sling.scripting.sightly.java.compiler.JavaClassBackendCompiler;
import org.apache.sling.scripting.sightly.java.compiler.JavaEscapeUtils;
import org.apache.sling.scripting.sightly.render.RenderContext;
import org.apache.sling.scripting.sightly.render.RenderUnit;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
        service = SlingHTLMasterCompiler.class
)
public class SlingHTLMasterCompiler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SlingHTLMasterCompiler.class);

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private DynamicClassLoaderManager dynamicClassLoaderManager;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private ClassLoaderWriter classLoaderWriter;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private SightlyCompiler sightlyCompiler;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private JavaCompiler javaCompiler;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private ScriptingResourceResolverProvider scriptingResourceResolverProvider;

    @Reference
    private SightlyEngineConfiguration sightlyEngineConfiguration;

    @Reference
    private ResourceBackedPojoChangeMonitor resourceBackedPojoChangeMonitor;

    private static final String NO_SCRIPT = "NO_SCRIPT";
    private static final Pattern MANGLED_CHAR_PATTERN = Pattern.compile("(.*)(__[0-9a-f]{4}__)(.*)");
    private static final char[] ambiguousSymbols = new char[]{'-', '_', '.'};
    static final String SIGHTLY_CONFIG_FILE = "/sightly.config";

    private final Map<String, Lock> compilationLocks = new HashMap<>();
    private Options options;


    @Activate
    void activate() {
        LOGGER.info("Activating {}", getClass().getName());

        String version = System.getProperty("java.specification.version");
        options = new Options();
        options.put(Options.KEY_GENERATE_DEBUG_INFO, true);
        options.put(Options.KEY_SOURCE_VERSION, version);
        options.put(Options.KEY_TARGET_VERSION, version);
        options.put(Options.KEY_CLASS_LOADER_WRITER, classLoaderWriter);
        options.put(Options.KEY_FORCE_COMPILATION, true);


        InputStream is;
        boolean newVersion = true;
        String versionInfo = null;
        String newVersionString = sightlyEngineConfiguration.getEngineVersion();
        try {
            is = classLoaderWriter.getInputStream(SIGHTLY_CONFIG_FILE);
            if (is != null) {
                versionInfo = IOUtils.toString(is, "UTF-8");
                if (newVersionString.equals(versionInfo)) {
                    newVersion = false;
                } else {
                    LOGGER.info("Detected stale classes generated by Apache Sling Scripting HTL engine version {}.", versionInfo);
                }
                IOUtils.closeQuietly(is);
            }
        } catch (IOException e) {
            // do nothing; if we didn't find any previous version information we're considering our version to be new
        }
        if (newVersion) {
            OutputStream os = classLoaderWriter.getOutputStream(SIGHTLY_CONFIG_FILE);
            try {
                IOUtils.write(sightlyEngineConfiguration.getEngineVersion(), os, "UTF-8");
            } catch (IOException e) {
                // ignore
            } finally {
                IOUtils.closeQuietly(os);
            }
            String scratchFolder = sightlyEngineConfiguration.getScratchFolder();
            boolean scratchFolderDeleted = classLoaderWriter.delete(scratchFolder);
            if (scratchFolderDeleted) {
                if (StringUtils.isNotEmpty(versionInfo)) {
                    LOGGER.info("Deleted stale classes generated by Apache Sling Scripting HTL engine version {}.", versionInfo);
                }
            }
        }
        sightlyCompiler = SightlyCompiler.withKnownExpressionOptions(sightlyEngineConfiguration.getAllowedExpressionOptions());
    }

    /**
     * This method returns an Object instance based on a {@link Resource}-backed class that is either found through regular classloading
     * mechanisms or on-the-fly compilation. In case the requested class does not denote a fully qualified class name, this service will
     * try to find the class through Sling's resource resolution mechanism and compile the class on-the-fly if required.
     *
     * @param renderContext the render context
     * @param className     name of class to use for object instantiation
     * @return object instance of the requested class or {@code null} if the specified class is not backed by a {@link Resource}
     */
    public Object getResourceBackedUseObject(RenderContext renderContext, String className) {
        LOGGER.debug("Attempting to load class {}.", className);
        try {
            if (className.contains(".")) {
                Resource pojoResource = getPOJOFromFQCN(scriptingResourceResolverProvider.getRequestScopedResourceResolver(), className);
                if (pojoResource != null) {
                    return getUseObjectAndRecompileIfNeeded(pojoResource);
                }
            } else {
                Resource pojoResource = ScriptUtils.resolveScript(
                        scriptingResourceResolverProvider.getRequestScopedResourceResolver(),
                        renderContext,
                        className + ".java"
                );
                if (pojoResource != null) {
                    return getUseObjectAndRecompileIfNeeded(pojoResource);
                }
            }
        } catch (Exception e) {
            throw new SightlyException("Cannot obtain an instance for class " + className + ".", e);
        }
        return null;
    }

    public SightlyCompiledScript compileHTLScript(final SightlyScriptEngine engine,
                                                  final Reader script,
                                                  final ScriptContext scriptContext) throws ScriptException {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(dynamicClassLoaderManager.getDynamicClassLoader());
        try {
            String sName = NO_SCRIPT;
            if (script instanceof ScriptNameAware) {
                sName = ((ScriptNameAware) script).getScriptName();
            }
            if (sName.equals(NO_SCRIPT)) {
                sName = getScriptName(scriptContext);
            }
            final String scriptName = sName;
            CompilationUnit compilationUnit = new CompilationUnit() {
                @Override
                public String getScriptName() {
                    return scriptName;
                }

                @Override
                public Reader getScriptReader() {
                    return script;
                }
            };
            GlobalShadowCheckBackendCompiler shadowCheckBackendCompiler = null;
            SlingJavaImportsAnalyser importsAnalyser = new SlingJavaImportsAnalyser(scriptingResourceResolverProvider);
            JavaClassBackendCompiler javaClassBackendCompiler = new JavaClassBackendCompiler(importsAnalyser);
            if (scriptContext != null) {
                Bindings bindings = scriptContext.getBindings(ScriptContext.ENGINE_SCOPE);
                Set<String> globals = bindings.keySet();
                shadowCheckBackendCompiler =
                        new GlobalShadowCheckBackendCompiler(javaClassBackendCompiler, globals);
            }
            CompilationResult result =
                    shadowCheckBackendCompiler == null ? sightlyCompiler.compile(compilationUnit, javaClassBackendCompiler) :
                            sightlyCompiler.compile(compilationUnit, shadowCheckBackendCompiler);
            if (result.getWarnings().size() > 0) {
                for (CompilerMessage warning : result.getWarnings()) {
                    LOGGER.warn("Script {} {}:{}: {}", warning.getScriptName(), warning.getLine(), warning.getColumn(),
                            warning.getMessage());
                }
            }
            if (result.getErrors().size() > 0) {
                CompilerMessage error = result.getErrors().get(0);
                throw new ScriptException(error.getMessage(), error.getScriptName(), error.getLine(), error.getColumn());
            }
            SourceIdentifier sourceIdentifier = new SourceIdentifier(sightlyEngineConfiguration, scriptName);
            String javaSourceCode = javaClassBackendCompiler.build(sourceIdentifier);
            Object renderUnit = compileSource(sourceIdentifier, javaSourceCode);
            if (renderUnit instanceof RenderUnit) {
                return new SightlyCompiledScript(engine, (RenderUnit) renderUnit);
            } else {
                throw new SightlyException("Expected a RenderUnit.");
            }
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    public ClassLoader getClassLoader() {
        return classLoaderWriter.getClassLoader();
    }

    /**
     * Given a {@code fullyQualifiedClassName} and optionally a sub-package that should be stripped ({@code slashSubpackage}), this
     * method will try to locate a {@code Resource} in the repository that provides the source code for the Java class.
     *
     * @param resolver                a resource resolver with access to the script paths
     * @param fullyQualifiedClassName the FQCN
     * @return the {@code Resource} backing the class, or {@code null} if one cannot be found
     */
    Resource getPOJOFromFQCN(ResourceResolver resolver, String fullyQualifiedClassName) {
        StringBuilder pathElements = new StringBuilder("/");
        String[] classElements = StringUtils.split(fullyQualifiedClassName, '.');
        for (int i = 0; i < classElements.length; i++) {
            String classElem = classElements[i];
            Matcher matcher = MANGLED_CHAR_PATTERN.matcher(classElem);
            if (matcher.matches()) {
                String group = matcher.group(2);
                char unmangled = JavaEscapeUtils.unmangle(group);
                classElem = classElem.replaceAll(group, Character.toString(unmangled));
                while (matcher.find()) {
                    group = matcher.group(2);
                    unmangled = JavaEscapeUtils.unmangle(group);
                    classElem = classElem.replaceAll(group, Character.toString(unmangled));
                }
            } else {
                int underscoreIndex = classElem.indexOf('_');
                if (underscoreIndex > -1) {
                    if (underscoreIndex == classElem.length() - 1) {
                        classElem = classElem.substring(0, classElem.length() -1);
                    } else if (underscoreIndex == 0 && !Character.isJavaIdentifierStart(classElem.charAt(1))){
                        classElem = classElem.substring(1);
                    }
                }
            }
            pathElements.append(classElem);
            if (i < classElements.length - 1) {
                pathElements.append("/");
            }
        }
        Set<String> possiblePOJOPaths = getPossiblePojoPaths(pathElements.toString() + ".java");
        for (String possiblePath : possiblePOJOPaths) {
            Resource r = resolver.getResource(possiblePath);
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    /**
     * For a JCR path obtained from expanding a generated class name this method generates all the alternative path names that can be
     * obtained by expanding the mentioned class' name.
     *
     * @param originalPath one of the possible paths
     * @return a {@link Set} containing all the alternative paths if symbol replacement was needed; otherwise the set will contain just
     * the {@code originalPath}
     */
    private static Set<String> getPossiblePojoPaths(String originalPath) {
        Set<String> possiblePaths = new LinkedHashSet<>();
        possiblePaths.add(originalPath);
        Map<Integer, Character> chars = new HashMap<>();
        for (char symbol : ambiguousSymbols) {
            String pathCopy = originalPath.substring(0, originalPath.lastIndexOf("/"));
            int actualIndex = 0;
            boolean firstPass = true;
            while (pathCopy.indexOf(symbol) != -1) {
                int pos = pathCopy.indexOf(symbol);
                actualIndex += pos;
                if (!firstPass) {
                    actualIndex += 1;
                }
                chars.put(actualIndex, symbol);
                pathCopy = pathCopy.substring(pos + 1);
                firstPass = false;
            }
        }
        if (chars.size() > 0) {
            ArrayList<char[]> possibleArrangements = new ArrayList<>();
            populateArray(possibleArrangements, new char[chars.size()], 0);
            Integer[] indexes = chars.keySet().toArray(new Integer[0]);
            for (char[] arrangement : possibleArrangements) {
                char[] possiblePath = originalPath.toCharArray();
                for (int i = 0; i < arrangement.length; i++) {
                    char currentSymbol = arrangement[i];
                    int currentIndex = indexes[i];
                    possiblePath[currentIndex] = currentSymbol;
                }
                possiblePaths.add(new String(possiblePath));
            }
        }
        return possiblePaths;
    }

    /**
     * Given an initial array with its size equal to the number of elements of a needed arrangement, this method will generate all
     * the possible arrangements of values for this array in the provided {@code arrayCollection}. The values with which the array is
     * populated are the {@link #ambiguousSymbols} characters.
     *
     * @param arrayCollection the collection that will store the arrays
     * @param symbolsArrangementArray an initial array that will be used for collecting the results
     * @param index the initial index of the array that will be populated (needed for recursion purposes; start with 0 for the initial call)
     */
    private static void populateArray(ArrayList<char[]> arrayCollection, char[] symbolsArrangementArray, int
            index) {
        if (symbolsArrangementArray.length > 0) {
            if (index == symbolsArrangementArray.length) {
                arrayCollection.add(symbolsArrangementArray.clone());
            } else {
                for (char symbol : ambiguousSymbols) {
                    symbolsArrangementArray[index] = symbol;
                    populateArray(arrayCollection, symbolsArrangementArray, index + 1);
                }
            }
        }
    }

    /**
     * Compiles a class using the passed fully qualified class name and its source code.
     *
     * @param sourceIdentifier the source identifier
     * @param sourceCode       the source code from which to generate the class
     * @return object instance of the class to compile
     */
    private Object compileSource(SourceIdentifier sourceIdentifier, String sourceCode) {
        Lock lock;
        final String fqcn = sourceIdentifier.getFullyQualifiedClassName();
        synchronized (compilationLocks) {
            lock = compilationLocks.get(fqcn);
            if (lock == null) {
                lock = new ReentrantLock();
                compilationLocks.put(fqcn, lock);
            }
        }
        lock.lock();
        try {
            if (sightlyEngineConfiguration.keepGenerated()) {
                String path = "/" + fqcn.replaceAll("\\.", "/") + ".java";
                OutputStream os = classLoaderWriter.getOutputStream(path);
                IOUtils.write(sourceCode, os, "UTF-8");
                IOUtils.closeQuietly(os);
            }
            String[] sourceCodeLines = sourceCode.split("\\r\\n|[\\n\\x0B\\x0C\\r\\u0085\\u2028\\u2029]");
            boolean foundPackageDeclaration = false;
            for (String line : sourceCodeLines) {
                Matcher matcher = Patterns.JAVA_PACKAGE_DECLARATION.matcher(line);
                if (matcher.matches()) {
                    /*
                     * This matching might return false positives like:
                     * // package a.b.c;
                     *
                     * where from a syntactic point of view the source code doesn't have a package declaration and the expectancy is that our
                     * SightlyJavaCompilerService will add one.
                     */
                    foundPackageDeclaration = true;
                    break;
                }
            }

            if (!foundPackageDeclaration) {
                sourceCode = "package " + sourceIdentifier.getPackageName() + ";\n" + sourceCode;
            }

            org.apache.sling.commons.compiler.CompilationUnit
                    compilationUnit = new SightlyCompilationUnit(sourceCode, fqcn);
            long start = System.currentTimeMillis();
            org.apache.sling.commons.compiler.CompilationResult
                    compilationResult = javaCompiler.compile(new org.apache.sling.commons.compiler.CompilationUnit[]{compilationUnit}, options);
            long end = System.currentTimeMillis();
            List<org.apache.sling.commons.compiler.CompilerMessage> errors = compilationResult.getErrors();
            if (errors != null && errors.size() > 0) {
                throw new SightlyException(createErrorMsg(errors));
            }
            if (compilationResult.didCompile()) {
                LOGGER.debug("Class {} was compiled in {}ms.", fqcn, end - start);
            }
            /*
             * the class loader might have become dirty, so let the {@link ClassLoaderWriter} decide which class loader to return
             */
            return classLoaderWriter.getClassLoader().loadClass(fqcn).newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IOException e) {
            throw new SightlyException(e);
        } finally {
            lock.unlock();
        }
    }

    private Object getUseObjectAndRecompileIfNeeded(Resource pojoResource)
            throws IOException, InstantiationException, IllegalAccessException, ClassNotFoundException {
        SourceIdentifier sourceIdentifier = new SourceIdentifier(sightlyEngineConfiguration, pojoResource.getPath());
        long sourceLastModifiedDateFromCache =
                resourceBackedPojoChangeMonitor.getLastModifiedDateForJavaUseObject(pojoResource.getPath());
        long classLastModifiedDate = classLoaderWriter.getLastModified("/" + sourceIdentifier.getFullyQualifiedClassName()
                .replaceAll("\\.", "/") + ".class");
        if (sourceLastModifiedDateFromCache == 0) {
            // first access; let's check the real last modified date of the source
            long sourceLastModifiedDate = pojoResource.getResourceMetadata().getModificationTime();
            resourceBackedPojoChangeMonitor.recordLastModifiedTimestamp(pojoResource.getPath(), sourceLastModifiedDate);
            if (classLastModifiedDate < 0 || sourceLastModifiedDate > classLastModifiedDate) {
                return compileSource(sourceIdentifier, IOUtils.toString(pojoResource.adaptTo(InputStream.class), "UTF-8"));
            } else {
                return classLoaderWriter.getClassLoader().loadClass(sourceIdentifier.getFullyQualifiedClassName()).newInstance();
            }
        } else {
            if (sourceLastModifiedDateFromCache > classLastModifiedDate) {
                return compileSource(sourceIdentifier, IOUtils.toString(pojoResource.adaptTo(InputStream.class), "UTF-8"));
            } else {
                return classLoaderWriter.getClassLoader().loadClass(sourceIdentifier.getFullyQualifiedClassName()).newInstance();
            }
        }
    }

    //---------------------------------- private -----------------------------------
    private String createErrorMsg(List<org.apache.sling.commons.compiler.CompilerMessage> errors) {
        final StringBuilder buffer = new StringBuilder();
        buffer.append("Compilation errors in ");
        buffer.append(errors.get(0).getFile());
        buffer.append(":");
        StringBuilder errorsBuffer = new StringBuilder();
        boolean duplicateVariable = false;
        for (final org.apache.sling.commons.compiler.CompilerMessage e : errors) {
            if (!duplicateVariable) {
                if (e.getMessage().contains("Duplicate local variable")) {
                    duplicateVariable = true;
                    buffer.append(" Maybe you defined more than one identical block elements without defining a different variable for "
                            + "each one?");
                }
            }
            errorsBuffer.append("\nLine ");
            errorsBuffer.append(e.getLine());
            errorsBuffer.append(", column ");
            errorsBuffer.append(e.getColumn());
            errorsBuffer.append(" : ");
            errorsBuffer.append(e.getMessage());
        }
        buffer.append(errorsBuffer);
        return buffer.toString();
    }

    private String getScriptName(ScriptContext scriptContext) {
        if (scriptContext != null) {
            Bindings bindings = scriptContext.getBindings(ScriptContext.ENGINE_SCOPE);
            String scriptName = (String) bindings.get(ScriptEngine.FILENAME);
            if (scriptName != null && !"".equals(scriptName)) {
                return scriptName;
            }
            SlingScriptHelper sling = BindingsUtils.getHelper(bindings);
            if (sling != null) {
                return sling.getScript().getScriptResource().getPath();
            }
        }
        return NO_SCRIPT;
    }

}
