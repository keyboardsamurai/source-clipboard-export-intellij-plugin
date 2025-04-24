# Export Source to Clipboard IntelliJ Plugin

## Overview

Export Source to Clipboard is an IntelliJ plugin designed to automate the inclusion of source code and its file path into your clipboard, thereby streamlining the process for analysis, sharing, and collaboration, especially with Large Language Models (LLMs). It enhances the workflow for developers and teams by providing a convenient bridge between your code and the latest AI technologies.

Including the file path with the source code often gives immediate context to the recipient or the processing tool. 
This is particularly useful when sharing code snippets with team members or integrating with AI and LLMs, 
as it clarifies where the code comes from within a project's directory structure. 
It helps in understanding the purpose and scope of the code, especially in large projects with complex directory
hierarchies and results in better LLM response accuracy and performance.

![Export Source to Clipboard IntelliJ Plugin Demo](media/demo.gif)

## Features

- **AI-Ready Sharing:** Prepare your code for analysis by AI and LLMs with seamless clipboard integration.
- **Efficient Collaboration:** Quickly share code and its context with team members or AI tools to enhance your workflow.
- **Automated Convenience:** Automatically append file paths (configurable) to clipboard content, saving time and reducing manual errors.
- **Smart Directory Processing:** Converts entire directories into a shareable format, intelligently skipping binary files, ignored files/folders, and files exceeding size limits.
- **Configurable Limits:** Set maximum number of files and maximum file size (KB) to process.
- **Flexible Filtering:** Include only files matching specific extensions (e.g., `java`, `.kt`). Filters can be toggled on/off easily.
- **Ignore List:** Define a list of file and directory names (like `.git`, `node_modules`) to always exclude.
- **Informative Feedback:** Provides notifications on success, failure, limits reached, and a summary of processed/excluded files.
- **IntelliJ Integration:** Uses standard IntelliJ progress indicators, notifications, and clipboard management.

## Use Case

This plugin is an essential tool for developers and teams looking to leverage AI in their coding process, offering a convenient way to enhance productivity and focus on innovation throughout the software development lifecycle.

## Compatibility

The plugin requires IntelliJ IDEA platform to function and is compatible with the IntelliJ Platform Plugin SDK (check `build.gradle.kts` and `plugin.xml` for specific versions).

## Installation

You can install the Export Source to Clipboard plugin directly from the IntelliJ IDEA by navigating to the `Settings/Preferences` ➜ `Plugins` ➜ `Marketplace` and searching for "Export Source to Clipboard".

## Usage

1.  **(Optional) Configure Settings:** Go to `Settings/Preferences` ➜ `Tools` ➜ `Export Source to Clipboard` to adjust:
    *   Maximum number of files.
    *   Maximum file size (KB).
    *   Whether to include the `// filename: path` prefix.
    *   File extension filters (add/remove).
    *   Ignored file/directory names.
2.  **Export:** Right-click on any file or directory in the Project view.
3.  **Select Action:** Choose "Export Source to Clipboard".
4.  **(Optional) Toggle Filters:** Right-click and choose "Filters: enabled/disabled" to quickly toggle the extension filters without going into settings.
5.  **Paste:** The content will be processed in the background and copied to your clipboard.

## Contributing

We welcome contributions! If you're interested in improving the Export Source to Clipboard plugin or have suggestions, please feel free to fork the repository, make your changes, and submit a pull request.

## License

This project is licensed under the MIT License. See the LICENSE file for more details.

Clipboard Icon provided by [janjf93](https://pixabay.com/vectors/flat-design-symbol-icon-www-2126883/) under the Pixabay License.
