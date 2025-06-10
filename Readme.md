# 📋🚀 Export Source to Clipboard – IntelliJ Plugin

**Supercharge your code sharing and AI workflows directly from IntelliJ IDEA!**

The **Export Source to Clipboard** plugin transforms your coding environment into an AI-ready powerhouse by effortlessly packaging your source code and relevant file paths directly into your clipboard—ideal for instant collaboration and seamless integration with Large Language Models (LLMs).

---

### 🎯 Why Use Export Source to Clipboard?

Sharing precise code snippets along with their paths accelerates understanding and boosts productivity. This plugin ensures every snippet shared retains critical context, helping team members and AI tools quickly grasp the project structure and logic, dramatically improving LLM-driven analysis and responses. You maintain full control over tokens sent to LLMs, preventing context overflow and optimizing AI interactions.

![Export Source to Clipboard IntelliJ Plugin Demo](media/demo.gif)

---

### ✨ Key Features

* **🚀 AI-Ready Sharing:** Instantly prepares your code snippets with context for smarter AI analysis.
* **🤝 Seamless Collaboration:** Effortlessly share detailed, contextual code snippets with teammates or AI assistants.
* **📂 Smart Directory Processing:** Automatically compiles entire directories into a clean, shareable format while intelligently skipping binaries, ignored files (e.g., `.gitignore`), and overly large files.
* **🔢 Token Count Estimation:** Get instant feedback on estimated token usage, making it easier to stay within LLM limits.
* **🧹 Stack Trace Folding:** Easily fold long, noisy stack traces to highlight the relevant information, simplifying error investigation.
* **⚙️ Advanced Configuration:** Easily set custom limits for file count, size, ignored paths, and file types directly from IntelliJ's settings.
* **🔔 Real-time Feedback:** Receive clear, intuitive notifications on export status, processed files, and any exclusions.
* **🛠️ IntelliJ Integrated:** Seamlessly fits into your existing IntelliJ workflow using familiar interfaces and interactions.

#### 🎯 Smart Export Actions

* **🔄 Export Dependents:** Find and export files that import or depend on your selected files - perfect for impact analysis.
* **🏗️ Export Implementations:** Export all implementations of interfaces and subclasses - ideal for understanding polymorphic behavior.
* **🎨 Export Templates & Styles:** Automatically include related HTML, CSS, and resource files - great for full-stack development.
* **🧪 Export All Tests:** Export comprehensive test coverage including unit, integration, E2E tests, and test resources.

---

### 🧑‍💻 How to Get Started

**1. Install the Plugin**

* Navigate to: `Settings/Preferences` ➜ `Plugins` ➜ `Marketplace`
* Search for "Export Source to Clipboard" and click install.

**2. (Optional) Customize Your Settings**

* Head over to: `Settings/Preferences` ➜ `Tools` ➜ `Export Source to Clipboard` and adjust to your workflow:

  * Set file size and file count limits.
  * Toggle file path prefixes.
  * Manage file extensions and ignored patterns.

**3. Export Your Source**

* Right-click a file or directory in your Project View.
* Choose from the "Export Source" menu:
  * **Export Selected Files** - Export exactly what you've selected
  * **Export with Context** - Choose from smart export options:
    * **Dependencies** → Direct Imports, Transitive Imports, or Dependents
    * **Code Structure** → Tests, Implementations/Subclasses, or Package Files
    * **Related Resources** → Configuration Files, Templates & Styles, or All Tests
    * **Version History** → Recent Changes or Last Commit Files

**4. Paste and Go!**

* Simply paste your clipboard content into any AI tool, chat, or collaboration environment.

---

### 🚧 What's New?

#### Version 2.0 – Smart Export Actions & Enhanced Menu Structure 🎯

* **🎯 Smart Export Actions:** New context-aware export options organized in logical groups:
  * **Dependencies:** Export Direct Imports, Transitive Imports, or find Dependents (reverse dependencies)
  * **Code Structure:** Export Tests, Implementations/Subclasses, or Current Package
  * **Related Resources:** Export Configuration Files, Templates & Styles, or All Related Tests
  * **Version History:** Export Recent Changes or Last Commit Files
* **🌍 Enhanced Multi-Language Support:** Full TypeScript, JavaScript, React, and Next.js support across all smart export actions with comprehensive test pattern detection and modern framework awareness
* **🔄 Export Dependents:** Revolutionary reverse dependency finder - discover what files depend on your selection
* **🏗️ Export Implementations:** Find all implementations of interfaces and subclasses with intelligent filtering
* **🎨 Export Templates & Styles:** Framework-aware resource detection for Spring, React, Vue, Angular, and more
* **🧪 Export All Tests:** Comprehensive test discovery including unit, integration, E2E, performance tests, and test resources

Small improvements

* **📋 Organized Menu Structure:** Intuitive hierarchical menu organization with logical grouping and separators
* **⌨️ Keyboard Shortcuts:** Quick access with Ctrl+Shift+C (Export) and Ctrl+Shift+Alt+C (Toggle Filters)
* **🎯 Smarter Defaults:** Better out-of-box experience with increased limits and sensible defaults
* **🖥️ Quick Export Tool Window:** Optional visual interface with file tree and real-time preview
* **📝 Export History:** Track your last 10 exports with diff comparison capabilities
* **📊 Enhanced Notifications:** Comprehensive export feedback with file count, size, and token estimates

#### Version 1.7 – Enhanced Stack Traces & Gitignore Magic ✨

* **📜 Gitignore Mastery:** Significantly upgraded gitignore parsing with hierarchical rule handling, caching optimizations, and precise pattern matching.
* **📌 Stack Trace Folding:** Introduced intelligent stack trace folding, making error logs cleaner by collapsing external frames and highlighting your project's code.
* **🚀 Accuracy Boosts:** Optimized token counting for GPT-4 accuracy, significantly enhancing export speed and responsiveness.

#### Version 1.6 – Powerful New Improvements

* **📜 Gitignore Integration:** Automatically respects your `.gitignore` settings, providing clean and relevant exports.
* **🧮 Token Count Insights:** Know exactly how much code you're exporting, helping you avoid LLM context overflow.
* **🎚️ Enhanced Settings Interface:** Now easier than ever to customize export limits and exclusions, with intuitive validation and user-friendly feedback.

---

### 🤝 Contribute

Your insights and contributions help make this tool better! Fork, modify, and submit pull requests—we’re excited to see your ideas.

---

### 📜 License

Distributed under the MIT License. See the [LICENSE](LICENSE) file for full details.

Clipboard icon by [janjf93](https://pixabay.com/vectors/flat-design-symbol-icon-www-2126883/) (Pixabay License).

---

Made with ❤️ by [Antonio Agudo](https://www.antonioagudo.com)
