package com.competra.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.competra.domain.models.OrienteeringCompetition
import com.competra.domain.models.Rating
import com.competra.domain.models.RatingGroup
import com.competra.domain.models.RatingGroupMappingSuggestion
import com.competra.web.pages.AboutPage
import com.competra.web.pages.AddCompetitionToRatingPage
import com.competra.web.pages.ClubDetailPage
import com.competra.web.pages.ClubJoinRequestsPage
import com.competra.web.pages.ClubsListPage
import com.competra.web.pages.CompetitionDetailPage
import com.competra.web.pages.CompetitionsPage
import com.competra.web.pages.CreateClubPage
import com.competra.web.pages.CreateCompetitionPage
import com.competra.web.pages.DiaryListPage
import com.competra.web.pages.GroupMappingPage
import com.competra.web.pages.GroupSplitsTablePage
import com.competra.web.pages.ManageCompetitionPage
import com.competra.web.pages.ManagementPage
import com.competra.web.pages.MyJoinRequestsPage
import com.competra.web.pages.ParticipantSplitsPage
import com.competra.web.pages.PrivacyPolicyPage
import com.competra.web.pages.ProfileEditorPage
import com.competra.web.pages.ProfilePage
import com.competra.web.pages.RaceGraphPage
import com.competra.web.pages.RatingDetailPage
import com.competra.web.pages.RatingFormPage
import com.competra.web.pages.RatingsSearchPage
import com.competra.web.pages.TeamDetailPage
import com.competra.web.pages.WorkoutDetailPage
import com.competra.web.pages.WorkoutEditorPage
import com.competra.web.pages.WorkoutTrackPage
import com.competra.web.theme.CompetiraTheme

sealed class Page {
    data object Competitions : Page()
    data class CompetitionDetail(val competitionId: String) : Page()
    data object Management : Page()
    data object CreateCompetition : Page()
    data class ManageCompetition(val competition: OrienteeringCompetition) : Page()
    data object Profile : Page()
    data object ProfileEditor : Page()
    data object About : Page()
    data object PrivacyPolicy : Page()
    data class ParticipantSplits(val competitionId: String, val participantId: String) : Page()
    data class GroupSplitsTable(val competitionId: String, val groupId: Long, val groupTitle: String, val distanceId: Long?) : Page()
    data class RaceGraph(val competitionId: String, val groupId: Long, val groupTitle: String, val distanceId: Long?) : Page()
    data object Clubs : Page()
    data object CreateClub : Page()
    data class ClubDetail(val clubId: String) : Page()
    data class ClubJoinRequests(val clubId: String) : Page()
    data object MyJoinRequests : Page()
    data class TeamDetail(val teamId: String, val clubId: String) : Page()
    data object RatingsSearch : Page()
    data class RatingDetail(val ratingId: String) : Page()
    data class RatingForm(val clubId: String, val rating: Rating? = null) : Page()
    data class AddCompetitionToRating(
        val ratingId: String,
        val alreadyAddedCompetitionIds: Set<String>,
        val ratingGroups: List<RatingGroup>,
    ) : Page()
    data class GroupMapping(
        val ratingId: String,
        val competitionId: String,
        val ratingGroups: List<RatingGroup>,
        val suggestions: List<RatingGroupMappingSuggestion>? = null,
    ) : Page()
    data object Diary : Page()
    data class WorkoutEditor(val workoutId: Long? = null) : Page()
    data class WorkoutDetail(val workoutId: Long) : Page()
    data class WorkoutTrack(val workoutId: Long) : Page()
}

