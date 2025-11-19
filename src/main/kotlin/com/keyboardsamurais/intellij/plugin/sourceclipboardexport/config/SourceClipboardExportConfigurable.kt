package com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.ui.PlaceholderTextField
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.config.ui.TableButtonColumn
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.AppConstants.OutputFormat
import com.keyboardsamurais.intellij.plugin.sourceclipboardexport.util.StringUtils
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.GridLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.SpinnerNumberModel
import javax.swing.UIManager
import javax.swing.table.DefaultTableModel

class SourceClipboardExportConfigurable : Configurable {
    private var settingsPanel: JPanel? = null
    private var fileCountSpinner: JSpinner? = null
    private var maxFileSizeSpinner: JSpinner? = null
    private var filtersTableModel: DefaultTableModel? = null
    private var filtersTable: JBTable? = null
    private var addFilterTextField: JTextField? = null
    private var ignoredNamesTextArea: JBTextArea? = null
    private var filtersEnabledCheckBox: JBCheckBox? = null
    private var includePathPrefixCheckBox: JBCheckBox? = null
    private var includeDirectoryStructureCheckBox: JBCheckBox? = null
    private var includeFilesInStructureCheckBox: JBCheckBox? = null
    private var includeRepositorySummaryCheckBox: JBCheckBox? = null
    private var includeLineNumbersCheckBox: JBCheckBox? = null
    private var outputFormatComboBox: JComboBox<String>? = null

    override fun createComponent(): JComponent? {
        settingsPanel = JPanel(GridBagLayout())
        val gbc = createGridBagConstraints()

        addFileLimitsPanel(gbc)
        addPathPrefixToggle(gbc)
        addLineNumbersToggle(gbc)
        addDirectoryStructureToggles(gbc)
        addRepositorySummaryToggle(gbc)
        addOutputFormatDropdown(gbc)
        addFiltersPanel(gbc)
        addFiltersEnableToggle(gbc)
        addFiltersNote(gbc)
        addFiltersTable(gbc)
        addIgnoredNamesPanel(gbc)

        // Add a filler component to push everything to the top
        gbc.gridy++
        gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.BOTH
        settingsPanel!!.add(JPanel(), gbc) // Empty panel acts as filler

        reset() // Load initial values
        return settingsPanel
    }

    private fun createGridBagConstraints() = GridBagConstraints().apply {
        gridx = 0
        gridy = 0
        gridwidth = 2 // Span across two columns initially
        fill = GridBagConstraints.HORIZONTAL
        anchor = GridBagConstraints.NORTHWEST
        weightx = 1.0 // Allow horizontal expansion
        weighty = 0.0 // Don't allow vertical expansion by default
        insets = JBUI.insets(5)
    }

    // --- Panel Creation Methods (Mostly unchanged, ensure components are initialized) ---

