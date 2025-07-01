# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

This IntelliJ IDEA plugin streamlines the process of exporting source code to clipboard for analysis and sharing, particularly optimized for use with Large Language Models (LLMs). It automatically includes file paths, handles complex directory structures, and provides context-aware export options.

## Build Commands

```bash
# Build the plugin
./gradlew build

# Run all tests
./gradlew test

# Run all tests with explicit output
./gradlew runAllTests

# Run a single test class
./gradlew test --tests "com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.SourceExporterTest"

# Run a single test method
./gradlew test --tests "com.keyboardsamurais.intellij.plugin.sourceclipboardexport.core.SourceExporterTest.testExportWithBinaryFileExclusion"

# Run IntelliJ IDEA with the plugin installed for manual testing
./gradlew runIde

# Create plugin distribution
./gradlew buildPlugin

# Sign plugin (requires CERTIFICATE_CHAIN, PRIVATE_KEY, PRIVATE_KEY_PASSWORD env vars)
./gradlew signPlugin

# Publish to JetBrains marketplace (requires PUBLISH_TOKEN env var)
./gradlew publishPlugin

# Check for linting issues
./gradlew ktlintCheck

# Run lint and typecheck if available
# Note: Look for gradle tasks like 'lint' or 'typecheck' in build.gradle.kts
```

## High-Level Architecture

This is an IntelliJ IDEA plugin that exports source code to clipboard with intelligent formatting for AI/LLM contexts. The architecture follows a layered approach:

### Core Export Flow
1. **Actions** (`DumpFolderContentsAction`) receive user input from IDE context menus
2. **SourceExporter** orchestrates the export process using Kotlin coroutines for concurrent file processing
3. **GitignoreParser** hierarchy respects .gitignore rules from root to file location
4. **Output formatting** supports Plain Text, Markdown, and XML formats with configurable templates

### Key Components

#### 1. **SourceExporter** (`core/SourceExporter.kt`)
The central engine handling file processing and export generation.
- **Concurrent Processing**: Uses Kotlin coroutines with system CPU-based parallelism
- **Smart Filtering**: Binary detection, size limits, gitignore respect, custom filters
- **Output Formats**: Plain Text, Markdown (with syntax highlighting), XML
- **Token Estimation**: Accurate GPT token counting using jtokkit library

#### 2. **HierarchicalGitignoreParser** (`core/gitignore/`)
Advanced gitignore handling with caching and hierarchical rule application.
- Parses and caches gitignore files for performance
- Handles nested gitignore files in subdirectories
- Provides git-consistent exclusion behavior

#### 3. **Smart Export Actions** (`actions/`)
Context-aware export actions for different workflows:
- **ExportWithTestsAction**: Includes related test files
- **ExportWithConfigsAction**: Includes configuration files (pom.xml, package.json, etc.)
- **ExportRecentChangesAction**: Exports files modified in last 24 hours
- **ExportWithDirectImportsAction**: Includes directly imported files
- **ExportWithTransitiveImportsAction**: Includes all dependencies
- **ExportCurrentPackageAction**: Exports entire package/module

#### 4. **Export History** (`core/ExportHistory.kt`)
Persistent tracking of exports for workflow continuity.
- Stores last 10 exports with metadata
- Enables diff comparison between exports
- Project-level persistence using IntelliJ's state components

#### 5. **StackTraceFolder** (`core/StackTraceFolder.kt`)
Intelligent stack trace processing for console output.
- Folds library frames while keeping project code visible
- Special handling for Spring test contexts
- PSI-aware project code detection

### UI Components

#### 1. **Tool Window** (`ui/ExportToolWindow.kt`)
Optional visual interface for file selection and preview.
- Interactive file tree with checkboxes
- Real-time preview with token counting
- Can be enabled/disabled in settings

#### 2. **Configuration UI** (`config/`)
Comprehensive settings management with validation.
- Smart defaults for immediate usability
- Preview functionality for settings
- Custom table editors for filters

