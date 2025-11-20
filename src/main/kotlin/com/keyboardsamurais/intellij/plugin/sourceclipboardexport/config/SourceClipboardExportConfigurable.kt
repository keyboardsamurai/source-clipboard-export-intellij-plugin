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

/**
 * Implements IntelliJ's [Configurable] interface to expose plugin settings under *Tools >
 * Source Clipboard Export*. The UI mirrors the backing [SourceClipboardExportSettings.State] so
 * users can configure file limits, formatting, filters, and previews without leaving the IDE.
 *
 * Because IntelliJ instantiates configurables declaratively via `plugin.xml`, this class focuses on
 * building Swing components and syncing them with the settings service.
 */
class SourceClipboardExportConfigurable : Configurable {
    private var settingsPanel: JPanel? = null
    private var fileCountSpinner: JSpinner? = null
    private var maxFileSizeSpinner: JSpinner? = null
    private var filtersTableModel: DefaultTableModel? = null
    private var filtersTable: JBTable? = null
    private var addFilterTextField: JTextField? = null
    private var addFilterButton: JButton? = null
    private var ignoredNamesTextArea: JBTextArea? = null
    private var filtersEnabledCheckBox: JBCheckBox? = null
    private var includePathPrefixCheckBox: JBCheckBox? = null
    private var includeDirectoryStructureCheckBox: JBCheckBox? = null
    private var includeFilesInStructureCheckBox: JBCheckBox? = null
    private var includeRepositorySummaryCheckBox: JBCheckBox? = null
    private var includeLineNumbersCheckBox: JBCheckBox? = null
    private var outputFormatComboBox: JComboBox<String>? = null
    private val filterControlComponents = mutableListOf<JComponent>()

    /**
     * Constructs the settings UI tree on demand. Called whenever IntelliJ opens the Settings
     * dialog, so the UI should be fast to instantiate and free of side effects.
     */
    override fun createComponent(): JComponent? {
        settingsPanel = JPanel(GridBagLayout())
        val gbc = createGridBagConstraints()

        addSection(createScopeAndLimitsSection(), gbc)
        addSection(createContentSection(), gbc)
        addSection(createOutputFormatSection(), gbc)
        addSection(createFiltersSection(), gbc)
        addSection(createIgnoredNamesSection(), gbc, weightY = 0.5, fill = GridBagConstraints.BOTH)

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

    private fun addSection(component: JComponent, gbc: GridBagConstraints, weightY: Double = 0.0, fill: Int = GridBagConstraints.HORIZONTAL) {
        gbc.weighty = weightY
        gbc.fill = fill
        settingsPanel!!.add(component, gbc)
        gbc.gridy++
        gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.HORIZONTAL
    }

    // --- Panel Creation Methods ---

    private fun createScopeAndLimitsSection(): JComponent {
        val panel = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createTitledBorder("Scope & Limits")
        }

        val innerGbc = GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.NORTHWEST
            insets = JBUI.insets(4, 4, 4, 4)
        }

        val fileCountRow = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
        val fileCountLabel = JLabel("Maximum number of files to process:")
        fileCountLabel.toolTipText = "Sets the upper limit for how many files can be exported in a single run."
        fileCountSpinner = JSpinner(SpinnerNumberModel(50, 1, Int.MAX_VALUE, 1))
        fileCountRow.add(fileCountLabel)
        fileCountRow.add(fileCountSpinner)
        panel.add(fileCountRow, innerGbc)

        innerGbc.gridy++
        panel.add(createHintLabel("Higher values may slow down the export on very large projects."), innerGbc)

        innerGbc.gridy++
        val fileSizeRow = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
        val fileSizeLabel = JLabel("Maximum file size per file (KB):")
        fileSizeLabel.toolTipText = "Files larger than this limit (in kilobytes) are skipped."
        maxFileSizeSpinner = JSpinner(SpinnerNumberModel(100, 1, Int.MAX_VALUE, 1))
        fileSizeRow.add(fileSizeLabel)
        fileSizeRow.add(maxFileSizeSpinner)
        panel.add(fileSizeRow, innerGbc)

