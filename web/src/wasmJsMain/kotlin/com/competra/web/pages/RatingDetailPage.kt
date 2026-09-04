package com.competra.web.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.competra.data.api.ApiResult
import com.competra.data.repository.ClubRepository
import com.competra.data.repository.RatingRepository
import com.competra.data.repository.UserRepository
import com.competra.domain.models.Rating
import com.competra.domain.models.RatingCompetition
import com.competra.domain.models.RatingGroup
import com.competra.domain.models.RatingStanding
import com.competra.web.utils.toLocaleDateString
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatingDetailPage(
    ratingId: String,
    onBack: () -> Unit,
    onAddCompetitionClick: (ratingId: String, alreadyAddedIds: Set<String>, ratingGroups: List<RatingGroup>) -> Unit,
    onEditClick: (clubId: String, rating: Rating) -> Unit,
    onMappingClick: (String, String, List<RatingGroup>) -> Unit,
) {
    val ratingRepo: RatingRepository = koinInject()
    val clubRepo: ClubRepository = koinInject()
    val userRepo: UserRepository = koinInject()
    val scope = rememberCoroutineScope()

    var rating by remember { mutableStateOf<Rating?>(null) }
    var competitions by remember { mutableStateOf<List<RatingCompetition>>(emptyList()) }
    var isAdmin by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedGroupId by remember { mutableStateOf<Long?>(null) }
    var standingsByGroup by remember { mutableStateOf<Map<Long, List<RatingStanding>>>(emptyMap()) }
    var standingsLoading by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPointsInfo by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }

    suspend fun reload() {
        loading = true
        standingsByGroup = emptyMap()
        when (val r = ratingRepo.getRating(ratingId)) {
            is ApiResult.Success -> {
                rating = r.data
                if (selectedGroupId == null) selectedGroupId = r.data.groups.firstOrNull()?.id
                when (val members = clubRepo.getClubMembers(r.data.ownerClubId)) {
                    is ApiResult.Success -> {
                        when (val profile = userRepo.getUserProfile()) {
                            is ApiResult.Success -> {
                                val myRole = members.data.firstOrNull { it.userId == profile.data.id }?.role
                                isAdmin = myRole in listOf("FOUNDER", "ADMIN")
                            }
                            is ApiResult.Error -> isAdmin = false
                        }
                    }
                    is ApiResult.Error -> isAdmin = false
                }
            }
            is ApiResult.Error -> error = r.message
        }
        when (val r = ratingRepo.getRatingCompetitions(ratingId)) {
            is ApiResult.Success -> competitions = r.data
            is ApiResult.Error -> {}
        }
        loading = false
    }

    suspend fun loadStandings(groupId: Long) {
        if (standingsByGroup.containsKey(groupId)) return
        standingsLoading = true
        when (val r = ratingRepo.getStandings(ratingId, groupId)) {
            is ApiResult.Success -> standingsByGroup = standingsByGroup + (groupId to r.data.standings)
            is ApiResult.Error -> {}
        }
        standingsLoading = false
    }

    LaunchedEffect(ratingId, reloadKey) { reload() }
    LaunchedEffect(selectedGroupId) { selectedGroupId?.let { loadStandings(it) } }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удалить рейтинг?") },
            text = { Text("Действие необратимо.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        when (ratingRepo.deleteRating(ratingId)) {
                            is ApiResult.Success -> onBack()
                            is ApiResult.Error -> actionError = "Не удалось удалить рейтинг"
                        }
                        showDeleteConfirm = false
                    }
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Отмена") } },
        )
    }

    if (showPointsInfo) {
        RatingPointsInfoDialog(onDismiss = { showPointsInfo = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(rating?.name ?: "Рейтинг", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (isAdmin) {
                        val r = rating
                        if (r != null) {
                            TextButton(onClick = {
                                onAddCompetitionClick(r.id, competitions.map { it.competitionId }.toSet(), r.groups)
                            }) { Text("Добавить старт") }
                            TextButton(onClick = { onEditClick(r.ownerClubId, r) }) { Text("Редактировать") }
                            TextButton(onClick = { showDeleteConfirm = true }) {
                                Text("Удалить", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                },
            )
        }
    ) { padding ->
        if (loading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val r = rating
        if (r == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(error ?: "Рейтинг не найден")
            }
            return@Scaffold
        }

        val standingsLoadingNow = standingsLoading && standingsByGroup[selectedGroupId] == null
        val standings = selectedGroupId?.let { standingsByGroup[it] } ?: emptyList()
        val onRemoveCompetition: (String) -> Unit = { competitionId ->
            scope.launch {
                when (ratingRepo.removeCompetition(ratingId, competitionId)) {
                    is ApiResult.Success -> reloadKey++
                    is ApiResult.Error -> actionError = "Не удалось удалить соревнование"
                }
            }
        }
        val onMapCompetition: (String) -> Unit = { competitionId -> onMappingClick(ratingId, competitionId, r.groups) }

        // На широких экранах рейтинг и список стартов, которые в него входят, показываются
        // рядом двумя колонками — при длинной таблице результатов не нужно скроллить вниз,
        // чтобы увидеть старты. На узких — как раньше, единый скроллящийся список (см. тот
        // же приём в competra-android RatingDetailScreen): иначе раздел "Соревнования
        // рейтинга" уходит за пределы экрана и становится недостижим.
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (maxWidth >= 900.dp) {
                // Высота заголовка левой колонки (вкладки групп + строка "Как начисляются очки")
                // измеряется фактически и переносится на заголовок правой колонки — так списки
                // участников и стартов гарантированно начинаются на одном уровне независимо от
                // шрифтов/масштаба, без подбора констант "на глаз".
                val density = LocalDensity.current
                var headerHeightPx by remember { mutableStateOf(0) }
                val headerHeight = with(density) { headerHeightPx.toDp() }

                Column(modifier = Modifier.fillMaxSize()) {
                    actionError?.let { message ->
                        Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier.weight(1f).onGloballyPositioned { headerHeightPx = it.size.height },
                        ) {
                            if (r.groups.isNotEmpty()) {
                                GroupTabsAndPointsInfo(
                                    groups = r.groups,
                                    selectedGroupId = selectedGroupId,
                                    onSelectGroup = { selectedGroupId = it },
                                    onShowPointsInfo = { showPointsInfo = true },
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        "В рейтинге нет групп зачёта",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Box(
                            modifier = Modifier.weight(1f).height(headerHeight),
                            contentAlignment = Alignment.BottomStart,
                        ) {
                            Text(
                                "Соревнования рейтинга",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        LazyColumn(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            if (r.groups.isNotEmpty()) {
                                standingsListItems(standingsLoadingNow, standings)
                            }
                        }
                        VerticalDivider(modifier = Modifier.fillMaxHeight())
                        LazyColumn(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            competitionsListItems(
                                competitions = competitions,
                                isAdmin = isAdmin,
                                onMappingClick = onMapCompetition,
                                onRemove = onRemoveCompetition,
                            )
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    actionError?.let { message ->
                        item {
                            Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                        }
                    }
                    standingsSection(
                        groups = r.groups,
                        selectedGroupId = selectedGroupId,
                        onSelectGroup = { selectedGroupId = it },
                        onShowPointsInfo = { showPointsInfo = true },
                        standingsLoading = standingsLoadingNow,
                        standings = standings,
                    )
                    competitionsSection(
                        competitions = competitions,
                        isAdmin = isAdmin,
                        onMappingClick = onMapCompetition,
                        onRemove = onRemoveCompetition,
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupTabsAndPointsInfo(
    groups: List<RatingGroup>,
    selectedGroupId: Long?,
    onSelectGroup: (Long) -> Unit,
    onShowPointsInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ScrollableTabRow(selectedTabIndex = groups.indexOfFirst { it.id == selectedGroupId }.coerceAtLeast(0)) {
            groups.forEach { group ->
                Tab(
                    selected = group.id == selectedGroupId,
                    onClick = { onSelectGroup(group.id) },
                    text = { Text(group.title) },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onShowPointsInfo() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                "Как начисляются очки",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun LazyListScope.standingsSection(
    groups: List<RatingGroup>,
    selectedGroupId: Long?,
    onSelectGroup: (Long) -> Unit,
    onShowPointsInfo: () -> Unit,
    standingsLoading: Boolean,
    standings: List<RatingStanding>,
) {
    if (groups.isEmpty()) {
        item {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("В рейтинге нет групп зачёта", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    item {
        GroupTabsAndPointsInfo(
            groups = groups,
            selectedGroupId = selectedGroupId,
            onSelectGroup = onSelectGroup,
            onShowPointsInfo = onShowPointsInfo,
        )
    }
    standingsListItems(standingsLoading, standings)
}

private fun LazyListScope.standingsListItems(standingsLoading: Boolean, standings: List<RatingStanding>) {
    if (standingsLoading) {
        item {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    } else if (standings.isEmpty()) {
        item {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Пока нет данных", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        items(standings, key = { "standing_${it.participantKey}" }) { standing ->
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                RatingStandingRow(standing)
            }
        }
    }
}

private fun LazyListScope.competitionsSection(
    competitions: List<RatingCompetition>,
    isAdmin: Boolean,
    onMappingClick: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    item {
        Text(
            "Соревнования рейтинга",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    competitionsListItems(competitions, isAdmin, onMappingClick, onRemove)
}

private fun LazyListScope.competitionsListItems(
    competitions: List<RatingCompetition>,
    isAdmin: Boolean,
    onMappingClick: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    if (competitions.isEmpty()) {
        item {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Соревнований пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        items(competitions, key = { "competition_${it.id}" }) { rc ->
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                CompetitionRatingCard(
                    competition = rc,
                    isAdmin = isAdmin,
                    onMappingClick = { onMappingClick(rc.competitionId) },
                    onRemove = { onRemove(rc.competitionId) },
                )
            }
        }
    }
}

@Composable
private fun CompetitionRatingCard(
    competition: RatingCompetition,
    isAdmin: Boolean,
    onMappingClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(competition.competitionTitle, style = MaterialTheme.typography.bodyLarge)
                Text(
                    competition.competitionStartDate.toLocaleDateString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isAdmin) {
                IconButton(onClick = onMappingClick) {
                    Icon(Icons.Filled.Edit, contentDescription = "Маппинг групп")
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Удалить")
                }
            }
        }
    }
}

@Composable
private fun RatingStandingRow(standing: RatingStanding) {
    val startsCount = standing.breakdown.size
    val isTopThree = standing.rank in 1..3
    val badgeContainer = when (standing.rank) {
        1 -> Color(0xFFE0B00A)
        2 -> Color(0xFFA9B0B8)
        3 -> Color(0xFFC17A3E)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val badgeContent = if (isTopThree) Color(0xFF2A2A2A) else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isTopThree) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(badgeContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (isTopThree) {
                    Icon(
                        Icons.Filled.EmojiEvents,
                        contentDescription = "Место ${standing.rank}",
                        tint = badgeContent,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Text("${standing.rank}", style = MaterialTheme.typography.titleSmall, color = badgeContent)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(standing.displayName, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.DirectionsRun,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "$startsCount ${startsCountLabel(startsCount)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${standing.totalPoints}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text("очков", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun startsCountLabel(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> "стартов"
        mod10 == 1 -> "старт"
        mod10 in 2..4 -> "старта"
        else -> "стартов"
    }
}

/** Таблица очков по месту, соответствует RatingPointsTable на бэкенде (основана на таблице IOF World Cup). */
private val fixedRatingPoints = listOf(
    1 to 100, 2 to 80, 3 to 60, 4 to 50, 5 to 45,
    6 to 40, 7 to 37, 8 to 35, 9 to 33,
)

@Composable
private fun RatingPointsInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Как начисляются очки") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "За каждый старт, добавленный в рейтинг, участник получает очки по месту, занятому в своей группе зачёта. Результат в рейтинге — сумма очков за все старты.",
                    style = MaterialTheme.typography.bodyLarge,
                )

                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Место",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Очки",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                fixedRatingPoints.forEach { (place, points) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("$place", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("$points", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("10–40", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("41 − место", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    "С 41-го места очки не начисляются.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Если несколько участников набрали одинаковую сумму очков, они делят место — например, при двух третьих местах следующий участник получает пятое.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Понятно") }
        },
    )
}