#### 3. **Streamlined Menu Structure**
Organized right-click context menu:
```
Export Source
├── Export Selected Files      [Ctrl/Cmd+Shift+C]
├── Export with Context
│   ├── Include Tests
│   ├── Include Configuration
│   ├── Recent Changes
│   ├── Include Package Files
│   ├── Include Direct Imports
│   └── Include All Dependencies
├── Compare with Last Export
└── Toggle File Filters        [Ctrl/Cmd+Shift+Alt+C]
```

### Key Architectural Decisions
- **Concurrent Processing**: Uses Kotlin coroutines to process multiple files in parallel, critical for performance with large codebases
- **Hierarchical Gitignore**: Gitignore rules are parsed and applied hierarchically from project root down to each file's directory
- **Token Estimation**: Uses JTokkit library for accurate GPT token counting to help users manage LLM context limits
- **PSI Integration**: Stack trace folding uses IntelliJ's PSI (Program Structure Interface) to identify project vs external code
- **Service Pattern**: Settings and history management use IntelliJ's service infrastructure
- **Command Pattern**: Actions are implemented as separate command classes extending `AnAction`

### Extension Points
- **File Filters**: Configurable through settings UI with include/exclude patterns
- **Output Formats**: Template-based formatting system allows easy addition of new formats
- **Size Limits**: Both file size and total export size limits are configurable
- **Smart Export Actions**: Easy to add new context-aware export types

### Testing Strategy
- Mock IntelliJ platform components using MockK with `relaxed = true` for complex interfaces
- Test concurrent behavior explicitly with large file sets
- Verify both success and error paths for all major features
- Use parameterized tests for comprehensive edge case coverage
- **Test Organization**: Tests mirror source structure for easy navigation
- **Performance Tests**: Dedicated tests for concurrent processing validation

### Important Implementation Notes
- Always use `VirtualFile` API for file operations, never `java.io.File`
- Action updates must specify thread requirements via `getActionUpdateThread()`
- Settings persistence uses IntelliJ's `@State` annotation pattern
- Notifications should use `NotificationUtils` for consistent user feedback
- Binary file detection uses both extension checking and content sampling
- Icon loading must handle test environments gracefully (use `IconUtils` helper)
- Use `BGT` (background thread) for action updates accessing file system
- Respect IntelliJ's threading model - UI updates on EDT, file operations on BGT

## Recent Major Improvements (v1.9)

### Smart Export Actions
1. **Export Dependents**: Revolutionary reverse dependency finder
   - Find all files that import or depend on selected files
   - Perfect for impact analysis before refactoring
   - Multi-language support (Java, Kotlin, JS, TS, Python)
   
2. **Export Implementations**: Find all implementations and subclasses
   - Discover all implementations of interfaces
   - Find all subclasses of abstract/concrete classes
   - Includes anonymous inner classes
   
3. **Export Templates & Styles**: Framework-aware resource detection
   - Automatic detection for Spring, React, Vue, Angular
   - Finds related HTML, CSS, and resource files
   - Uses pattern matching and string literal analysis
   
4. **Export All Tests**: Comprehensive test discovery
   - Unit, integration, E2E, and performance tests
   - Test resources, fixtures, and utilities
   - Categorized reporting by test type

### Enhanced Menu Structure
- Hierarchical organization with logical grouping
- Four main categories: Dependencies, Code Structure, Related Resources, Version History
- Visual separators for better discoverability
- Intuitive action naming and descriptions

## Recent Major Improvements (v1.8)

### UI/UX Enhancements
1. **Quick Export Tool Window**: Optional visual file selection interface
2. **Smart Export Actions**: Context-aware exports (tests, configs, imports, etc.)
3. **Export History**: Track last 10 exports with diff comparison
4. **Keyboard Shortcuts**: `Ctrl/Cmd+Shift+C` for main export
5. **Enhanced Notifications**: File count, size, and token estimates
6. **Streamlined Right-Click Menu**: Single "Export Source" menu with organized submenus

