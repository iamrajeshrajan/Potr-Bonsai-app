package `in`.potr.bonsai

import android.app.Application
import `in`.potr.bonsai.cloud.UpdateChecker
import `in`.potr.bonsai.data.local.BonsaiDatabase
import `in`.potr.bonsai.data.local.InitialSeedData
import `in`.potr.bonsai.data.local.SecurePrefs
import `in`.potr.bonsai.data.repository.BonsaiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PotrBonsaiApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: BonsaiDatabase
        private set

    lateinit var prefs: SecurePrefs
        private set

    lateinit var repository: BonsaiRepository
        private set

    val updateChecker: UpdateChecker = UpdateChecker(
        repoOwner = "iamrajeshrajan",
        repoName = "Potr-Bonsai-app"
    )

    override fun onCreate() {
        super.onCreate()
        database = BonsaiDatabase.getInstance(this)
        prefs = SecurePrefs(this)
        repository = BonsaiRepository(
            context = this,
            dao = database.bonsaiDao(),
            prefs = prefs
        )
        appScope.launch {
            InitialSeedData.seedIfEmpty(database.bonsaiDao())
        }
    }
}
