# 📋🚀 Export Source to Clipboard – IntelliJ Plugin

**Supercharge your code sharing and AI workflows directly from IntelliJ IDEA!**

[![JetBrains Plugin Version](https://img.shields.io/jetbrains/plugin/v/23881-export-source-to-clipboard.svg)](https://plugins.jetbrains.com/plugin/23881-export-source-to-clipboard)
[![JetBrains Plugin Downloads](https://img.shields.io/jetbrains/plugin/d/23881-export-source-to-clipboard.svg)](https://plugins.jetbrains.com/plugin/23881-export-source-to-clipboard)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)



**Export Source to Clipboard** transforms your IDE into an AI-ready powerhouse. It intelligently packages your source code—along with its full context—directly into your clipboard, making it perfect for providing deep context to Large Language Models (LLMs).

---

### Table of Contents
- [Why Use This Plugin?](#-why-use-this-plugin)
- [How is this different from CLI tools like `repomix`?](#-how-is-this-different-from-cli-tools-like-repomix)
- [✨ Key Features](#-key-features)
- [📣 What's New in Version 2.1](#-whats-new-in-version-21)
- [📣 What's New in Version 2.0](#-whats-new-in-version-20)
- [🧑‍💻 How to Get Started](#-how-to-get-started)
- [⚙️ Configuration](#️-configuration)
- [Full Changelog](#-full-changelog)
- [🤝 Contribute](#-contribute)
- [📜 License](#-license)

---

### 🎯 Why Use This Plugin?

The quality of your AI's output depends directly on the quality of your input. This plugin ensures every prompt you create is rich with the context necessary for high-quality analysis and code generation.

*   **⚡️ Get Superior AI Results:** Provide LLMs with clean, context-aware code to get dramatically better analysis, suggestions, and refactoring.
*   **🧠 Build Complete Context Effortlessly:** Instantly gather related files like dependencies, implementations, or tests to give your AI a comprehensive understanding of the code's ecosystem.
*   **🤖 Full Token Control:** Fine-tune every export to stay within LLM token limits, preventing context overflow and optimizing your AI interactions for cost and performance.

![Export Source to Clipboard IntelliJ Plugin Demo](media/demo.gif)

---

### 🤔 How is this different from CLI tools like `repomix`?

CLI tools like `repomix` (formerly `repopack`) are excellent for packaging entire repositories into a single file. This plugin excels at a different, more surgical task: **building a precise, context-aware prompt *from your current focus* within the IDE.**

Instead of manually listing files for a CLI tool, you can:
*   Right-click a class and select **"Export with Implementations"**.
*   Right-click a function and select **"Export with Dependents"** to find all its usages.
*   Right-click a component and select **"Export with Tests & Styles"**.

This plugin is better for the iterative, in-IDE loop of an AI-assisted workflow because of:

*   **🧠 Smart, Context-Aware Actions:** The plugin understands your code. It automatically finds related files (dependencies, tests, implementations, etc.) so you don't have to.
*   ** seamlessly IDE Integration:** Never leave your editor. The entire workflow happens inside IntelliJ, keeping you in the zone. No context switching to a terminal.
*   **Intelligent Filtering on the Fly:** It automatically respects your `.gitignore` files, skips binaries, and filters by file size *as it exports*, ensuring a clean and relevant context every time.
*   **Instant Feedback Loop:** Get immediate notifications with token counts and file statistics, allowing you to quickly iterate on your context before sending it to an LLM.

**Think of it as the difference between packing your whole house for a move (`repomix`) and having a robot assistant hand you exactly the tools you need for a specific task (this plugin).**

---

### ✨ Key Features

#### 🎯 New in v2.0: Smart Export Actions
Instantly grab not just the code you see, but the context that surrounds it. Our new smart actions understand your code's relationships, allowing you to export exactly what you need with a single click.

| Category | Action | Best For... |
| :--- | :--- | :--- |
| **Dependencies** | `Direct Imports` | Understanding a file's immediate dependencies. |
| | `Transitive Imports` | Getting the full dependency tree for a component. |
| | `Dependents` | **Impact analysis**; finding all files that use your selection. |
| **Code Structure**| `Implementations / Subclasses`| Exploring polymorphic behavior and abstractions. |
| | `Related Tests` | Grabbing the primary tests for a specific source file. |
| | `Package Files` | Exporting all files within the same directory/package. |
| **Related Resources**| `Templates & Styles` | Full-stack context for frontend and backend components. |
| | `Configuration Files` | Including project configs (`pom.xml`, `package.json`, etc.). |
| | `All Tests & Resources`| Getting comprehensive test coverage for a feature. |
| **Version History**| `Recent Changes` | Reviewing work from the last 24 hours. |
| | `Last Commit Files` | Creating context from all files in the latest commit. |

#### Core Functionality
*   **📂 Smart Directory Processing:** Automatically compiles entire directories into a clean, shareable format while intelligently skipping binaries, ignored files (via `.gitignore`), and overly large files.
*   **🔢 Token Count Estimation:** Get instant feedback on estimated token usage to stay within LLM limits.
*   **🧹 Stack Trace Folding:** Intelligently collapse noisy library frames in stack traces to highlight your project's code, simplifying error investigation.
*   **🌍 Enhanced Multi-Language Support:** Full support for TypeScript, JavaScript, React, and Next.js across all smart actions, with modern framework awareness.
*   **🛠️ IntelliJ Integrated:** Seamlessly fits into your existing IntelliJ workflow with a native look and feel.

---

### 📣 What's New in Version 2.1?

This release focuses on workflow polish, clarity, and accuracy.

*   **✅ Accurate Diff & History:** Export history now stores the actual files included after `.gitignore`, filter, size, and binary checks. The Diff view shows true added/removed files.
*   **📚 Deterministic Ordering:** Exports are sorted by relative path, making output easier to scan and diffs cleaner.
*   **🧩 Clearer Filter Controls:** New “Enable file extension filters” checkbox in Settings, plus a note clarifying that an empty enabled list doesn’t exclude files (all non-binary files considered, within size/ignore limits).
*   **🔔 Richer Export Notification:** The “Content Copied” notification now includes a concise summary (processed/excluded files, active filters).
*   **🛠️ Special Filenames:** Better support for files without extensions (Dockerfile/Makefile) for correct filename prefixes and Markdown language hints.
*   **⚙️ Stability & Consistency:** Unified token estimation in repository summaries. Earlier cancellation when file limits are reached. Streamed sampling for binary detection to avoid heavy reads.
*   **⌨️ macOS Keymap Reliability:** Added modern `macOS` keymap entries to ensure shortcuts work across IDE keymaps.

---

### 📣 What's New in Version 2.0?

Version 2.0 is a complete redesign focused on providing deep, contextual code exports with ease.

*   **🚀 All-New Smart Export Actions:** The context menu has been rebuilt from the ground up with powerful, logically grouped actions for dependencies, code structure, resources, and version history.
*   **✨ Redesigned UI & Workflow:** An intuitive, hierarchical menu makes finding the right export option effortless. Plus, new icons provide instant visual cues.
*   **⌨️ New Keyboard Shortcuts:** Use `Ctrl+Shift+C` for a standard export and `Ctrl+Shift+Alt+C` to quickly toggle file filters.
*   **📋 Export History & Diff:** A new tool window lets you track your last 10 exports and even compare your current selection against the last one to see what's changed.
*   **🎯 Smarter & Faster:** With improved `gitignore` handling and optimized performance, you get more accurate results, faster.

---

### 🧑‍💻 How to Get Started

**1. Install the Plugin**
- Go to `Settings/Preferences` ➜ `Plugins` ➜ `Marketplace`.
- Search for "Export Source to Clipboard" and click **Install**.

**2. Export Your Source**
- In your Project View, right-click a file or directory.
- Choose from the **"Export Source"** menu:
  - **`Export Selected Files`**: The classic action—exports exactly what you selected.
  - **`Export with Context`**: A submenu with all the new Smart Actions.
  - **`Compare with Last Export`**: See what's changed since your last export.

**3. Paste and Go!**
- Your clipboard now contains perfectly formatted, context-rich code. Paste it into your AI tool's prompt window.

---

### ⚙️ Configuration

Customize the plugin to fit your exact needs. Go to `Settings/Preferences` ➜ `Tools` ➜ `Export Source to Clipboard`.

<details>
<summary><strong>Click to view configuration options</strong></summary>

*   **File Limits:** Set the maximum number of files and the maximum size (in KB) for any single file to be included in the export.
*   **Content Options:**
  *   Toggle the `// filename:` prefix.
  *   Toggle line numbers.
  *   Toggle the inclusion of a directory tree at the start of the export.
  *   Toggle a repository summary with file stats.
*   **Output Format:** Choose between `Plain Text`, `Markdown`, or `XML` output.
*   **Filters:** Specify a list of file extensions to include (e.g., `java`, `.kt`). If the list is empty and filters are enabled, nothing will match. If filters are disabled, all files are considered.
*   **Ignored Names:** Provide a list of file or directory names to always exclude (e.g., `node_modules`, `build`).

</details>

### 🧪 Testing & Coverage

- Run the IntelliJ harness tests (includes IDE integration wiring) via `./gradlew test`.
- Run the plain JVM unit tests via `./gradlew unitTest`—these power JaCoCo coverage.
- Generate an HTML/XML coverage report with `./gradlew jacocoTestReport`; open `build/reports/jacoco/test/html/index.html` to inspect the results.

---

### 📜 Full Changelog

For a detailed history of all changes, see the [CHANGELOG.md](CHANGELOG.md) file.

---

### 🤝 Contribute

Your insights and contributions make this tool better! Fork the repo, make your changes, and submit a pull request—we’re excited to see your ideas.

---

### 📜 License

Distributed under the MIT License. See the `LICENSE` file for full details.

Clipboard icon by [janjf93](https://pixabay.com/vectors/flat-design-symbol-icon-www-2126883/) (Pixabay License).

---

Made with ❤️ by [Antonio Agudo](https://www.antonioagudo.com)