### Performance Optimizations
1. **CPU-based Thread Pool**: Dynamic sizing based on available cores
2. **Early Termination**: Stops at file limit exactly (not after)
3. **Efficient Token Counting**: Optimized subword heuristics
4. **Gitignore Caching**: Parsed patterns cached for session

### Developer Experience
1. **Clearer Error Messages**: Specific guidance for common issues
2. **Large Operation Warnings**: Confirmation for >100 files
3. **Smart Defaults**: 200 files, 500KB size, line numbers enabled
4. **Preview Export**: See output before copying

## Visual Style Guide

A comprehensive visual style guide is available at `documentation/visual_style.md` covering:
- Color schemes (theme-aware)
- Typography standards
- Icon specifications (16x16 SVG)
- Component patterns
- Spacing system (4px base unit)
- Accessibility requirements

## Common Development Tasks

### Adding a New Export Action
1. Create action class extending `AnAction` in `actions/` package
2. Implement `actionPerformed()` and `update()` methods
3. Add to `SmartExportGroup.getChildren()` if it's a smart export
4. Register in `plugin.xml` if it's a top-level action
5. Add corresponding test in `test/kotlin/.../actions/`

### Modifying Export Format
1. Update `SourceExporter.formatFileContent()` method
2. Add new format enum value if needed
3. Update tests in `SourceExporterTest`
4. Add format description to settings UI

### Adding New Settings
1. Add property to `SourceClipboardExportSettings.State`
2. Update `SourceClipboardExportConfigurable` UI
3. Implement validation in `isValid()`
4. Add default value consideration
5. Update tests and documentation

## Performance Considerations

### Memory Management
- Stream large files instead of loading entirely
- Use `StringBuilder` for efficient string concatenation
- Clear caches periodically (gitignore parser)

### Concurrency
- Default to `Runtime.getRuntime().availableProcessors()` threads
- Use `SupervisorJob` for coroutine error isolation
- Implement proper cancellation support

### UI Responsiveness
- Run file operations on background threads
- Use progress indicators for long operations
- Provide cancellation options for large exports

## Troubleshooting Common Issues

### Tests Failing with Icon Loading
- Icons are currently disabled in tests (set to `null`)
- Use `IconUtils.loadIcon()` for safe loading
- Icons need proper test environment setup

### File Not Included in Export
1. Check gitignore rules
2. Verify file size limits
3. Review filter settings
4. Check binary file detection

### Performance Issues
1. Reduce file count limit
2. Disable repository summary for large repos
3. Check for large binary files in selection

## Future Improvements Needed

1. **Fix Icon Loading**: Implement proper icon loading for production while maintaining test compatibility
2. **Smart Auto-Selection**: Auto-select related files based on context
3. **Export Presets**: Save and reuse common export configurations
4. **Performance Monitoring**: Add metrics for large exports
5. **Enhanced Tool Window**: Search/filter in file tree
6. **Export Templates**: User-defined export formats

## Code Style Guidelines

- **Kotlin Idioms**: Use Kotlin's expressive features (when, let, apply, etc.)
- **Null Safety**: Leverage Kotlin's null safety, avoid `!!`
- **Coroutines**: Prefer coroutines over threads for concurrency
- **Error Handling**: Comprehensive try-catch with meaningful messages
- **Logging**: Use appropriate log levels (debug, info, warn, error)
- **Documentation**: KDoc for public APIs, inline comments for complex logic

## Links and Resources

- **Plugin Repository**: https://github.com/keyboardsamurais/intellij-plugin-export-source
- **JetBrains Plugin Development**: https://plugins.jetbrains.com/docs/intellij/
- **Kotlin Coroutines**: https://kotlinlang.org/docs/coroutines-overview.html
- **MockK Documentation**: https://mockk.io/
- **JTokkit Library**: https://github.com/knuddelsgmbh/jtokkit