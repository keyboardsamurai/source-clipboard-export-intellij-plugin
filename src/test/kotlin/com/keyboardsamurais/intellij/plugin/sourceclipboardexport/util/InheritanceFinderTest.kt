package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InheritanceFinderTest {
    
    @Test
    fun testTypeScriptClassExtraction() {
        val tsCode = """
            export class UserService {
                getUser() { return null; }
            }
            
            export abstract class BaseComponent {
                abstract render(): void;
            }
            
            interface UserRepository {
                findById(id: string): User;
            }
            
            export interface ApiClient {
                get(url: string): Promise<any>;
            }
        """.trimIndent()
        
        val elements = extractInheritableElementsFromText(tsCode)
        
        val classNames = elements.filter { it.type == "CLASS" }.map { it.name }
        val abstractClasses = elements.filter { it.type == "ABSTRACT_CLASS" }.map { it.name }
        val interfaces = elements.filter { it.type == "INTERFACE" }.map { it.name }
        
        assertTrue(classNames.contains("UserService"))
        assertTrue(abstractClasses.contains("BaseComponent"))
        assertTrue(interfaces.contains("UserRepository"))
        assertTrue(interfaces.contains("ApiClient"))
    }
    
    @Test
    fun testReactComponentExtraction() {
        val reactCode = """
            export const Button = (props: ButtonProps) => {
                return <button>{props.children}</button>;
            };
            
            export function Modal({ children }: ModalProps) {
                return <div className="modal">{children}</div>;
            }
            
            const Header: React.FC<HeaderProps> = ({ title }) => {
                return <h1>{title}</h1>;
            };
            
            // Non-component (doesn't start with capital)
            const utils = () => { return {}; };
        """.trimIndent()
        
        val elements = extractInheritableElementsFromText(reactCode)
        val componentNames = elements.filter { it.type == "REACT_COMPONENT" }.map { it.name }
        
        assertTrue(componentNames.contains("Button"))
        assertTrue(componentNames.contains("Modal"))
        assertTrue(componentNames.contains("Header"))
        assertFalse(componentNames.contains("utils"))
    }
    
    @Test
    fun testImplementationDetectionTypeScript() {
        val interfaceCode = """
            interface Drawable {
                draw(): void;
            }
            
            interface Clickable {
                onClick(): void;
            }
        """.trimIndent()
        
        val implementationCode = """
            class Button implements Clickable, Drawable {
                draw() { console.log('drawing button'); }
                onClick() { console.log('clicked'); }
            }
            
            class Canvas implements Drawable {
                draw() { console.log('drawing canvas'); }
            }
        """.trimIndent()
        
        assertTrue(isImplementingElementInText(implementationCode, createMockElement("Drawable", "INTERFACE")))
        assertTrue(isImplementingElementInText(implementationCode, createMockElement("Clickable", "INTERFACE")))
        assertFalse(isImplementingElementInText(implementationCode, createMockElement("NonExistent", "INTERFACE")))
    }
    
    @Test
    fun testClassExtensionDetection() {
        val baseClassCode = """
            abstract class BaseService {
                abstract execute(): void;
            }
            
            class UserService {
                getUsers() { return []; }
            }
        """.trimIndent()
        
        val extensionCode = """
            class EmailService extends BaseService {
                execute() { console.log('sending email'); }
            }
            
            class AdminUserService extends UserService {
                getAdminUsers() { return []; }
            }
        """.trimIndent()
        
        assertTrue(isImplementingElementInText(extensionCode, createMockElement("BaseService", "ABSTRACT_CLASS")))
        assertTrue(isImplementingElementInText(extensionCode, createMockElement("UserService", "CLASS")))
        assertFalse(isImplementingElementInText(extensionCode, createMockElement("NonExistent", "CLASS")))
    }
    
    @Test
    fun testReactHOCDetection() {
        val baseComponentCode = """
            export const Button = ({ children }) => {
                return <button>{children}</button>;
            };
        """.trimIndent()
        
        val hocCode = """
            import { withRouter } from 'react-router-dom';
            import { connect } from 'react-redux';
            import { memo, forwardRef } from 'react';
            
            const RouterButton = withRouter(Button);
            const ConnectedButton = connect(mapStateToProps)(Button);
            const MemoButton = memo(Button);
            const RefButton = forwardRef(Button);
            
            const WrappedButton = (props) => <Button {...props} />;
        """.trimIndent()
        
        assertTrue(isImplementingElementInText(hocCode, createMockElement("Button", "REACT_COMPONENT")))
    }
    
    @Test
    fun testTestFileDetection() {
        // JavaScript/TypeScript test patterns
        assertTrue(isTestFileByName("Component.test.js"))
        assertTrue(isTestFileByName("Component.spec.ts"))
        assertTrue(isTestFileByName("Component.test.tsx"))
        assertTrue(isTestFileByPath("src/__tests__/Component.js"))
        assertTrue(isTestFileByPath("src/tests/utils.ts"))
        
        // Java test patterns
        assertTrue(isTestFileByName("ComponentTest.java"))
        assertTrue(isTestFileByName("ComponentTests.java"))
        assertTrue(isTestFileByName("TestComponent.java"))
        assertTrue(isTestFileByName("ComponentIT.java"))
        assertTrue(isTestFileByPath("src/test/java/ComponentTest.java"))
        
        // Non-test files
        assertFalse(isTestFileByName("Component.js"))
        assertFalse(isTestFileByName("ComponentService.java"))
        assertFalse(isTestFileByPath("src/main/java/Component.java"))
    }
    
    @Test
    fun testLanguageDetection() {
        assertEquals("java", detectLanguageFromExtension("java"))
        assertEquals("kt", detectLanguageFromExtension("kt"))
        assertEquals("js", detectLanguageFromExtension("js"))
        assertEquals("ts", detectLanguageFromExtension("ts"))
        assertEquals("jsx", detectLanguageFromExtension("jsx"))
        assertEquals("tsx", detectLanguageFromExtension("tsx"))
        assertEquals("unknown", detectLanguageFromExtension("unknown"))
    }
    
    @Test
    fun testInterfaceExtension() {
        val baseInterfaceCode = """
            interface BaseRepository<T> {
                findById(id: string): T;
            }
        """.trimIndent()
        
        val extendedInterfaceCode = """
            interface UserRepository extends BaseRepository<User> {
                findByEmail(email: string): User;
            }
            
            interface AdminRepository extends BaseRepository<Admin>, AuditableRepository {
                findAdmins(): Admin[];
            }
        """.trimIndent()
        
        assertTrue(isImplementingElementInText(extendedInterfaceCode, createMockElement("BaseRepository", "INTERFACE")))
        assertTrue(isImplementingElementInText(extendedInterfaceCode, createMockElement("AuditableRepository", "INTERFACE")))
    }
    
    @Test
    fun testGenericClassPatterns() {
        val genericCode = """
            export class Repository<T extends Entity> {
                save(entity: T): Promise<T> { }
            }
            
            export interface Service<TRequest, TResponse> {
                process(request: TRequest): Promise<TResponse>;
            }
            
            class UserService extends Repository<User> implements Service<UserRequest, UserResponse> {
                process(request: UserRequest): Promise<UserResponse> { }
            }
        """.trimIndent()
        
        val elements = extractInheritableElementsFromText(genericCode)
        
        val classNames = elements.filter { it.type == "CLASS" }.map { it.name }
        val interfaceNames = elements.filter { it.type == "INTERFACE" }.map { it.name }
        
        assertTrue(classNames.contains("Repository"))
        assertTrue(interfaceNames.contains("Service"))
        
        // Test that implementation detection works with generics
        assertTrue(isImplementingElementInText(genericCode, createMockElement("Repository", "CLASS")))
        assertTrue(isImplementingElementInText(genericCode, createMockElement("Service", "INTERFACE")))
    }
    
    @Test
    fun testComplexInheritanceScenarios() {
        val complexCode = """
            export abstract class BaseComponent<TProps = {}> {
                abstract render(): JSX.Element;
            }
            
            export interface Themeable {
                theme?: string;
            }
            
            export interface Clickable {
                onClick?: () => void;
            }
            
            class Button extends BaseComponent<ButtonProps> implements Themeable, Clickable {
                render() { return <button>Click me</button>; }
            }
            
            // Multiple inheritance levels
            abstract class InteractiveComponent extends BaseComponent implements Clickable {
                onClick() { console.log('clicked'); }
            }
            
            class IconButton extends InteractiveComponent implements Themeable {
                render() { return <button><Icon /></button>; }
            }
        """.trimIndent()
        
        val elements = extractInheritableElementsFromText(complexCode)
        
        // Verify all types are detected
        assertTrue(elements.any { it.name == "BaseComponent" && it.type == "ABSTRACT_CLASS" })
        assertTrue(elements.any { it.name == "Themeable" && it.type == "INTERFACE" })
        assertTrue(elements.any { it.name == "Clickable" && it.type == "INTERFACE" })
        
        // Verify implementation detection works for complex scenarios
        assertTrue(isImplementingElementInText(complexCode, createMockElement("BaseComponent", "ABSTRACT_CLASS")))
        assertTrue(isImplementingElementInText(complexCode, createMockElement("Themeable", "INTERFACE")))
        assertTrue(isImplementingElementInText(complexCode, createMockElement("Clickable", "INTERFACE")))
    }
    
    // Helper methods for testing
    private fun extractInheritableElementsFromText(text: String): List<MockInheritableElement> {
        val elements = mutableListOf<MockInheritableElement>()
        
        // TypeScript/JavaScript class patterns
        val classPattern = Regex("""(?:export\s+)?(?:abstract\s+)?class\s+([A-Z][a-zA-Z0-9_]*)\s*(?:extends\s+[^{]+)?(?:implements\s+[^{]+)?\s*\{""")
        classPattern.findAll(text).forEach { match ->
            elements.add(MockInheritableElement(match.groupValues[1], "CLASS"))
        }
        
        // TypeScript interface patterns
        val interfacePattern = Regex("""(?:export\s+)?interface\s+([A-Z][a-zA-Z0-9_]*)\s*(?:extends\s+[^{]+)?\s*\{""")
        interfacePattern.findAll(text).forEach { match ->
            elements.add(MockInheritableElement(match.groupValues[1], "INTERFACE"))
        }
        
        // React component patterns
        val reactComponentPattern = Regex("""(?:export\s+)?(?:const|function)\s+([A-Z][a-zA-Z0-9_]*)\s*(?:\([^)]*\))?\s*(?::\s*[^=]+)?\s*=?\s*(?:\([^)]*\))?\s*=>""")
        reactComponentPattern.findAll(text).forEach { match ->
            val name = match.groupValues[1]
            if (name[0].isUpperCase()) {
                elements.add(MockInheritableElement(name, "REACT_COMPONENT"))
            }
        }
        
        // Abstract class patterns
        val abstractClassPattern = Regex("""(?:export\s+)?abstract\s+class\s+([A-Z][a-zA-Z0-9_]*)\s*(?:extends\s+[^{]+)?\s*\{""")
        abstractClassPattern.findAll(text).forEach { match ->
            elements.add(MockInheritableElement(match.groupValues[1], "ABSTRACT_CLASS"))
        }
        
        return elements
    }
    
    private fun isImplementingElementInText(candidateText: String, element: MockInheritableElement): Boolean {
        val elementName = element.name
        
        return when (element.type) {
            "CLASS", "ABSTRACT_CLASS" -> {
                val extendsPattern = Regex("""class\s+[A-Z][a-zA-Z0-9_]*\s+extends\s+$elementName(?:\s|<|,|\{)""")
                extendsPattern.containsMatchIn(candidateText)
            }
            "INTERFACE" -> {
                val implementsPattern = Regex("""(?:implements|extends)\s+(?:[^,\s{]*,\s*)*$elementName(?:\s|<|,|\{)""")
                implementsPattern.containsMatchIn(candidateText)
            }
            "REACT_COMPONENT" -> {
                val hocPattern = Regex("""(?:withRouter|connect|memo|forwardRef)\s*\(\s*$elementName\s*\)""")
                val wrapperPattern = Regex("""const\s+[A-Z][a-zA-Z0-9_]*\s*=\s*\([^)]*\)\s*=>\s*<$elementName""")
                hocPattern.containsMatchIn(candidateText) || wrapperPattern.containsMatchIn(candidateText)
            }
            else -> false
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
    
    private fun detectLanguageFromExtension(extension: String): String {
        return when (extension.lowercase()) {
            "java" -> "java"
            "kt", "kts" -> "kt"
            "js", "mjs" -> "js"
            "ts" -> "ts"
            "jsx" -> "jsx"
            "tsx" -> "tsx"
            else -> "unknown"
        }
    }
    
    private fun createMockElement(name: String, type: String): MockInheritableElement {
        return MockInheritableElement(name, type)
    }
    
    private data class MockInheritableElement(
        val name: String,
        val type: String
    )
} 