    private fun addFileLimitsPanel(gbc: GridBagConstraints) {
        val limitsPanel = JPanel(GridLayout(2, 1, 0, 5)) // Use GridLayout for alignment

        val fileCountPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0)) // Tighter layout
        val fileCountLabel = JLabel("Maximum number of files to process:")
        fileCountLabel.toolTipText = "Sets the upper limit for the number of files that will be processed and copied."
        fileCountSpinner = JSpinner(SpinnerNumberModel(50, 1, Int.MAX_VALUE, 1))
        fileCountPanel.add(fileCountLabel)
        fileCountPanel.add(fileCountSpinner)
        limitsPanel.add(fileCountPanel)

        val fileSizePanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0)) // Tighter layout
        val fileSizeLabel = JLabel("Maximum file size to process (KB):")
        fileSizeLabel.toolTipText = "Skips files larger than this size (in Kilobytes)."
        maxFileSizeSpinner = JSpinner(SpinnerNumberModel(100, 1, Int.MAX_VALUE, 1))
        fileSizePanel.add(fileSizeLabel)
        fileSizePanel.add(maxFileSizeSpinner)
        limitsPanel.add(fileSizePanel)

        settingsPanel!!.add(limitsPanel, gbc)
        gbc.gridy++
    }

    private fun addPathPrefixToggle(gbc: GridBagConstraints) {
        includePathPrefixCheckBox = JBCheckBox("Include '// filename: path' prefix in output")
        includePathPrefixCheckBox!!.toolTipText = "If checked, each file's content will be preceded by a comment with its relative path."
        settingsPanel!!.add(includePathPrefixCheckBox, gbc)
        gbc.gridy++
    }

    private fun addLineNumbersToggle(gbc: GridBagConstraints) {
        includeLineNumbersCheckBox = JBCheckBox("Include line numbers in copied code")
        includeLineNumbersCheckBox!!.toolTipText = "If checked, line numbers will be added to the beginning of each line in the copied code."
        settingsPanel!!.add(includeLineNumbersCheckBox, gbc)
        gbc.gridy++
    }

    private fun addDirectoryStructureToggles(gbc: GridBagConstraints) {
        val structurePanel = JPanel(GridLayout(2, 1, 0, 5))

        includeDirectoryStructureCheckBox = JBCheckBox("Include directory structure")
        includeDirectoryStructureCheckBox!!.toolTipText = "If checked, a text-based tree representation of the directory structure will be included at the beginning of the output."
        structurePanel.add(includeDirectoryStructureCheckBox)

        includeFilesInStructureCheckBox = JBCheckBox("Include files in directory structure")
        includeFilesInStructureCheckBox!!.toolTipText = "If checked, files will be included in the directory structure tree. Only applies if 'Include directory structure' is enabled."
        structurePanel.add(includeFilesInStructureCheckBox)

        settingsPanel!!.add(structurePanel, gbc)
        gbc.gridy++
    }

    private fun addRepositorySummaryToggle(gbc: GridBagConstraints) {
        includeRepositorySummaryCheckBox = JBCheckBox("Include repository summary")
        includeRepositorySummaryCheckBox!!.toolTipText = "If checked, a summary of repository statistics will be included at the beginning of the output."
        settingsPanel!!.add(includeRepositorySummaryCheckBox, gbc)
        gbc.gridy++
    }


    private fun addOutputFormatDropdown(gbc: GridBagConstraints) {
        val formatPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
        val formatLabel = JLabel("Output Format:")
        formatLabel.toolTipText = "Select the format for the exported source code."

        outputFormatComboBox = JComboBox<String>().apply {
            addItem("Plain Text (// filename:)")
            addItem("Markdown (```lang)")
            addItem("XML (machine-readable)")
            toolTipText = "<html>Plain Text: Simple format with filename comments<br>" +
                         "Markdown: Code blocks with syntax highlighting<br>" +
                         "XML: Machine-readable format for tool integration</html>"
        }

        formatPanel.add(formatLabel)
        formatPanel.add(outputFormatComboBox)

        // Add Preview Export button
        val previewButton = createStyledButton("Preview Export")
        previewButton.toolTipText = "Preview what will be exported with current settings"
        previewButton.addActionListener { showExportPreview() }
        formatPanel.add(previewButton)

        settingsPanel!!.add(formatPanel, gbc)
        gbc.gridy++
    }

    private fun addFiltersPanel(gbc: GridBagConstraints) {
        val filtersInputPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0)) // Tighter layout
        val filterLabel = JLabel("Include files matching extensions (e.g., java, .kt):")
        filterLabel.toolTipText = "Add file extensions (with or without leading dot) to include. If list is empty, all non-binary files are considered (respecting size/ignore limits)."
        addFilterTextField = createStyledTextField("java or .kt")
        val addButton = createStyledButton("Add Filter")
        addButton.addActionListener { addFilter() }

        filtersInputPanel.add(filterLabel)
        filtersInputPanel.add(addFilterTextField)
        filtersInputPanel.add(addButton)

        settingsPanel!!.add(filtersInputPanel, gbc)
        gbc.gridy++
    }

    private fun addFiltersEnableToggle(gbc: GridBagConstraints) {
        filtersEnabledCheckBox = JBCheckBox("Enable file extension filters")
        filtersEnabledCheckBox!!.toolTipText = "When enabled, only files matching the filters below are included. If the list is empty, no files are filtered out."
        settingsPanel!!.add(filtersEnabledCheckBox, gbc)
        gbc.gridy++
    }

    private fun addFiltersNote(gbc: GridBagConstraints) {
        val note = JLabel(
            "Note: If enabled and the list is empty, no filter is applied — all non-binary files are considered (respecting size and ignore settings)."
        )
        note.foreground = UIManager.getColor("Label.disabledForeground") ?: note.foreground
        settingsPanel!!.add(note, gbc)
        gbc.gridy++
    }

    private fun addFiltersTable(gbc: GridBagConstraints) {
        gbc.fill = GridBagConstraints.BOTH // Allow table to resize
        gbc.weighty = 0.3 // Give table some vertical space preference

        filtersTableModel = DefaultTableModel(arrayOf("Filter", "Action"), 0)
        filtersTable = JBTable(filtersTableModel).apply {
            // Make table non-editable except for the button column interaction
            setDefaultEditor(Object::class.java, null)
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            preferredScrollableViewportSize = Dimension(450, 100) // Suggest initial size
            columnModel.getColumn(0).preferredWidth = 350
            columnModel.getColumn(1).preferredWidth = 100
            columnModel.getColumn(1).maxWidth = 120

            // Add the remove button column using the helper
            TableButtonColumn.add(this, 1, "Remove") { row ->
                filtersTableModel?.takeIf { row >= 0 && it.rowCount > row }?.removeRow(row)
            }
        }

        val scrollPane = JBScrollPane(filtersTable)
        settingsPanel!!.add(scrollPane, gbc)
        gbc.gridy++
        gbc.weighty = 0.0 // Reset weighty for next components
    }

    private fun addIgnoredNamesPanel(gbc: GridBagConstraints) {
        val ignoredPanel = JPanel(BorderLayout(0, 5)) // Use BorderLayout

        val ignoredLabel = JLabel("Ignored file/directory names (one per line):")
        ignoredLabel.toolTipText = "Files or directories with these exact names will be skipped entirely."
        ignoredPanel.add(ignoredLabel, BorderLayout.NORTH)

        ignoredNamesTextArea = JBTextArea().apply {
            rows = 4
            lineWrap = true
            wrapStyleWord = true
            // Consider adding border for clarity
            border = BorderFactory.createCompoundBorder(
                UIManager.getBorder("TextField.border"), // Match look and feel
                JBUI.Borders.empty(2) // Add inner padding
            )
        }
        val scrollPane = JBScrollPane(ignoredNamesTextArea)
        scrollPane.preferredSize = Dimension(450, 80) // Suggest initial size
        ignoredPanel.add(scrollPane, BorderLayout.CENTER)

        gbc.fill = GridBagConstraints.BOTH // Allow text area to resize
        gbc.weighty = 0.7 // Give text area more vertical space preference
        settingsPanel!!.add(ignoredPanel, gbc)
        // gbc.gridy++ // No need to increment gridy if it's the last element before the filler
    }

    // --- Action Handlers and Logic ---

    private fun addFilter() {
        val filterText = addFilterTextField!!.text.trim()
        if (filterText.isEmpty()) return

        if (StringUtils.isValidFilterFormat(filterText) && !filterExists(filterText)) {
            filtersTableModel!!.addRow(arrayOf(filterText, "Remove")) // "Remove" text is handled by renderer now
            addFilterTextField!!.text = "" // Clear input field
        } else if (!StringUtils.isValidFilterFormat(filterText)) {
            JOptionPane.showMessageDialog(
                settingsPanel,
                "Invalid filter format. Filters should be like '.java' or 'kt' (alphanumeric, optionally starting with a dot).",
                "Invalid Input",
                JOptionPane.ERROR_MESSAGE
            )
        } else {
            JOptionPane.showMessageDialog(
                settingsPanel,
                "Filter '$filterText' already exists.",
                "Duplicate Filter",
                JOptionPane.WARNING_MESSAGE
            )
        }
    }

    private fun filterExists(filterText: String): Boolean {
        return (0 until filtersTableModel!!.rowCount).any {
            filtersTableModel!!.getValueAt(it, 0) as String == filterText
        }
    }

    override fun isModified(): Boolean {
        val settings = SourceClipboardExportSettings.getInstance().state
        val currentFilters = (0 until filtersTableModel!!.rowCount).map {
            filtersTableModel!!.getValueAt(it, 0) as String
        }.toMutableList() // Ensure it's mutable for comparison if needed, though list comparison works
        val currentIgnoredNames = ignoredNamesTextArea?.text?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toMutableList() ?: mutableListOf()

        val selectedFormatIndex = outputFormatComboBox?.selectedIndex ?: 0
        val currentOutputFormat = when (selectedFormatIndex) {
            0 -> OutputFormat.PLAIN_TEXT
            1 -> OutputFormat.MARKDOWN
            2 -> OutputFormat.XML
            else -> OutputFormat.PLAIN_TEXT
        }

        // Compare each setting
        return fileCountSpinner!!.value != settings.fileCount ||
               maxFileSizeSpinner!!.value != settings.maxFileSizeKb ||
               includePathPrefixCheckBox!!.isSelected != settings.includePathPrefix ||
               includeDirectoryStructureCheckBox!!.isSelected != settings.includeDirectoryStructure ||
               includeFilesInStructureCheckBox!!.isSelected != settings.includeFilesInStructure ||
               includeRepositorySummaryCheckBox!!.isSelected != settings.includeRepositorySummary ||
               includeLineNumbersCheckBox!!.isSelected != settings.includeLineNumbers ||
               filtersEnabledCheckBox!!.isSelected != settings.areFiltersEnabled ||
               currentFilters != settings.filenameFilters || // Direct list comparison
               currentIgnoredNames != settings.ignoredNames || // Direct list comparison
               currentOutputFormat != settings.outputFormat
    }

    override fun apply() {
        if (!validateInput()) return // Perform validation before applying

        val settings = SourceClipboardExportSettings.getInstance().state // Get state directly
        settings.fileCount = fileCountSpinner!!.value as Int
        settings.maxFileSizeKb = maxFileSizeSpinner!!.value as Int
        settings.includePathPrefix = includePathPrefixCheckBox!!.isSelected
        settings.includeDirectoryStructure = includeDirectoryStructureCheckBox!!.isSelected
        settings.includeFilesInStructure = includeFilesInStructureCheckBox!!.isSelected
        settings.includeRepositorySummary = includeRepositorySummaryCheckBox!!.isSelected
        settings.includeLineNumbers = includeLineNumbersCheckBox!!.isSelected
        settings.areFiltersEnabled = filtersEnabledCheckBox!!.isSelected

        // Update output format from dropdown
        val selectedFormatIndex = outputFormatComboBox?.selectedIndex ?: 0
        settings.outputFormat = when (selectedFormatIndex) {
            0 -> OutputFormat.PLAIN_TEXT
            1 -> OutputFormat.MARKDOWN
            2 -> OutputFormat.XML
            else -> OutputFormat.PLAIN_TEXT
        }

        settings.filenameFilters = (0 until filtersTableModel!!.rowCount).map {
            filtersTableModel!!.getValueAt(it, 0) as String
        }.toMutableList() // Create new list
        settings.ignoredNames = ignoredNamesTextArea?.text?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toMutableList() ?: mutableListOf() // Create new list

        LOGGER.debug("Applying settings: File count = ${settings.fileCount}, Max Size KB = ${settings.maxFileSizeKb}, " +
                "Include Prefix = ${settings.includePathPrefix}, Include Directory Structure = ${settings.includeDirectoryStructure}, " +
                "Include Files in Structure = ${settings.includeFilesInStructure}, Include Repository Summary = ${settings.includeRepositorySummary}, " +
                "Include Line Numbers = ${settings.includeLineNumbers}, " +
                "Output Format = ${settings.outputFormat}, " +
                "Filters = ${settings.filenameFilters.joinToString()}, " +
                "Ignored = ${settings.ignoredNames.joinToString()}")
    }

    override fun reset() {
        val settings = SourceClipboardExportSettings.getInstance().state // Get state directly
        fileCountSpinner!!.value = settings.fileCount
        maxFileSizeSpinner!!.value = settings.maxFileSizeKb
        includePathPrefixCheckBox!!.isSelected = settings.includePathPrefix
        includeDirectoryStructureCheckBox!!.isSelected = settings.includeDirectoryStructure
        includeFilesInStructureCheckBox!!.isSelected = settings.includeFilesInStructure
        includeRepositorySummaryCheckBox!!.isSelected = settings.includeRepositorySummary
        includeLineNumbersCheckBox!!.isSelected = settings.includeLineNumbers
        filtersEnabledCheckBox!!.isSelected = settings.areFiltersEnabled

        // Set the output format dropdown
        val formatIndex = when (settings.outputFormat) {
            OutputFormat.PLAIN_TEXT -> 0
            OutputFormat.MARKDOWN -> 1
            OutputFormat.XML -> 2
        }
        outputFormatComboBox!!.selectedIndex = formatIndex

        // Clear and repopulate table model
        filtersTableModel!!.rowCount = 0
        settings.filenameFilters.forEach { filter ->
            filtersTableModel!!.addRow(arrayOf(filter, "Remove")) // "Remove" text handled by renderer
        }

        ignoredNamesTextArea!!.text = settings.ignoredNames.joinToString("\n")

        LOGGER.debug("Resetting settings UI to: File count = ${settings.fileCount}, Max Size KB = ${settings.maxFileSizeKb}, " +
                "Include Prefix = ${settings.includePathPrefix}, Include Directory Structure = ${settings.includeDirectoryStructure}, " +
                "Include Files in Structure = ${settings.includeFilesInStructure}, Include Repository Summary = ${settings.includeRepositorySummary}, " +
                "Include Line Numbers = ${settings.includeLineNumbers}, " +
                "Output Format = ${settings.outputFormat}, " +
                "Filters = ${settings.filenameFilters.joinToString()}, " +
                "Ignored = ${settings.ignoredNames.joinToString()}")
    }

    override fun getDisplayName(): String {
        return "Source Clipboard Export" // Consistent naming
    }

    private fun validateInput(): Boolean {
        // Validate spinners (already constrained by SpinnerNumberModel, but good practice)
        val fileCount = fileCountSpinner?.value as? Int ?: 0
        if (fileCount <= 0) {
            JOptionPane.showMessageDialog(settingsPanel, "File count must be a positive integer.", "Invalid Input", JOptionPane.ERROR_MESSAGE)
            return false
        }

        val maxFileSize = maxFileSizeSpinner?.value as? Int ?: 0
        if (maxFileSize <= 0) {
            JOptionPane.showMessageDialog(settingsPanel, "Maximum file size (KB) must be a positive integer.", "Invalid Input", JOptionPane.ERROR_MESSAGE)
            return false
        }

        // Validate filters in the table (redundant if addFilter validates, but safe)
        val invalidFilters = (0 until filtersTableModel!!.rowCount)
            .map { filtersTableModel!!.getValueAt(it, 0) as String }
            .filter { !StringUtils.isValidFilterFormat(it) }

        if (invalidFilters.isNotEmpty()) {
            JOptionPane.showMessageDialog(settingsPanel, "Invalid filters found in table: ${invalidFilters.joinToString(", ")}\nFilters should be like '.java' or 'kt'.", "Invalid Input", JOptionPane.ERROR_MESSAGE)
            return false
        }

        // Optionally validate ignored names (e.g., check for empty lines if they cause issues)
        // val ignoredNames = ignoredNamesTextArea?.text?.lines()?.map { it.trim() } ?: emptyList()
        // if (ignoredNames.any { it.isEmpty() && ignoredNames.size > 1 }) { ... }

        return true // All checks passed
    }

    // --- Helper Methods ---

    private fun createStyledTextField(placeholder: String): JTextField {
        return PlaceholderTextField(placeholder).apply {
            preferredSize = Dimension(150, preferredSize.height) // Set preferred width
        }
    }

    private fun createStyledButton(text: String): JButton {
        return JButton(text) // Standard JButton is usually fine
    }

    private fun showExportPreview() {
        val previewSettings = ExportPreviewGenerator.PreviewSettings(
            maxFiles = fileCountSpinner?.value as? Int ?: 0,
            maxFileSizeKb = maxFileSizeSpinner?.value as? Int ?: 0,
            includePathPrefix = includePathPrefixCheckBox?.isSelected ?: false,
            includeLineNumbers = includeLineNumbersCheckBox?.isSelected ?: false,
            includeDirectoryStructure = includeDirectoryStructureCheckBox?.isSelected ?: false,
            includeFilesInStructure = includeFilesInStructureCheckBox?.isSelected ?: false,
            includeRepositorySummary = includeRepositorySummaryCheckBox?.isSelected ?: false,
            outputFormat = selectedOutputFormat(),
            filters = currentFilters(),
            ignoredNames = currentIgnoredNames()
        )

        val previewText = ExportPreviewGenerator().buildPreview(previewSettings)

        val textArea = JBTextArea(previewText).apply {
            isEditable = false
            font = UIManager.getFont("EditorPane.font") ?: font
        }
        val scrollPane = JBScrollPane(textArea).apply {
            preferredSize = Dimension(600, 400)
        }
        
        JOptionPane.showMessageDialog(
            settingsPanel,
            scrollPane,
            "Export Preview",
            JOptionPane.INFORMATION_MESSAGE
        )
    }

    private fun selectedOutputFormat(): OutputFormat {
        return when (outputFormatComboBox?.selectedIndex ?: 0) {
            1 -> OutputFormat.MARKDOWN
            2 -> OutputFormat.XML
            else -> OutputFormat.PLAIN_TEXT
        }
    }

    private fun currentFilters(): List<String> {
        val rows = filtersTableModel?.rowCount ?: 0
        if (rows == 0) return emptyList()
        return (0 until rows).mapNotNull { idx ->
            (filtersTableModel?.getValueAt(idx, 0) as? String)?.takeIf { it.isNotBlank() }
        }
    }

    private fun currentIgnoredNames(): List<String> {
        return ignoredNamesTextArea?.text?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
    }

    companion object {
        private val LOGGER = Logger.getInstance(SourceClipboardExportConfigurable::class.java)
    }
} 
