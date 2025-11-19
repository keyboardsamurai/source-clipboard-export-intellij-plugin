package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InheritanceFinderTest {

    private val inheritableElementClass = Class.forName(
        "com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.InheritanceFinder\$InheritableElement"
    )
    @Suppress("UNCHECKED_CAST")
    private val elementTypeClass = Class.forName(
        "com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.InheritanceFinder\$ElementType"
    ) as Class<out Enum<*>>

    @Test
    fun `extractInheritableElements finds classes interfaces and react components`() {
        val method = InheritanceFinder::class.java.getDeclaredMethod(
            "extractInheritableElements",
            String::class.java
        )
        method.isAccessible = true

        val text = """
            export class BaseWidget {}
            export interface EventBus {}
            export const FancyView = () => <BaseWidget />;
            export abstract class AbstractThing {}
        """.trimIndent()

        val result = method.invoke(InheritanceFinder, text) as List<*>
        val names = result.map { element ->
            val nameField = inheritableElementClass.getDeclaredField("name").apply { isAccessible = true }
            nameField.get(element) as String
        }

        assertTrue(names.containsAll(listOf("BaseWidget", "EventBus", "FancyView", "AbstractThing")))
    }

    @Test
    fun `isImplementingElement detects multiple element types`() {
        val method = InheritanceFinder::class.java.getDeclaredMethod(
            "isImplementingElement",
            String::class.java,
            inheritableElementClass
        )
        method.isAccessible = true

        val classElement = newElement("BaseWidget", "CLASS")
        val interfaceElement = newElement("EventBus", "INTERFACE")
        val reactElement = newElement("FancyView", "REACT_COMPONENT")

        val extendsText = "class ChildWidget extends BaseWidget {}"
        val implementsText = "class EventBusImpl implements EventBus, Another"
        val reactHocText = "const Connected = memo(FancyView)"
        val missingText = "const noop = () => null"

        assertTrue(method.invoke(InheritanceFinder, extendsText, classElement) as Boolean)
        assertTrue(method.invoke(InheritanceFinder, implementsText, interfaceElement) as Boolean)
        assertTrue(method.invoke(InheritanceFinder, reactHocText, reactElement) as Boolean)
        assertFalse(method.invoke(InheritanceFinder, missingText, classElement) as Boolean)
    }

    private fun newElement(name: String, type: String): Any {
        val ctor = inheritableElementClass.getDeclaredConstructor(String::class.java, elementTypeClass)
        ctor.isAccessible = true
        @Suppress("UNCHECKED_CAST", "USELESS_CAST")
        val enumValue = java.lang.Enum.valueOf(elementTypeClass as Class<out Enum<*>>, type)
        return ctor.newInstance(name, enumValue)
    }

    @Test
    fun `detectLanguage infers from extension`() {
        val method = InheritanceFinder::class.java.getDeclaredMethod(
            "detectLanguage",
            VirtualFile::class.java
        ).apply { isAccessible = true }

        val javaFile = mockk<VirtualFile>()
        every { javaFile.extension } returns "java"
        val tsxFile = mockk<VirtualFile>()
        every { tsxFile.extension } returns "tsx"

        assertEquals("JAVA", method.invoke(InheritanceFinder, javaFile).toString())
        assertEquals("TSX", method.invoke(InheritanceFinder, tsxFile).toString())
    }

    @Test
    fun `findImplementations discovers TypeScript inheritors`() = runBlocking {
        val project = mockk<Project>(relaxed = true)
        mockkStatic(GlobalSearchScope::class)
        every { GlobalSearchScope.projectScope(project) } returns mockk(relaxed = true)

        mockkStatic(ReadAction::class)
        every { ReadAction.compute(any<ThrowableComputable<*, *>>()) } answers {
            val computable = it.invocation.args[0] as ThrowableComputable<Any?, Exception>
            computable.compute()
        }

        mockkStatic(ApplicationManager::class)
        val app = mockk<Application>(relaxed = true)
        every { ApplicationManager.getApplication() } returns app
        every { app.runReadAction(any<Computable<*>>()) } answers {
            val computable = it.invocation.args[0] as Computable<*>
            computable.compute()
        }

        mockkStatic(ProjectFileIndex::class)
        val fileIndex = mockk<ProjectFileIndex>()
        every { ProjectFileIndex.getInstance(project) } returns fileIndex

        mockkStatic(PsiManager::class)
        val psiManager = mockk<PsiManager>()
        every { PsiManager.getInstance(project) } returns psiManager

        val baseFile = mockVirtualFile("BaseWidget.ts")
        val implFile = mockVirtualFile("Button.ts")
        val basePsi = mockk<PsiFile>()
        val implPsi = mockk<PsiFile>()
        every { basePsi.virtualFile } returns baseFile
        every { implPsi.virtualFile } returns implFile
        every { psiManager.findFile(baseFile) } returns basePsi
        every { psiManager.findFile(implFile) } returns implPsi
        every { basePsi.text } returns "export class BaseWidget {}"
        every { implPsi.text } returns "class Button extends BaseWidget {}"

        every { fileIndex.iterateContent(any()) } answers {
            val iterator = it.invocation.args[0] as ContentIterator
            iterator.processFile(implFile)
            true
        }

        val implementations = InheritanceFinder.findImplementations(arrayOf(baseFile), project)

        assertEquals(setOf(implFile), implementations)
        unmockkAll()
    }

    private fun mockVirtualFile(name: String): VirtualFile {
        val vf = mockk<VirtualFile>(relaxed = true)
        every { vf.name } returns name
        every { vf.extension } returns name.substringAfterLast('.', "ts")
        every { vf.nameWithoutExtension } returns name.substringBeforeLast('.')
        every { vf.isDirectory } returns false
        every { vf.isValid } returns true
        every { vf.path } returns "/repo/$name"
        return vf
    }
}
