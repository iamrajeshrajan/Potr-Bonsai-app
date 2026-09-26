package `in`.potr.bonsai.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import `in`.potr.bonsai.data.AppState
import `in`.potr.bonsai.data.Session
import `in`.potr.bonsai.ui.screens.BonsaiDetailScreen
import `in`.potr.bonsai.ui.screens.BonsaiFormScreen
import `in`.potr.bonsai.ui.screens.HomeScreen
import `in`.potr.bonsai.ui.screens.LogCareScreen
import `in`.potr.bonsai.ui.screens.PotDetailScreen
import `in`.potr.bonsai.ui.screens.PotFormScreen
import `in`.potr.bonsai.ui.screens.QrCardScreen
import `in`.potr.bonsai.ui.screens.SetupScreen

val LocalSession = staticCompositionLocalOf<Session> { error("Not signed in") }

object Routes {
    const val HOME = "home"
    const val BONSAI_NEW = "new-bonsai"
    fun bonsai(ref: String) = "bonsai/$ref"
    fun bonsaiEdit(id: String) = "bonsai/$id/edit"
    fun logCare(id: String, taskId: String? = null) = "bonsai/$id/log" + (taskId?.let { "?task=$it" } ?: "")
    const val POT_NEW = "new-pot"
    fun pot(ref: String) = "pot/$ref"
    fun potEdit(id: String) = "pot/$id/edit"
    fun qr(code: String) = "qr/$code"
}

@Composable
fun PotrApp(state: AppState, pendingLink: String?, onLinkHandled: () -> Unit) {
    val session = state.session
    if (session == null) {
        SetupScreen(state)
        return
    }
    CompositionLocalProvider(LocalSession provides session) {
        val nav = rememberNavController()
        val context = LocalContext.current
        LaunchedEffect(pendingLink) {
            if (pendingLink == null) return@LaunchedEffect
            openScanned(session, nav, pendingLink) { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
            onLinkHandled()
        }
        NavHost(nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) { HomeScreen(nav, onSignOut = state::signOut) }
            composable(Routes.BONSAI_NEW) { BonsaiFormScreen(nav, bonsaiId = null) }
            composable("bonsai/{ref}") { BonsaiDetailScreen(nav, it.arguments?.getString("ref")!!) }
            composable("bonsai/{id}/edit") { BonsaiFormScreen(nav, bonsaiId = it.arguments?.getString("id")) }
            composable(
                "bonsai/{id}/log?task={task}",
                arguments = listOf(navArgument("task") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) {
                LogCareScreen(nav, it.arguments?.getString("id")!!, taskId = it.arguments?.getString("task"))
            }
            composable(Routes.POT_NEW) { PotFormScreen(nav, potId = null) }
            composable("pot/{ref}") { PotDetailScreen(nav, it.arguments?.getString("ref")!!) }
            composable("pot/{id}/edit") { PotFormScreen(nav, potId = it.arguments?.getString("id")) }
            composable("qr/{code}") { QrCardScreen(nav, it.arguments?.getString("code")!!) }
        }
    }
}

/** Resolves a scanned QR / opened link to a bonsai or pot and navigates to it. */
suspend fun openScanned(session: Session, nav: NavHostController, scanned: String, onError: (String) -> Unit) {
    try {
        val found = session.api.lookup(scanned)
        nav.navigate(if (found.kind == "pot") Routes.pot(found.id) else Routes.bonsai(found.id))
    } catch (e: Exception) {
        onError(e.message ?: "Not found")
    }
}
