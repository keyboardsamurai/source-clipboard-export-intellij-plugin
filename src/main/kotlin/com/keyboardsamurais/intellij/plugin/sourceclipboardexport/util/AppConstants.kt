package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

object AppConstants {
    const val NOTIFICATION_GROUP_ID = "SourceClipboardExport"
    const val FILENAME_PREFIX = "// filename: " // Default C-style comment prefix
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
}
