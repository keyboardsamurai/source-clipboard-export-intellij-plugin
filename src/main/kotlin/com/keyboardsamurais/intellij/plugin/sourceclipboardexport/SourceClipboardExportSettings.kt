import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(
    name = "SourceClipboardExportSettings",
    storages = [Storage("SourceClipboardExportSettings.xml")]
)
class SourceClipboardExportSettings : PersistentStateComponent<SourceClipboardExportSettings.State> {
    class State {
        var fileCount: Int = 50
        var filenameFilters: MutableList<String> = mutableListOf()
        var areFiltersEnabled: Boolean = true
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(): SourceClipboardExportSettings {
            return ApplicationManager.getApplication().getService(SourceClipboardExportSettings::class.java)
        }
    }
}
