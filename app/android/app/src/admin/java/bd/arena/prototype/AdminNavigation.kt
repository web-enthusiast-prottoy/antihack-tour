package bd.arena.prototype

import androidx.activity.compose.BackHandler
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.DocumentSnapshot

internal val depositCollection = if (BuildConfig.LOCAL_OPERATIONS) "depositRequests" else "walletDeposits"

internal enum class AdminPage(val title: String, val destination: AdminDestination, val path: String = "") {
    Overview("Overview", AdminDestination.Overview),
    Matches("Matches", AdminDestination.Matches, "matches"),
    Results("Results", AdminDestination.Matches, "results"),
    Schedules("Schedules", AdminDestination.Matches),
    Finance("Finance", AdminDestination.Finance),
    Deposits("Deposits", AdminDestination.Finance, depositCollection),
    Withdrawals("Withdrawals", AdminDestination.Finance, "withdrawals"),
    People("Players", AdminDestination.Players),
    Players("Player records", AdminDestination.Players, "users"),
    Support("Support", AdminDestination.Players),
    Settings("Settings", AdminDestination.Settings),
    WhatsApp("WhatsApp support", AdminDestination.Settings),
    PaymentNumber("Payment settings", AdminDestination.Settings),
    WithdrawalPolicy("Withdrawal settings", AdminDestination.Settings),
    Notices("Announcements", AdminDestination.Settings, "notices"),
    Categories("Categories", AdminDestination.Settings),
    PlayerPages("Player pages", AdminDestination.Settings, "playerPages"),
    Content("App content", AdminDestination.Settings, "configuration"),
    Staff("Staff", AdminDestination.Settings, "staffRoles"),
    Audit("Audit", AdminDestination.Settings, "auditRecords")
}

private fun AdminDestination.root() = when (this) {
    AdminDestination.Overview -> AdminPage.Overview
    AdminDestination.Matches -> AdminPage.Matches
    AdminDestination.Finance -> AdminPage.Finance
    AdminDestination.Players -> AdminPage.People
    AdminDestination.Settings -> AdminPage.Settings
}

@Composable
internal fun NativeAdmin(session: AdminSession, bangla: Boolean, language: () -> Unit, signOut: () -> Unit) {
    StartAdminMatchScheduler(session)
    var smsRefresh by remember { mutableIntStateOf(0) }
    val smsSettings = rememberSmsDepositSettings(session, bangla) { smsRefresh++ }
    var page by rememberSaveable { mutableStateOf(AdminPage.Overview) }
    var dashboardMatch by remember { mutableStateOf<DocumentSnapshot?>(null) }
    fun open(next: AdminPage) {
        dashboardMatch = null
        page = next
    }
    val back = {
        val root = page.destination.root()
        open(if (dashboardMatch != null) AdminPage.Overview else if (root == page) AdminPage.Overview else root)
    }
    BackHandler(page != AdminPage.Overview || dashboardMatch != null) { back() }
    LaunchedEffect(session.owner, page) { if (!session.owner && page == AdminPage.Staff) open(AdminPage.Settings) }

    AdminWorkspace(page.destination, { open(it.root()) }) {
        val match = dashboardMatch
        if (match != null) {
            MatchAdminScreen(session, match, back)
        } else {
            key(page) {
                when (page) {
                    AdminPage.Overview -> AdminDashboard(session, ::open) {
                        page = AdminPage.Matches
                        dashboardMatch = it
                    }
                    AdminPage.Finance -> AdminLanding("Finance") {
                        AdminGroup("Review queues") { AdminLinks(listOf(AdminPage.Deposits, AdminPage.Withdrawals), ::open) }
                    }
                    AdminPage.People -> AdminLanding("Players") {
                        AdminGroup("Players & support") { AdminLinks(listOf(AdminPage.Players, AdminPage.Support), ::open) }
                    }
                    AdminPage.Settings -> AdminLanding("Settings") {
                        AdminGroup("Payments") { AdminLinks(listOf(AdminPage.PaymentNumber, AdminPage.WithdrawalPolicy), ::open) }
                        AdminGroup("Announcements") {
                            AdminRow(adminText("Manage announcements")) { open(AdminPage.Notices) }
                        }
                        AdminGroup("Support") { AdminLinks(listOf(AdminPage.WhatsApp), ::open) }
                        AdminGroup("Content") {
                            AdminLinks(listOf(AdminPage.Categories, AdminPage.PlayerPages, AdminPage.Content), ::open)
                        }
                        AdminGroup("Access & activity") {
                            AdminLinks(listOf(AdminPage.Audit), ::open)
                        }
                        AdminGroup("Account") {
                            AdminRow(adminText("Language"), if (bangla) "বাংলা" else "English", open = language)
                            HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                            AdminRow(adminText("Sign out"), session.auth.currentUser?.email, open = signOut)
                        }
                    }
                    AdminPage.Schedules -> ScheduledMatchesScreen(session,back){dashboardMatch=it}
                    AdminPage.WhatsApp -> SupportSettings(session, bangla, back)
                    AdminPage.PaymentNumber -> PaymentSettings(session, bangla, back, smsSettings)
                    AdminPage.WithdrawalPolicy -> WithdrawalSettings(session, back)
                    AdminPage.Categories -> CategoriesAdmin(session, back)
                    AdminPage.Support -> AdminSupport(session, bangla, back)
                    AdminPage.Staff -> if (session.owner) AdminRecords(session, page, bangla, back, ::open)
                    else -> AdminRecords(session, page, bangla, back, ::open, if (page == AdminPage.Deposits) smsRefresh else 0)
                }
            }
        }
    }
}

@Composable
private fun AdminLinks(pages: List<AdminPage>, open: (AdminPage) -> Unit) {
    pages.forEachIndexed { index, item ->
        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp))
        AdminRow(adminText(item.title)) { open(item) }
    }
}