@Composable
fun App(initialPage: Page = Page.Competitions) {
    var page by remember { mutableStateOf(initialPage) }

    CompetiraTheme {
        when (val current = page) {
            is Page.CompetitionDetail -> CompetitionDetailPage(
                competitionId = current.competitionId,
                onBack = { page = Page.Competitions },
                onParticipantClick = { participantId ->
                    page = Page.ParticipantSplits(current.competitionId, participantId)
                },
                onGroupSplitsClick = { groupId, groupTitle, distanceId ->
                    page = Page.GroupSplitsTable(current.competitionId, groupId, groupTitle, distanceId)
                },
                onRaceGraphClick = { groupId, groupTitle, distanceId ->
                    page = Page.RaceGraph(current.competitionId, groupId, groupTitle, distanceId)
                },
            )
            is Page.ParticipantSplits -> ParticipantSplitsPage(
                competitionId = current.competitionId,
                participantId = current.participantId,
                onBack = { page = Page.CompetitionDetail(current.competitionId) },
            )
            is Page.GroupSplitsTable -> GroupSplitsTablePage(
                competitionId = current.competitionId,
                groupId = current.groupId,
                groupTitle = current.groupTitle,
                distanceId = current.distanceId,
                onBack = { page = Page.CompetitionDetail(current.competitionId) },
            )
            is Page.RaceGraph -> RaceGraphPage(
                competitionId = current.competitionId,
                groupId = current.groupId,
                groupTitle = current.groupTitle,
                distanceId = current.distanceId,
                onBack = { page = Page.CompetitionDetail(current.competitionId) },
            )
            is Page.CreateCompetition -> CreateCompetitionPage(
                onBack = { page = Page.Management },
                onCreated = { competition -> page = Page.ManageCompetition(competition) },
            )
            is Page.ManageCompetition -> ManageCompetitionPage(
                competition = current.competition,
                onBack = { page = Page.Management },
            )
            is Page.ProfileEditor -> ProfileEditorPage(
                onBack = { page = Page.Profile },
                onSaved = { page = Page.Profile },
            )
            is Page.About -> AboutPage(onBack = { page = Page.Profile })
            is Page.PrivacyPolicy -> PrivacyPolicyPage(onBack = { page = Page.Profile })
            is Page.CreateClub -> CreateClubPage(
                onBack = { page = Page.Clubs },
                onCreated = { clubId -> page = Page.ClubDetail(clubId) },
                onLoginSuccess = { page = Page.CreateClub },
            )
            is Page.ClubDetail -> ClubDetailPage(
                clubId = current.clubId,
                onBack = { page = Page.Clubs },
                onLoginSuccess = { page = Page.ClubDetail(current.clubId) },
                onJoinRequestsClick = { clubId -> page = Page.ClubJoinRequests(clubId) },
                onTeamClick = { teamId -> page = Page.TeamDetail(teamId, current.clubId) },
                onRatingClick = { ratingId -> page = Page.RatingDetail(ratingId) },
                onCreateRatingClick = { clubId -> page = Page.RatingForm(clubId) },
            )
            is Page.RatingsSearch -> RatingsSearchPage(
                onBack = { page = Page.Clubs },
                onRatingClick = { ratingId -> page = Page.RatingDetail(ratingId) },
            )
            is Page.RatingDetail -> RatingDetailPage(
                ratingId = current.ratingId,
                onBack = { page = Page.Clubs },
                onAddCompetitionClick = { ratingId, alreadyAddedIds, ratingGroups ->
                    page = Page.AddCompetitionToRating(ratingId, alreadyAddedIds, ratingGroups)
                },
                onEditClick = { clubId, rating -> page = Page.RatingForm(clubId, rating) },
                onMappingClick = { ratingId, competitionId, ratingGroups ->
                    page = Page.GroupMapping(ratingId, competitionId, ratingGroups)
                },
            )
            is Page.RatingForm -> RatingFormPage(
                clubId = current.clubId,
                existingRating = current.rating,
                onBack = {
                    page = current.rating?.let { Page.RatingDetail(it.id) } ?: Page.ClubDetail(current.clubId)
                },
                onSaved = { rating -> page = Page.RatingDetail(rating.id) },
                onLoginSuccess = { page = Page.RatingForm(current.clubId, current.rating) },
            )
            is Page.AddCompetitionToRating -> AddCompetitionToRatingPage(
                ratingId = current.ratingId,
                alreadyAddedCompetitionIds = current.alreadyAddedCompetitionIds,
                onBack = { page = Page.RatingDetail(current.ratingId) },
                onAdded = { competitionId, suggestions ->
                    page = Page.GroupMapping(current.ratingId, competitionId, current.ratingGroups, suggestions)
                },
            )
            is Page.GroupMapping -> GroupMappingPage(
                ratingId = current.ratingId,
                competitionId = current.competitionId,
                ratingGroups = current.ratingGroups,
                initialSuggestions = current.suggestions,
                onBack = { page = Page.RatingDetail(current.ratingId) },
                onSaved = { page = Page.RatingDetail(current.ratingId) },
            )
            is Page.ClubJoinRequests -> ClubJoinRequestsPage(
                clubId = current.clubId,
                onBack = { page = Page.ClubDetail(current.clubId) },
            )
            is Page.MyJoinRequests -> MyJoinRequestsPage(
                onBack = { page = Page.Clubs },
                onClubClick = { clubId -> page = Page.ClubDetail(clubId) },
            )
            is Page.TeamDetail -> TeamDetailPage(
                teamId = current.teamId,
                onBack = { page = Page.ClubDetail(current.clubId) },
            )
            is Page.WorkoutEditor -> WorkoutEditorPage(
                workoutId = current.workoutId,
                onBack = { page = current.workoutId?.let { Page.WorkoutDetail(it) } ?: Page.Diary },
                onSaved = { workout -> page = Page.WorkoutDetail(workout.id) },
                onLoginSuccess = { page = Page.WorkoutEditor(current.workoutId) },
            )
            is Page.WorkoutDetail -> WorkoutDetailPage(
                workoutId = current.workoutId,
                onBack = { page = Page.Diary },
                onEditClick = { workoutId -> page = Page.WorkoutEditor(workoutId) },
                onTrackClick = { workoutId -> page = Page.WorkoutTrack(workoutId) },
            )
            is Page.WorkoutTrack -> WorkoutTrackPage(
                workoutId = current.workoutId,
                onBack = { page = Page.WorkoutDetail(current.workoutId) },
            )
            else -> MainScaffold(currentPage = current, onNavigate = { page = it })
        }
    }
}

