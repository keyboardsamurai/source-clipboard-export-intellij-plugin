package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util

object AppConstants {
    const val NOTIFICATION_GROUP_ID = "SourceClipboardExport"
    const val FILENAME_PREFIX = "// filename: "
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
} 