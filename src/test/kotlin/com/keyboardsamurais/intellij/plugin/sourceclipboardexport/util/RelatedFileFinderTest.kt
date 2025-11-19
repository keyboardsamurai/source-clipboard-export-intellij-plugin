package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RelatedFileFinderTest {

    private val project = mockk<Project>(relaxed = true)

    @BeforeEach
    fun setup() {
        mockkStatic(ReadAction::class)
        every { ReadAction.compute(any<ThrowableComputable<*, *>>()) } answers
                {
                    @Suppress("UNCHECKED_CAST")
                    val computable = invocation.args[0] as ThrowableComputable<Any?, Exception>
                    computable.compute()
                }

        mockkStatic(ApplicationManager::class)
        mockkStatic(ProjectScope::class)
        val app = mockk<Application>(relaxed = true)
        every { ApplicationManager.getApplication() } returns app
        every { app.runReadAction(any<Computable<*>>()) } answers
                {
                    @Suppress("UNCHECKED_CAST")
                    val computable = invocation.args[0] as Computable<Any?>
                    computable.compute()
                }
        every { ProjectScope.getProjectScope(project) } returns mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `findTestFiles returns matches from filename index and test folders`() {
        val sourceFile = mockFile("Component.tsx", extension = "tsx")
        val testMatch = mockFile("Component.test.tsx")
        val folderTestFile = mockFile("Component.tsx")
        val testsFolder = mockFolder("__tests__", children = arrayOf(folderTestFile))
        val parent = mockFolder("components", children = arrayOf(sourceFile, testsFolder))
        every { sourceFile.parent } returns parent
        every { parent.findChild("__tests__") } returns testsFolder
        every { testsFolder.children } returns arrayOf(folderTestFile)

        mockkStatic(FilenameIndex::class)
        every {
            FilenameIndex.getVirtualFilesByName(any<String>(), any<GlobalSearchScope>())
        } answers
                {
                    val name = firstArg<String>()
                    if (name == "Component.test.js" ||
                                    name == "Component.test.ts" ||
                                    name == "Component.test.jsx" ||
                                    name == "Component.test.tsx"
                    ) {
                        setOf(testMatch)
                    } else emptySet()
                }

        val files = RelatedFileFinder.findTestFiles(project, sourceFile)

        assertTrue(files.contains(folderTestFile))
    }

    @Test
    fun `findCurrentPackageFiles includes index and style resources`() {
        val jsxFile = mockFile("Widget.tsx", extension = "tsx")
        val indexFile = mockFile("index.ts")
        val styleFile = mockFile("Widget.module.css")
        val siblingFile = mockFile("Widget.test.tsx")
        val parent =
                mockFolder(
                        "components",
                        children = arrayOf(jsxFile, siblingFile, indexFile, styleFile)
                )
        every { jsxFile.parent } returns parent
        every { parent.findChild("index.js") } returns null
        every { parent.findChild("index.ts") } returns indexFile
        every { parent.findChild("Widget.css") } returns null
        every { parent.findChild("Widget.scss") } returns null
        every { parent.findChild("Widget.sass") } returns null
        every { parent.findChild("Widget.module.css") } returns styleFile

        val result = RelatedFileFinder.findCurrentPackageFiles(jsxFile)

        assertTrue(result.contains(indexFile))
        assertTrue(result.contains(styleFile))
        assertTrue(result.contains(siblingFile))
    }

    @Test
    fun `findRecentChanges filters by timestamp`() {
        val recentFile = mockFile("Recent.kt")
        every { recentFile.timeStamp } returns System.currentTimeMillis()
        val oldFile = mockFile("Old.kt")
        every { oldFile.timeStamp } returns System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000

        mockkStatic(ProjectFileIndex::class)
        val index = mockk<ProjectFileIndex>()
        every { ProjectFileIndex.getInstance(project) } returns index
        every { index.iterateContent(any()) } answers
                {
                    val iterator = firstArg<ContentIterator>()
                    iterator.processFile(recentFile)
                    iterator.processFile(oldFile)
                    true
                }

        val files = RelatedFileFinder.findRecentChanges(project, hours = 24)

        assertEquals(listOf(recentFile), files)
    }

    @Test
    fun `findConfigFiles merges base and language configs`() {
        mockkStatic(ProjectRootManager::class)
        val projectRootManager = mockk<ProjectRootManager>()
        every { ProjectRootManager.getInstance(project) } returns projectRootManager

        val projectRoot = mockFolder("root")
        every { projectRootManager.contentRoots } returns arrayOf(projectRoot)

        val moduleDir = mockFolder("module")
        val sourceFile = mockFile("Component.tsx")
        every { sourceFile.parent } returns moduleDir

        val packageJson = mockFile("package.json")
        val tsconfig = mockFile("tsconfig.json")
        val eslint = mockFile(".eslintrc.json")
        val dockerfile = mockFile("Dockerfile")
        every { projectRoot.findChild("package.json") } returns packageJson
        every { projectRoot.findChild("tsconfig.json") } returns tsconfig
        every { moduleDir.findChild(".eslintrc.json") } returns eslint
        every { moduleDir.findChild("Dockerfile") } returns dockerfile

        val configs = RelatedFileFinder.findConfigFiles(project, sourceFile)

        assertTrue(configs.containsAll(listOf(packageJson, tsconfig, eslint, dockerfile)))
    }

    @Test
    fun `findDirectImports resolves relative and alias imports`() {
        mockkStatic(ProjectRootManager::class)
        mockkStatic(PsiManager::class)
        val projectRootManager = mockk<ProjectRootManager>()
        every { ProjectRootManager.getInstance(project) } returns projectRootManager
        val projectRoot = mockFolder("root")
        val srcRoot = mockFolder("src")
        every { projectRootManager.contentRoots } returns arrayOf(projectRoot)
        every { projectRoot.findChild("src") } returns srcRoot

        val sourceFile = mockFile("Card.tsx")
        val componentDir = mockFolder("components")
        every { sourceFile.parent } returns componentDir

        val buttonFile = mockFile("Button.tsx")
        val themeFile = mockFile("theme.ts")
        every { componentDir.findFileByRelativePath(any<String>()) } returns null
        every { componentDir.findFileByRelativePath("./Button.tsx") } returns buttonFile
        every { srcRoot.findFileByRelativePath(any<String>()) } returns null
        every { srcRoot.findFileByRelativePath("styles/theme.ts") } returns themeFile

        val psiManager = mockk<PsiManager>()
        every { PsiManager.getInstance(project) } returns psiManager
        val psiFile = mockk<PsiFile>()
        every { psiManager.findFile(sourceFile) } returns psiFile
        every { psiFile.virtualFile } returns sourceFile
        every { psiFile.text } returns
                """
            import Button from "./Button"
            import theme from "@/styles/theme"
        """.trimIndent()

        val imports = RelatedFileFinder.findDirectImports(project, sourceFile)

        assertEquals(setOf(buttonFile, themeFile), imports.toSet())
    }

    @Test
    fun `findTransitiveImports traverses nested dependencies`() {
        mockkStatic(ProjectRootManager::class)
        mockkStatic(PsiManager::class)
        val projectRootManager = mockk<ProjectRootManager>()
        every { ProjectRootManager.getInstance(project) } returns projectRootManager
        val projectRoot = mockFolder("root")
        every { projectRootManager.contentRoots } returns arrayOf(projectRoot)

        val psiManager = mockk<PsiManager>()
        every { PsiManager.getInstance(project) } returns psiManager

        val sourceFile = mockFile("Component.ts")
        val helperFile = mockFile("helper.ts")
        val utilFile = mockFile("util.ts")
        val componentDir = mockFolder("components")
        val helperDir = mockFolder("helpers")
        every { sourceFile.parent } returns componentDir
        every { helperFile.parent } returns helperDir

        every { componentDir.findFileByRelativePath(any<String>()) } returns null
        every { componentDir.findFileByRelativePath("./helper.ts") } returns helperFile
        every { helperDir.findFileByRelativePath(any<String>()) } returns null
        every { helperDir.findFileByRelativePath("./util.ts") } returns utilFile

        val sourcePsi = mockk<PsiFile>()
        val helperPsi = mockk<PsiFile>()
        val utilPsi = mockk<PsiFile>()
        every { psiManager.findFile(sourceFile) } returns sourcePsi
        every { psiManager.findFile(helperFile) } returns helperPsi
        every { psiManager.findFile(utilFile) } returns utilPsi

        every { sourcePsi.virtualFile } returns sourceFile
        every { helperPsi.virtualFile } returns helperFile
        every { utilPsi.virtualFile } returns utilFile

        every { sourcePsi.text } returns """import helper from "./helper""""
        every { helperPsi.text } returns """import util from "./util""""
        every { utilPsi.text } returns ""

        val imports = RelatedFileFinder.findTransitiveImports(project, sourceFile)

        assertEquals(setOf(helperFile, utilFile), imports.toSet())
    }

    @Test
    fun `resolveJavaScriptImport tries extensionless relative and absolute modules`() {
        val srcRoot = mockFolder("src")
        val projectRoot = mockFolder("root", children = arrayOf(srcRoot))
        val sourceFile = mockFile("Widget.tsx")
        val componentDir = mockFolder("components")
        every { sourceFile.parent } returns componentDir

        val buttonFile = mockFile("Button.tsx")
        every { componentDir.findFileByRelativePath("./Button") } returns null
        every { componentDir.findFileByRelativePath("./Button.tsx") } returns buttonFile

        val childIndex = mockFile("index.tsx")
        every { componentDir.findFileByRelativePath("./child/index.tsx") } returns childIndex

        val absoluteFile = mockFile("Toolbar.ts")
        every { srcRoot.findFileByRelativePath("components/Toolbar") } returns absoluteFile

        val resolvedRelative = invokeResolveJavaScriptImport(projectRoot, sourceFile, "./Button")
        val resolvedDirectory = invokeResolveJavaScriptImport(projectRoot, sourceFile, "./child")
        val resolvedAbsolute =
                invokeResolveJavaScriptImport(projectRoot, sourceFile, "components/Toolbar")

        assertEquals(buttonFile, resolvedRelative)
        assertEquals(childIndex, resolvedDirectory)
        assertEquals(absoluteFile, resolvedAbsolute)
    }

    @Test
    fun `resolveCssImport appends extensions when missing`() {
        val sourceFile = mockFile("styles.css")
        val stylesFolder = mockFolder("styles")
        every { sourceFile.parent } returns stylesFolder

        val scssFile = mockFile("colors.scss")
        every { stylesFolder.findFileByRelativePath("./colors") } returns null
        every { stylesFolder.findFileByRelativePath("./colors.scss") } returns scssFile

        val resolved = invokeResolveCssImport(sourceFile, "./colors")

        assertEquals(scssFile, resolved)
    }

    @Test
    fun `resolveJavaScriptImport handles Next alias`() {
        val projectRoot = mockFolder("root")
        val srcRoot = mockFolder("src")
        every { projectRoot.findChild("src") } returns srcRoot
        val sourceFile = mockFile("Page.tsx")
        val aliasTarget = mockFile("components/Header.tsx")
        every { srcRoot.findFileByRelativePath("components/Header.tsx") } returns aliasTarget

        val resolved =
                invokeResolveJavaScriptImport(projectRoot, sourceFile, "@/components/Header.tsx")

        assertEquals(aliasTarget, resolved)
    }

    @Test
    fun `resolveJavaKotlinImport removes underscores in packages`() {
        val projectRoot = mockFolder("root")
        val javaFile = mockFile("MyService.java")
        every {
            projectRoot.findFileByRelativePath("src/main/java/com/example/My_Service.java")
        } returns null
        every {
            projectRoot.findFileByRelativePath("src/main/java/com/example/MyService.java")
        } returns javaFile

        val resolved = invokeResolveJavaKotlinImport(projectRoot, "com.example.My_Service")

        assertEquals(javaFile, resolved)
    }

    @Test
    fun `resolvePythonImport tries module and package files`() {
        val projectRoot = mockFolder("root")
        val moduleFile = mockFile("helpers/utils.py")
        val packageInit = mockFile("services/api/__init__.py")
        every { projectRoot.findFileByRelativePath("helpers/utils.py") } returns moduleFile
        every { projectRoot.findFileByRelativePath("helpers.utils.py") } returns null
        every { projectRoot.findFileByRelativePath("helpers/utils/__init__.py") } returns null
        every { projectRoot.findFileByRelativePath("services/api.py") } returns null
        every { projectRoot.findFileByRelativePath("services/api/__init__.py") } returns packageInit

        val moduleResult = invokeResolvePythonImport(projectRoot, "helpers.utils")
        val packageResult = invokeResolvePythonImport(projectRoot, "services.api")

        assertEquals(moduleFile, moduleResult)
        assertEquals(packageInit, packageResult)
    }

    @Test
    fun `resolveHtmlReference handles absolute and relative paths`() {
        val fileSystem = mockk<VirtualFileSystem>(relaxed = true)
        val sourceFile = mockFile("index.html")
        every { sourceFile.fileSystem } returns fileSystem
        val parent = mockFolder("templates")
        every { sourceFile.parent } returns parent

        val absoluteFile = mockFile("/static/app.js")
        val relativeFile = mockFile("styles/main.css")
        every { fileSystem.findFileByPath("/static/app.js") } returns absoluteFile
        every { parent.findFileByRelativePath("styles/main.css") } returns relativeFile

        val absResult = invokeResolveHtmlReference(sourceFile, "/static/app.js")
        val relResult = invokeResolveHtmlReference(sourceFile, "styles/main.css")

        assertEquals(absoluteFile, absResult)
        assertEquals(relativeFile, relResult)
    }

    @Test
    fun `findTestFiles includes tests folder and specs`() {
        val sourceFile = mockFile("Widget.tsx", "tsx")
        val testsFolder = mockFolder("tests")
        val siblingSpec = mockFile("Widget.spec.tsx")
        val parent = mockFolder("components")
        every { sourceFile.parent } returns parent
        every { parent.findChild("__tests__") } returns null
        every { parent.findChild("tests") } returns testsFolder
        every { testsFolder.children } returns arrayOf(mockFile("Widget.tsx"))
        every { parent.children } returns arrayOf(sourceFile, siblingSpec)

        mockkStatic(FilenameIndex::class)
        every { FilenameIndex.getVirtualFilesByName(any<String>(), any()) } returns emptySet()

        val files = RelatedFileFinder.findTestFiles(project, sourceFile)

        assertTrue(files.any { it.name == "Widget.tsx" })
        assertTrue(files.contains(siblingSpec))
        unmockkStatic(FilenameIndex::class)
    }

    private fun mockFile(
            name: String,
            extension: String? = name.substringAfterLast('.', "")
    ): VirtualFile {
        val vf = mockk<VirtualFile>(relaxed = true)
        every { vf.name } returns name
        every { vf.extension } returns extension
        every { vf.nameWithoutExtension } returns name.substringBeforeLast('.', name)
        every { vf.isDirectory } returns false
        every { vf.path } returns "/repo/$name"
        every { vf.exists() } returns true
        return vf
    }

    private fun mockFolder(name: String, children: Array<VirtualFile> = emptyArray()): VirtualFile {
        val vf = mockk<VirtualFile>(relaxed = true)
        every { vf.name } returns name
        every { vf.isDirectory } returns true
        every { vf.children } returns children
        every { vf.path } returns "/repo/$name"
        every { vf.exists() } returns true
        every { vf.findChild(any<String>()) } answers
                {
                    val childName = invocation.args[0] as String
                    children.firstOrNull { it.name == childName }
                }
        children.forEach { child -> every { child.parent } returns vf }
        return vf
    }

    private fun invokeResolveJavaScriptImport(
            projectRoot: VirtualFile,
            source: VirtualFile,
            importPath: String
    ): VirtualFile? {
        val method =
                RelatedFileFinder::class.java.getDeclaredMethod(
                        "resolveJavaScriptImport",
                        VirtualFile::class.java,
                        VirtualFile::class.java,
                        String::class.java
                )
        method.isAccessible = true
        return method.invoke(RelatedFileFinder, projectRoot, source, importPath) as? VirtualFile
    }

    private fun invokeResolveCssImport(source: VirtualFile, importPath: String): VirtualFile? {
        val method =
                RelatedFileFinder::class.java.getDeclaredMethod(
                        "resolveCssImport",
                        VirtualFile::class.java,
                        String::class.java
                )
        method.isAccessible = true
        return method.invoke(RelatedFileFinder, source, importPath) as? VirtualFile
    }

    private fun invokeResolvePythonImport(
            projectRoot: VirtualFile,
            importPath: String
    ): VirtualFile? {
        val method =
                RelatedFileFinder::class.java.getDeclaredMethod(
                        "resolvePythonImport",
                        VirtualFile::class.java,
                        String::class.java
                )
        method.isAccessible = true
        return method.invoke(RelatedFileFinder, projectRoot, importPath) as? VirtualFile
    }

    private fun invokeResolveHtmlReference(
            source: VirtualFile,
            referencePath: String
    ): VirtualFile? {
        val method =
                RelatedFileFinder::class.java.getDeclaredMethod(
                        "resolveHtmlReference",
                        VirtualFile::class.java,
                        String::class.java
                )
        method.isAccessible = true
        return method.invoke(RelatedFileFinder, source, referencePath) as? VirtualFile
    }

    private fun invokeResolveJavaKotlinImport(
            projectRoot: VirtualFile,
            importPath: String
    ): VirtualFile? {
        val method =
                RelatedFileFinder::class.java.getDeclaredMethod(
                        "resolveJavaKotlinImport",
                        VirtualFile::class.java,
                        String::class.java
                )
        method.isAccessible = true
        return method.invoke(RelatedFileFinder, projectRoot, importPath) as? VirtualFile
    }
}
