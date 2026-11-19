package com.sierracode.autocontrol.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.data.preferences.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val settings: SettingsStore) : ViewModel() {
    fun complete(onDone: () -> Unit) = viewModelScope.launch {
        settings.setOnboarded(true)
        onDone()
    }
}

private enum class PageKind { Hook, Expenses, Fuel, Maintenance, Documents, Insights, Trust }

private data class OnboardingPage(
    val kind: PageKind,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int
)

private val onboardingPages = listOf(
    OnboardingPage(PageKind.Hook, R.string.onboard_p1_title, R.string.onboard_p1_desc),
    OnboardingPage(PageKind.Expenses, R.string.onboard_p2_title, R.string.onboard_p2_desc),
    OnboardingPage(PageKind.Fuel, R.string.onboard_p3_title, R.string.onboard_p3_desc),
    OnboardingPage(PageKind.Maintenance, R.string.onboard_p4_title, R.string.onboard_p4_desc),
    OnboardingPage(PageKind.Documents, R.string.onboard_p5_title, R.string.onboard_p5_desc),
    OnboardingPage(PageKind.Insights, R.string.onboard_p6_title, R.string.onboard_p6_desc),
    OnboardingPage(PageKind.Trust, R.string.onboard_p7_title, R.string.onboard_p7_desc)
)

