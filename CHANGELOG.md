# Changelog

All notable changes to the Export Source to Clipboard plugin will be documented in this file.

## [1.9.0] - 2024-01-XX

### Added
- **Export Dependents Action**: Find and export files that import or depend on selected files
  - Revolutionary reverse dependency analysis
  - Multi-language support (Java, Kotlin, JavaScript, TypeScript, Python)
  - Perfect for impact analysis before refactoring
  
- **Export Implementations Action**: Export all implementations and subclasses
  - Find all implementations of interfaces
  - Discover subclasses of abstract and concrete classes
  - Include anonymous inner classes
  - Provide helpful statistics
  
- **Export Templates & Styles Action**: Framework-aware resource detection
  - Automatic framework detection (Spring MVC, React, Vue, Angular)
  - Find related HTML, CSS, and resource files
  - Pattern matching and string literal analysis
  - Naming convention support
  
- **Export All Tests Action**: Comprehensive test discovery
  - Extended test patterns (unit, integration, E2E, performance)
  - Test resource and fixture detection
  - Test utility discovery
  - Categorized test reporting

- Keyboard shortcuts for quick access
- Smarter default settings
- Quick Export Tool Window
- Export History tracking
- Export Diff comparison
- Enhanced notifications


### Changed
- Reorganized menu structure with hierarchical grouping
  - Dependencies group: Direct Imports, Transitive Imports, Dependents
  - Code Structure group: Tests, Implementations, Package Files
  - Related Resources group: Configs, Templates & Styles, All Tests
  - Version History group: Recent Changes, Last Commit
- Added visual separators between menu groups
- Improved action naming for better clarity

### Technical Improvements
- Added specialized utility classes for each export type
- Implemented concurrent processing with Kotlin coroutines
- Enhanced language support for dependency analysis
- Added framework-specific detection algorithms

## [1.7.0] - 2023-XX-XX

### Added
- Stack trace folding functionality
- Enhanced gitignore parsing
- Accurate token counting with JTokkit

### Changed
- Improved performance
- Better error handling

## [1.6.0] - 2023-XX-XX

### Added
- Gitignore integration
- Token count insights
- Enhanced settings interface

### Changed
- Improved configuration options
- Better user feedback