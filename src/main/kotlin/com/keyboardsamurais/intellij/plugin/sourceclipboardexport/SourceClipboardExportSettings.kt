import com.intellij.openapi.components.Service
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(
    name = "SourceClipboardExportSettings",
    storages = [Storage("SourceClipboardExportSettings.xml")]
)
class SourceClipboardExportSettings : PersistentStateComponent<SourceClipboardExportSettings.State> {
    class State {
        var fileCount: Int = 50
        var filenameFilters: MutableList<String> = mutableListOf() // List of filters
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(project: Project): SourceClipboardExportSettings {
            return project.getService(SourceClipboardExportSettings::class.java)
        }
    }
}