@Composable
fun OnboardingScreen(onDone: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    val pages = onboardingPages
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLast = pager.currentPage == pages.lastIndex

    BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier.fillMaxWidth().height(48.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isLast) {
                    TextButton(onClick = { vm.complete(onDone) }) {
                        Text(
                            stringResource(R.string.onboard_skip),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
                val progress by animateFloatAsState(
                    targetValue = if (pager.currentPage == index) 1f else 0f,
                    animationSpec = tween(900),
                    label = "pageProgress"
                )
                OnboardingPageContent(pages[index], progress)
            }

            PageIndicator(count = pages.size, current = pager.currentPage)

            Button(
                onClick = {
                    if (!isLast) {
                        scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    } else {
                        vm.complete(onDone)
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Text(stringResource(if (!isLast) R.string.onboard_next else R.string.onboard_start))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage, progress: Float) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Las vistas previas son ilustrativas: TalkBack las omite, salvo la de "Trust" que tiene texto real.
        val heroModifier = if (page.kind == PageKind.Trust) {
            Modifier.fillMaxWidth()
        } else {
            Modifier.fillMaxWidth().clearAndSetSemantics { }
        }
        Box(heroModifier, contentAlignment = Alignment.Center) {
            when (page.kind) {
                PageKind.Hook -> HookHero(progress)
                PageKind.Expenses -> ExpensesHero()
                PageKind.Fuel -> FuelHero(progress)
                PageKind.Maintenance -> MaintenanceHero(progress)
                PageKind.Documents -> DocumentsHero()
                PageKind.Insights -> InsightsHero(progress)
                PageKind.Trust -> TrustHero(progress)
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(page.bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
    }
}

/* ---------- Ilustraciones (vistas previas de la app con datos de ejemplo) ---------- */

@Composable
private fun HookHero(progress: Float) {
    val items = listOf(
        Icons.Filled.Savings to R.string.onboard_q1,
        Icons.Filled.Build to R.string.onboard_q2,
        Icons.Filled.Description to R.string.onboard_q3
    )
    Column(Modifier.fillMaxWidth()) {
        items.forEachIndexed { i, (icon, textRes) ->
            val a = (progress * 3f - i).coerceIn(0f, 1f)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .graphicsLayer {
                        alpha = a
                        translationY = (1f - a) * 24.dp.toPx()
                    }
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(icon)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        stringResource(textRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpensesHero() {
    MockCard {
        Text(
            stringResource(R.string.onboard_mock_cost_per_km),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "\$2.85",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        HorizontalDivider()
        MockRow(Icons.Filled.LocalGasStation, stringResource(R.string.onboard_mock_fuel)) {
            Text("\$850", fontWeight = FontWeight.SemiBold)
        }
        MockRow(Icons.Filled.Build, stringResource(R.string.onboard_mock_oil)) {
            Text("\$1,200", fontWeight = FontWeight.SemiBold)
        }
        MockRow(Icons.Filled.Description, stringResource(R.string.onboard_mock_insurance)) {
            Text("\$640", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FuelHero(progress: Float) {
    val value = String.format(Locale.US, "%.1f", 12.4f * progress)
    MockCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Filled.LocalGasStation, 56.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    stringResource(R.string.onboard_mock_efficiency),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        value,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "km/L",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
        }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.onboard_mock_full_tank), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun MaintenanceHero(progress: Float) {
    MockCard {
        MockProgressRow(
            icon = Icons.Filled.Build,
            title = stringResource(R.string.onboard_mock_oil_change),
            subtitle = stringResource(R.string.onboard_mock_km_left, "450"),
            target = 0.88f,
            progress = progress,
            color = MaterialTheme.colorScheme.tertiary
        )
        HorizontalDivider()
        MockProgressRow(
            icon = Icons.Filled.Build,
            title = stringResource(R.string.onboard_mock_brakes),
            subtitle = stringResource(R.string.onboard_mock_km_left, "3,200"),
            target = 0.35f,
            progress = progress,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun DocumentsHero() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MockCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Filled.Notifications)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        stringResource(R.string.onboard_mock_notif_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.onboard_mock_notif_text),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        MockCard {
            DocRow(R.string.onboard_mock_insurance, 7, urgent = true)
            DocRow(R.string.onboard_mock_doc_inspection, 45, urgent = false)
            DocRow(R.string.onboard_mock_doc_registration, 120, urgent = false)
        }
    }
}

@Composable
private fun DocRow(@StringRes labelRes: Int, days: Int, urgent: Boolean) {
    MockRow(Icons.Filled.Description, stringResource(labelRes)) {
        Surface(
            shape = CircleShape,
            color = if (urgent) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                stringResource(R.string.onboard_mock_in_days, days),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = if (urgent) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun InsightsHero(progress: Float) {
    val heights = listOf(0.35f, 0.55f, 0.4f, 0.8f, 0.6f, 0.95f)
    MockCard {
        Row(
            Modifier.fillMaxWidth().height(110.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            heights.forEachIndexed { i, h ->
                val p = (progress * 1.5f - i * 0.1f).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(h * p)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (i == heights.lastIndex) 1f else 0.55f))
                )
            }
        }
        HorizontalDivider()
        MockProgressRow(
            icon = Icons.Filled.Flag,
            title = stringResource(R.string.onboard_mock_goal),
            subtitle = stringResource(R.string.onboard_mock_goal_progress),
            target = 0.4f,
            progress = progress,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun TrustHero(progress: Float) {
    val checks = listOf(
        R.string.onboard_check_1,
        R.string.onboard_check_2,
        R.string.onboard_check_3,
        R.string.onboard_check_4
    )
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBubble(Icons.Filled.Lock, 72.dp)
        Spacer(Modifier.height(16.dp))
        MockCard {
            checks.forEachIndexed { i, res ->
                val a = (progress * 4f - i).coerceIn(0f, 1f)
                Row(
                    Modifier.fillMaxWidth().graphicsLayer { alpha = a },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(res), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/* ---------- Piezas reutilizables ---------- */

@Composable
private fun MockCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun IconBubble(icon: ImageVector, size: Dp = 40.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(size * 0.55f),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun MockRow(icon: ImageVector, label: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconBubble(icon)
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
private fun MockProgressRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    target: Float,
    progress: Float,
    color: Color
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(icon)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { target * progress },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun PageIndicator(count: Int, current: Int) {
    val description = stringResource(R.string.onboard_page_indicator, current + 1, count)
    Row(
        Modifier
            .padding(vertical = 16.dp)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { i ->
            val selected = i == current
            val width by animateDpAsState(if (selected) 28.dp else 8.dp, label = "dotWidth")
            val color by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "dotColor"
            )
            Box(
                Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}