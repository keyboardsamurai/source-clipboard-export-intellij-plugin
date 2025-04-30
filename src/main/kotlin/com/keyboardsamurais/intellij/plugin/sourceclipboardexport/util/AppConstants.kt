package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

object AppConstants {
    const val NOTIFICATION_GROUP_ID = "SourceClipboardExport"
    const val FILENAME_PREFIX = "// filename: " // Default C-style comment prefix

    // Output format options
    enum class OutputFormat {
        PLAIN_TEXT,
        MARKDOWN,
        XML
    }
    val DEFAULT_IGNORED_NAMES = listOf(".git", "node_modules", "build", "target", "__pycache__")
    val COMMON_BINARY_EXTENSIONS = setOf(
        "png", "jpg", "jpeg", "gif", "bmp", "tiff", "ico",
        "mp3", "wav", "ogg", "flac", "aac",
        "mp4", "avi", "mov", "wmv", "mkv",
        "zip", "rar", "7z", "tar", "gz", "bz2",
        "exe", "dll", "so", "dylib", "app",
        "o", "obj", "lib", "a",
        "class", "pyc",
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "jar", "war", "ear",
        "woff", "woff2", "ttf", "otf", "eot",
        "db", "sqlite", "mdb",
        "iso", "img", "swf"
    )

    // Map of file extensions to comment prefixes
    val COMMENT_PREFIXES = mapOf(
        // C-style comments (default)
        "c" to "// filename: ",
        "cpp" to "// filename: ",
        "cs" to "// filename: ",
        "java" to "// filename: ",
        "js" to "// filename: ",
        "ts" to "// filename: ",
        "tsx" to "// filename: ",
        "jsx" to "// filename: ",
        "go" to "// filename: ",
        "swift" to "// filename: ",
        "kt" to "// filename: ",
        "kts" to "// filename: ",
        "scala" to "// filename: ",
        "dart" to "// filename: ",
        "rs" to "// filename: ",
        "php" to "// filename: ",
        "groovy" to "// filename: ",
        "gradle" to "// filename: ",

        // Hash-style comments
        "py" to "# filename: ",
        "rb" to "# filename: ",
        "pl" to "# filename: ",
        "sh" to "# filename: ",
        "bash" to "# filename: ",
        "zsh" to "# filename: ",
        "fish" to "# filename: ",
        "yaml" to "# filename: ",
        "yml" to "# filename: ",
        "toml" to "# filename: ",
        "r" to "# filename: ",
        "perl" to "# filename: ",
        "tcl" to "# filename: ",
        "Dockerfile" to "# filename: ",
        "makefile" to "# filename: ",
        "Makefile" to "# filename: ",

        // HTML/XML style comments
        "html" to "<!-- filename: -->",
        "htm" to "<!-- filename: -->",
        "xml" to "<!-- filename: -->",
        "svg" to "<!-- filename: -->",
        "xhtml" to "<!-- filename: -->",
        "jsp" to "<!-- filename: -->",
        "asp" to "<!-- filename: -->",
        "aspx" to "<!-- filename: -->",

        // SQL style comments
        "sql" to "-- filename: ",

        // Lisp style comments
        "lisp" to ";; filename: ",
        "clj" to ";; filename: ",
        "cljs" to ";; filename: ",
        "el" to ";; filename: ",

        // Batch file comments
        "bat" to "REM filename: ",
        "cmd" to "REM filename: ",

        // VB style comments
        "vb" to "' filename: ",
        "vbs" to "' filename: ",

        // Haskell style comments
        "hs" to "-- filename: ",

        // Lua style comments
        "lua" to "-- filename: ",

        // Assembly style comments
        "asm" to "; filename: ",
        "s" to "; filename: ",

        // Fortran style comments
        "f" to "! filename: ",
        "f90" to "! filename: ",
        "f95" to "! filename: ",

        // CSS style comments
        "css" to "/* filename: */",
        "scss" to "/* filename: */",
        "sass" to "/* filename: */",
        "less" to "/* filename: */"
    )

    // Map of file extensions to markdown language hints for code blocks
    // These are the language identifiers recognized by GitHub and other markdown renderers
    val MARKDOWN_LANGUAGE_HINTS = mapOf(
        // Programming Languages
        "c" to "c",
        "cpp" to "cpp",
        "cc" to "cpp",
        "h" to "c",
        "hpp" to "cpp",
        "cs" to "csharp",
        "java" to "java",
        "js" to "javascript",
        "jsx" to "jsx",
        "ts" to "typescript",
        "tsx" to "tsx",
        "go" to "go",
        "rb" to "ruby",
        "py" to "python",
        "php" to "php",
        "pl" to "perl",
        "kt" to "kotlin",
        "kts" to "kotlin",
        "swift" to "swift",
        "rs" to "rust",
        "scala" to "scala",
        "groovy" to "groovy",
        "gradle" to "gradle",
        "dart" to "dart",
        "r" to "r",
        "sh" to "bash",
        "bash" to "bash",
        "zsh" to "bash",
        "fish" to "fish",
        "ps1" to "powershell",
        "bat" to "batch",
        "cmd" to "batch",
        "hs" to "haskell",
        "lua" to "lua",
        "sql" to "sql",
        "asm" to "asm",
        "s" to "asm",
        "f" to "fortran",
        "f90" to "fortran",
        "f95" to "fortran",
        "lisp" to "lisp",
        "clj" to "clojure",
        "cljs" to "clojure",
        "el" to "lisp",
        "vb" to "vb",
        "vbs" to "vbscript",
        "perl" to "perl",
        "tcl" to "tcl",

        // Markup and Style Languages
        "html" to "html",
        "htm" to "html",
        "xml" to "xml",
        "svg" to "svg",
        "xhtml" to "html",
        "jsp" to "jsp",
        "asp" to "asp",
        "aspx" to "aspx",
        "css" to "css",
        "scss" to "scss",
        "sass" to "sass",
        "less" to "less",
        "md" to "markdown",
        "markdown" to "markdown",
        "tex" to "latex",
        "latex" to "latex",

        // Data Formats
        "json" to "json",
        "yaml" to "yaml",
        "yml" to "yaml",
        "toml" to "toml",
        "csv" to "csv",
        "tsv" to "tsv",

        // Configuration Files
        "ini" to "ini",
        "conf" to "conf",
        "properties" to "properties",
        "prop" to "properties",
        "env" to "env",
        "gitignore" to "gitignore",
        "dockerignore" to "dockerignore",
        "Dockerfile" to "dockerfile",
        "makefile" to "makefile",
        "Makefile" to "makefile",

        // Other
        "log" to "log",
        "txt" to "text"
    )
}
