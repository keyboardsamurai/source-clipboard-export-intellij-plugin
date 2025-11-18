package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InheritanceFinderTest {

    private val inheritableElementClass = Class.forName(
        "com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.InheritanceFinder\$InheritableElement"
    )
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
        @Suppress("UNCHECKED_CAST")
        val enumValue = java.lang.Enum.valueOf(elementTypeClass as Class<out Enum<*>>, type)
        return ctor.newInstance(name, enumValue)
    }
}