@Composable
private fun NavIcon(selected: Boolean, label: String) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier.size(24.dp).background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = MaterialTheme.colorScheme.surface, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MainScaffold(currentPage: Page, onNavigate: (Page) -> Unit) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentPage is Page.Competitions,
                    onClick = { onNavigate(Page.Competitions) },
                    icon = { NavIcon(selected = currentPage is Page.Competitions, label = "С") },
                    label = { Text("Соревнования") },
                )
                NavigationBarItem(
                    selected = currentPage is Page.Management,
                    onClick = { onNavigate(Page.Management) },
                    icon = { NavIcon(selected = currentPage is Page.Management, label = "У") },
                    label = { Text("Управление") },
                )
                NavigationBarItem(
                    selected = currentPage is Page.Clubs,
                    onClick = { onNavigate(Page.Clubs) },
                    icon = { NavIcon(selected = currentPage is Page.Clubs, label = "К") },
                    label = { Text("Клубы") },
                )
                NavigationBarItem(
                    selected = currentPage is Page.Diary,
                    onClick = { onNavigate(Page.Diary) },
                    icon = { NavIcon(selected = currentPage is Page.Diary, label = "Д") },
                    label = { Text("Дневник") },
                )
                NavigationBarItem(
                    selected = currentPage is Page.Profile,
                    onClick = { onNavigate(Page.Profile) },
                    icon = { NavIcon(selected = currentPage is Page.Profile, label = "П") },
                    label = { Text("Профиль") },
                )
            }
        }
    ) { padding ->
        when (currentPage) {
            is Page.Competitions -> CompetitionsPage(
                modifier = Modifier.padding(padding),
                onCompetitionClick = { id -> onNavigate(Page.CompetitionDetail(id)) },
            )
            is Page.Management -> ManagementPage(
                modifier = Modifier.padding(padding),
                onCreateClick = { onNavigate(Page.CreateCompetition) },
                onManageClick = { competition -> onNavigate(Page.ManageCompetition(competition)) },
                onLoginSuccess = { onNavigate(Page.Competitions) },
            )
            is Page.Profile -> ProfilePage(
                onLoginSuccess = { onNavigate(Page.Competitions) },
                onCompetitionClick = { id -> onNavigate(Page.CompetitionDetail(id)) },
                onEditProfileClick = { onNavigate(Page.ProfileEditor) },
                onAboutClick = { onNavigate(Page.About) },
                onPrivacyClick = { onNavigate(Page.PrivacyPolicy) },
            )
            is Page.Clubs -> ClubsListPage(
                onClubClick = { clubId -> onNavigate(Page.ClubDetail(clubId)) },
                onCreateClick = { onNavigate(Page.CreateClub) },
                onMyJoinRequestsClick = { onNavigate(Page.MyJoinRequests) },
                onRatingsSearchClick = { onNavigate(Page.RatingsSearch) },
            )
            is Page.Diary -> DiaryListPage(
                onWorkoutClick = { workoutId -> onNavigate(Page.WorkoutDetail(workoutId)) },
                onCreateClick = { onNavigate(Page.WorkoutEditor(null)) },
                onLoginSuccess = { onNavigate(Page.Diary) },
            )
            else -> {}
        }
    }
}