        innerGbc.gridy++
        panel.add(createHintLabel("Use lower limits for quick exports; raise them when you need more context."), innerGbc)

        return panel
    }

    private fun createContentSection(): JComponent {
        val container = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("Content Included in Export")
        }

        val contentPanel = JPanel(GridLayout(0, 1, 4, 4))
        includePathPrefixCheckBox = JBCheckBox("Add \"// filename: path\" before each file")
        includePathPrefixCheckBox!!.toolTipText = "Adds the relative file path as a comment above each block of code."
        includeLineNumbersCheckBox = JBCheckBox("Include line numbers in exported code")
        includeLineNumbersCheckBox!!.toolTipText = "Prefixes every line in the export with its line number."
        includeDirectoryStructureCheckBox = JBCheckBox("Include directory tree listing")
        includeDirectoryStructureCheckBox!!.toolTipText = "Prepends a directory tree showing the exported scope."
        includeFilesInStructureCheckBox = JBCheckBox("Include file list under each directory")
        includeFilesInStructureCheckBox!!.toolTipText = "Lists files beneath each directory in the tree. Only applies when the tree is included."
        includeRepositorySummaryCheckBox = JBCheckBox("Include repository summary")
        includeRepositorySummaryCheckBox!!.toolTipText = "Adds repository statistics before the exported files."

        contentPanel.add(includePathPrefixCheckBox)
        contentPanel.add(includeLineNumbersCheckBox)
        contentPanel.add(includeDirectoryStructureCheckBox)
        contentPanel.add(includeFilesInStructureCheckBox)
        contentPanel.add(includeRepositorySummaryCheckBox)

        container.add(contentPanel, BorderLayout.CENTER)
        container.add(createHintLabel("Toggle the extra context that accompanies copied code."), BorderLayout.SOUTH)
        return container
    }

    private fun createOutputFormatSection(): JComponent {
        val container = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("Output Format & Preview")
        }

        val formatPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
        val formatLabel = JLabel("Output format:")
        formatLabel.toolTipText = "Choose how the exported text should be structured."

        outputFormatComboBox = JComboBox<String>().apply {
            addItem("Plain text with filename headers")
            addItem("Markdown code blocks")
            addItem("XML (machine-readable)")
            toolTipText = "<html>Plain text: Includes \"// filename\" comments.<br>" +
                "Markdown: Wraps files in Markdown code fences.<br>" +
                "XML: Provides structured output for tooling.</html>"
        }

        formatPanel.add(formatLabel)
        formatPanel.add(outputFormatComboBox)

        val previewButton = createStyledButton("Preview Output…")
        previewButton.toolTipText = "Open a modal preview using the current settings."
        previewButton.addActionListener { showExportPreview() }
        formatPanel.add(previewButton)

        container.add(formatPanel, BorderLayout.NORTH)
        container.add(createHintLabel("Preview to verify formatting before copying."), BorderLayout.SOUTH)
        return container
    }

    private fun createFiltersSection(): JComponent {
        val container = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createTitledBorder("File Extension Filters (Advanced)")
        }

        val innerGbc = GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.NORTHWEST
            insets = JBUI.insets(4, 4, 4, 4)
        }

        filtersEnabledCheckBox = JBCheckBox("Enable file extension filters")
        filtersEnabledCheckBox!!.toolTipText = "Restrict exports to files with specific extensions."
        filtersEnabledCheckBox!!.addActionListener { updateFilterControlsEnabledState() }
        container.add(filtersEnabledCheckBox, innerGbc)

        innerGbc.gridy++
        container.add(
            createHintLabel("If disabled, all non-binary files are considered (respecting ignore list and limits)."),
            innerGbc
        )

        innerGbc.gridy++
        val filtersInputPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
        val filterLabel = JLabel("Extensions to include (e.g., .kt, .java):")
        filterLabel.toolTipText = "Add extensions with or without a dot."
        addFilterTextField = createStyledTextField(".kt, .java")
        addFilterButton = createStyledButton("Add")
        addFilterButton!!.addActionListener { addFilter() }

        filtersInputPanel.add(filterLabel)
        filtersInputPanel.add(addFilterTextField)
        filtersInputPanel.add(addFilterButton)

        container.add(filtersInputPanel, innerGbc)

        innerGbc.gridy++
        filtersTableModel = DefaultTableModel(arrayOf("Extension", "Action"), 0)
        filtersTable = JBTable(filtersTableModel).apply {
            setDefaultEditor(Object::class.java, null)
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            preferredScrollableViewportSize = Dimension(450, 100)
            columnModel.getColumn(0).preferredWidth = 350
            columnModel.getColumn(1).preferredWidth = 100
            columnModel.getColumn(1).maxWidth = 120

            TableButtonColumn.add(this, 1, "Remove") { row ->
                filtersTableModel?.takeIf { row >= 0 && it.rowCount > row }?.removeRow(row)
            }
        }
        val scrollPane = JBScrollPane(filtersTable)
        innerGbc.fill = GridBagConstraints.BOTH
        innerGbc.weighty = 0.3
        container.add(scrollPane, innerGbc)

        innerGbc.gridy++
        innerGbc.weighty = 0.0
        innerGbc.fill = GridBagConstraints.HORIZONTAL
        container.add(createHintLabel("Only files with listed extensions are exported."), innerGbc)

        registerFilterControl(addFilterTextField!!)
        registerFilterControl(addFilterButton!!)
        registerFilterControl(filtersTable!!)

        return container
    }

    private fun createIgnoredNamesSection(): JComponent {
        val container = JPanel(BorderLayout(0, 5)).apply {
            border = BorderFactory.createTitledBorder("Ignored Files & Directories")
        }

        val ignoredLabel = JLabel("Ignored file/directory names (one per line):")
        ignoredLabel.toolTipText = "Files or directories with these names are skipped completely."
        container.add(ignoredLabel, BorderLayout.NORTH)

        ignoredNamesTextArea = JBTextArea().apply {
            rows = 4
            lineWrap = true
            wrapStyleWord = true
            border = BorderFactory.createCompoundBorder(
                UIManager.getBorder("TextField.border"),
                JBUI.Borders.empty(2)
            )
        }

        container.add(JBScrollPane(ignoredNamesTextArea), BorderLayout.CENTER)
        container.add(
            createHintLabel("Matches project paths. Add entries like \".git\", \"build\", or \"**/generated\"."),
            BorderLayout.SOUTH
        )
        return container
    }

    private fun registerFilterControl(component: JComponent) {
        filterControlComponents.add(component)
    }

    private fun updateFilterControlsEnabledState() {
        val enabled = filtersEnabledCheckBox?.isSelected ?: false
        filterControlComponents.forEach { it.isEnabled = enabled }
    }

    private fun createHintLabel(text: String): JLabel {
        return JLabel(text).apply {
            foreground = UIManager.getColor("Label.disabledForeground") ?: foreground
        }
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
                "Invalid extension format. Extensions should look like '.java' or 'kt' (letters/numbers, optional leading dot).",
                "Invalid Input",
                JOptionPane.ERROR_MESSAGE
            )
        } else {
            JOptionPane.showMessageDialog(
                settingsPanel,
                "Extension '$filterText' is already listed.",
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

    /**
     * Tells the Settings dialog whether the Apply button should be active. Compares the current UI
     * state to the persisted [SourceClipboardExportSettings.State].
     */
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

    /**
     * Persists UI state back to [SourceClipboardExportSettings]. The dialog calls this when users
     * click *Apply* or *OK*.
     */
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

    /**
     * Resets the UI controls to match [SourceClipboardExportSettings]. Called both when the dialog
     * opens and when the user presses *Reset*.
     */
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

        updateFilterControlsEnabledState()

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
