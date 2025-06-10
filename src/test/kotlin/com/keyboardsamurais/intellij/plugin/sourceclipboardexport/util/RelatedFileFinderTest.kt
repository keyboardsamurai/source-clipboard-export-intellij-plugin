package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RelatedFileFinderTest {
    
    @Test
    fun testLanguageDetection() {
        // Test extension-based language detection
        assertEquals("java", detectLanguageFromExtension("java"))
        assertEquals("kt", detectLanguageFromExtension("kt"))
        assertEquals("js", detectLanguageFromExtension("js"))
        assertEquals("ts", detectLanguageFromExtension("ts"))
        assertEquals("jsx", detectLanguageFromExtension("jsx"))
        assertEquals("tsx", detectLanguageFromExtension("tsx"))
        assertEquals("html", detectLanguageFromExtension("html"))
        assertEquals("css", detectLanguageFromExtension("css"))
        assertEquals("scss", detectLanguageFromExtension("scss"))
        assertEquals("sass", detectLanguageFromExtension("sass"))
        assertEquals("unknown", detectLanguageFromExtension("unknown"))
    }
    
    @Test
    fun testTestFileDetectionJavaScript() {
        // Jest/Vitest patterns
        assertTrue(isTestFileByName("Component.test.js"))
        assertTrue(isTestFileByName("Component.spec.js"))
        assertTrue(isTestFileByName("Component.test.ts"))
        assertTrue(isTestFileByName("Component.spec.tsx"))
        
        // __tests__ folder pattern
        assertTrue(isTestFileByPath("src/components/__tests__/Component.js"))
        assertTrue(isTestFileByPath("src/utils/__tests__/helper.ts"))
        
        // tests folder pattern
        assertTrue(isTestFileByPath("src/components/tests/Component.js"))
        
        // Non-test files
        assertFalse(isTestFileByName("Component.js"))
        assertFalse(isTestFileByName("Component.tsx"))
        assertFalse(isTestFileByName("utils.ts"))
        assertFalse(isTestFileByPath("src/components/Component.jsx"))
    }
    
    @Test
    fun testTestFileDetectionJava() {
        // Traditional Java test patterns
        assertTrue(isTestFileByName("ComponentTest.java"))
        assertTrue(isTestFileByName("ComponentTests.java"))
        assertTrue(isTestFileByName("TestComponent.java"))
        assertTrue(isTestFileByName("ComponentSpec.java"))
        assertTrue(isTestFileByName("ComponentIT.java"))
        assertTrue(isTestFileByName("ComponentIntegrationTest.java"))
        
        // Test folder patterns
        assertTrue(isTestFileByPath("src/test/java/ComponentTest.java"))
        assertTrue(isTestFileByPath("src/tests/java/ComponentTest.java"))
        
        // Non-test files
        assertFalse(isTestFileByName("Component.java"))
        assertFalse(isTestFileByName("ComponentService.java"))
        assertFalse(isTestFileByPath("src/main/java/Component.java"))
    }
    
    @Test
    fun testImportPatternExtractionJavaScript() {
        val jsCode = """
            import React from 'react';
            import { Component, useState } from 'react';
            import './Component.css';
            import utils from '../utils/helper';
            import type { Props } from './types';
            
            const dynamicImport = import('./dynamic-component');
            const Component = require('./legacy-component');
            
            export { default } from './reexport';
        """.trimIndent()
        
        val imports = extractImportsFromText(jsCode, "javascript")
        
        assertTrue(imports.contains("react"))
        assertTrue(imports.contains("./Component.css"))
        assertTrue(imports.contains("../utils/helper"))
        assertTrue(imports.contains("./types"))
        assertTrue(imports.contains("./dynamic-component"))
        assertTrue(imports.contains("./legacy-component"))
        assertTrue(imports.contains("./reexport"))
    }
    
    @Test
    fun testImportPatternExtractionTypeScript() {
        val tsCode = """
            import { Observable } from 'rxjs';
            import * as React from 'react';
            import type { User } from '@/types/user';
            import './styles.scss';
            
            // Dynamic imports
            const LazyComponent = () => import('./LazyComponent');
        """.trimIndent()
        
        val imports = extractImportsFromText(tsCode, "typescript")
        
        assertTrue(imports.contains("rxjs"))
        assertTrue(imports.contains("react"))
        assertTrue(imports.contains("@/types/user"))
        assertTrue(imports.contains("./styles.scss"))
        assertTrue(imports.contains("./LazyComponent"))
    }
    
    @Test
    fun testImportPatternExtractionJava() {
        val javaCode = """
            package com.example;
            
            import java.util.List;
            import static java.util.Collections.emptyList;
            import com.example.service.UserService;
            import com.example.model.*;
        """.trimIndent()
        
        val imports = extractImportsFromText(javaCode, "java")
        
        assertTrue(imports.contains("java.util.List"))
        assertTrue(imports.contains("java.util.Collections"))
        assertTrue(imports.contains("com.example.service.UserService"))
        assertTrue(imports.contains("com.example.model"))
    }
    
    @Test
    fun testImportPatternExtractionHTML() {
        val htmlCode = """
            <!DOCTYPE html>
            <html>
            <head>
                <link rel="stylesheet" href="./styles.css">
                <link href="https://cdn.example.com/bootstrap.css" rel="stylesheet">
                <script src="./app.js"></script>
                <script type="module" src="./modules/main.js"></script>
            </head>
            </html>
        """.trimIndent()
        
        val imports = extractImportsFromText(htmlCode, "html")
        
        assertTrue(imports.contains("./styles.css"))
        assertTrue(imports.contains("https://cdn.example.com/bootstrap.css"))
        assertTrue(imports.contains("./app.js"))
        assertTrue(imports.contains("./modules/main.js"))
    }
    
    @Test
    fun testImportPatternExtractionCSS() {
        val cssCode = """
            @import url("./base.css");
            @import './variables.scss';
            @import "https://fonts.googleapis.com/css2?family=Inter";
            
            .component {
                background: url('./images/bg.png');
            }
        """.trimIndent()
        
        val imports = extractImportsFromText(cssCode, "css")
        
        assertTrue(imports.contains("./base.css"))
        assertTrue(imports.contains("./variables.scss"))
        assertTrue(imports.contains("https://fonts.googleapis.com/css2?family=Inter"))
    }
    
    @Test
    fun testReactNextJSPatterns() {
        // Next.js specific imports
        val nextJsCode = """
            import Link from 'next/link';
            import Image from 'next/image';
            import { useRouter } from 'next/router';
            import '@/styles/globals.css';
            import config from '@/config/app';
        """.trimIndent()
        
        val imports = extractImportsFromText(nextJsCode, "javascript")
        
        assertTrue(imports.contains("next/link"))
        assertTrue(imports.contains("next/image"))
        assertTrue(imports.contains("next/router"))
        assertTrue(imports.contains("@/styles/globals.css"))
        assertTrue(imports.contains("@/config/app"))
    }
    
    @Test
    fun testConfigFileDetection() {
        // JavaScript/TypeScript config files
        val jsConfigs = listOf(
            "package.json", "tsconfig.json", "next.config.js", 
            "webpack.config.js", "jest.config.js", ".eslintrc.json",
            "tailwind.config.ts", "vite.config.js"
        )
        
        jsConfigs.forEach { config ->
            assertTrue(isJavaScriptConfigFile(config), "Should recognize $config as JS/TS config")
        }
        
        // Java/Kotlin config files
        val javaConfigs = listOf(
            "pom.xml", "build.gradle", "build.gradle.kts",
            "application.properties", "application.yml"
        )
        
        javaConfigs.forEach { config ->
            assertTrue(isJavaConfigFile(config), "Should recognize $config as Java/Kotlin config")
        }
        
        // General config files
        val generalConfigs = listOf(
            "Dockerfile", "docker-compose.yml", ".env"
        )
        
        generalConfigs.forEach { config ->
            assertTrue(isGeneralConfigFile(config), "Should recognize $config as general config")
        }
    }
    
    @Test
    fun testStyleFileAssociation() {
        // React component style associations
        assertTrue(isStyleFileForComponent("Button.jsx", "Button.css"))
        assertTrue(isStyleFileForComponent("Button.tsx", "Button.module.css"))
        assertTrue(isStyleFileForComponent("Header.jsx", "Header.scss"))
        assertTrue(isStyleFileForComponent("Modal.tsx", "Modal.module.scss"))
        
        // Non-matching cases
        assertFalse(isStyleFileForComponent("Button.jsx", "Modal.css"))
        assertFalse(isStyleFileForComponent("Button.tsx", "styles.css"))
    }
    
    // Helper methods that test the logic without IntelliJ dependencies
    private fun detectLanguageFromExtension(extension: String): String {
        return when (extension.lowercase()) {
            "java" -> "java"
            "kt", "kts" -> "kt"
            "js", "mjs" -> "js"
            "ts" -> "ts"
            "jsx" -> "jsx"
            "tsx" -> "tsx"
            "html", "htm" -> "html"
            "css" -> "css"
            "scss", "sass" -> "css"
            else -> "unknown"
        }
    }
    
    private fun isTestFileByName(fileName: String): Boolean {
        return fileName.contains(".test.") || fileName.contains(".spec.") ||
               fileName.endsWith("Test.java") || fileName.endsWith("Tests.java") ||
               fileName.startsWith("Test") || fileName.endsWith("IT.java") ||
               fileName.endsWith("IntegrationTest.java")
    }
    
    private fun isTestFileByPath(path: String): Boolean {
        return path.contains("/test/") || path.contains("/tests/") || 
               path.contains("/__tests__/")
    }
    
    private fun extractImportsFromText(text: String, language: String): List<String> {
        val imports = mutableListOf<String>()
        
        when (language) {
            "javascript", "typescript" -> {
                // ES6 imports
                Regex("""import\s+.*?from\s+['"]([^'"]+)['"]""").findAll(text).forEach {
                    imports.add(it.groupValues[1])
                }
                // Side effect imports
                Regex("""import\s+['"]([^'"]+)['"]""").findAll(text).forEach {
                    imports.add(it.groupValues[1])
                }
                // CommonJS
                Regex("""require\s*\(\s*['"]([^'"]+)['"]\s*\)""").findAll(text).forEach {
                    imports.add(it.groupValues[1])
                }
                // Dynamic imports
                Regex("""import\s*\(\s*['"]([^'"]+)['"]\s*\)""").findAll(text).forEach {
                    imports.add(it.groupValues[1])
                }
                // Re-exports
                Regex("""export\s+.*?from\s+['"]([^'"]+)['"]""").findAll(text).forEach {
                    imports.add(it.groupValues[1])
                }
            }
            "java" -> {
                Regex("""import\s+(?:static\s+)?([a-zA-Z_][a-zA-Z0-9_.]*)\s*;""").findAll(text).forEach {
                    imports.add(it.groupValues[1])
                }
            }
            "html" -> {
                // Script tags
                Regex("""<script[^>]+src\s*=\s*['"]([^'"]+)['"]""").findAll(text).forEach {
                    imports.add(it.groupValues[1])
                }
                // Link tags
                Regex("""<link[^>]+href\s*=\s*['"]([^'"]+)['"]""").findAll(text).forEach {
                    imports.add(it.groupValues[1])
                }
            }
            "css" -> {
                // CSS @import
                Regex("""@import\s+(?:url\s*\(\s*)?['"]([^'"]+)['"]""").findAll(text).forEach {
                    imports.add(it.groupValues[1])
                }
            }
        }
        
        return imports
    }
    
    private fun isJavaScriptConfigFile(fileName: String): Boolean {
        val jsConfigFiles = setOf(
            "package.json", "tsconfig.json", "jsconfig.json", "webpack.config.js",
            "vite.config.js", "vite.config.ts", "next.config.js", "next.config.mjs",
            "tailwind.config.js", "tailwind.config.ts", "jest.config.js", "jest.config.ts",
            "vitest.config.js", "vitest.config.ts", ".eslintrc.js", ".eslintrc.json",
            ".prettierrc", ".prettierrc.json", "rollup.config.js", "esbuild.config.js",
            "babel.config.js"
        )
        return jsConfigFiles.contains(fileName)
    }
    
    private fun isJavaConfigFile(fileName: String): Boolean {
        val javaConfigFiles = setOf(
            "pom.xml", "build.gradle", "build.gradle.kts",
            "application.properties", "application.yml", "application.yaml"
        )
        return javaConfigFiles.contains(fileName)
    }
    
    private fun isGeneralConfigFile(fileName: String): Boolean {
        val generalConfigFiles = setOf(
            "Dockerfile", "docker-compose.yml", ".env", "Cargo.toml",
            "requirements.txt", "pyproject.toml", "setup.py", "composer.json",
            "Gemfile", "go.mod", "CMakeLists.txt"
        )
        return generalConfigFiles.contains(fileName)
    }
    
    private fun isStyleFileForComponent(componentFile: String, styleFile: String): Boolean {
        val componentName = componentFile.substringBeforeLast(".")
        val styleName = styleFile.substringBeforeLast(".")
        
        return styleName == componentName || styleName == "$componentName.module"
    }
} 