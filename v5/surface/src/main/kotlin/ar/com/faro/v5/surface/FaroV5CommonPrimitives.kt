package ar.com.faro.v5.surface

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

enum class FaroV5StatusTone {
    NEUTRAL,
    EMPHASIS,
    SUCCESS,
    WARNING,
    ERROR,
}

@Composable
fun FaroV5PrimaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.minimumInteractiveComponentSize(),
        enabled = enabled,
    ) {
        Text(label)
    }
}

@Composable
fun FaroV5SecondaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.minimumInteractiveComponentSize(),
        enabled = enabled,
    ) {
        Text(label)
    }
}

@Composable
fun FaroV5TextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.minimumInteractiveComponentSize(),
        enabled = enabled,
    ) {
        Text(label)
    }
}

@Composable
fun FaroV5SurfaceIntro(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FaroV5Spacing.compact),
    ) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
        )
        supportingText
            ?.takeIf(String::isNotBlank)
            ?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
    }
}

@Composable
fun FaroV5SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(FaroV5Spacing.large),
            verticalArrangement = Arrangement.spacedBy(FaroV5Spacing.small),
        ) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
            supportingText
                ?.takeIf(String::isNotBlank)
                ?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            content()
        }
    }
}

@Composable
fun FaroV5LoadingState(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    FaroV5StateLayout(
        title = title,
        supportingText = supportingText,
        modifier = modifier.semantics {
            liveRegion = LiveRegionMode.Polite
        },
        leading = {
            CircularProgressIndicator()
        },
    )
}

@Composable
fun FaroV5EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    FaroV5StateLayout(
        title = title,
        supportingText = supportingText,
        modifier = modifier,
        actionLabel = actionLabel,
        onAction = onAction,
    )
}

@Composable
fun FaroV5ErrorState(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val semanticError =
        listOfNotNull(title, supportingText?.takeIf(String::isNotBlank))
            .joinToString(". ")

    FaroV5StateLayout(
        title = title,
        supportingText = supportingText,
        modifier = modifier.semantics {
            error(semanticError)
        },
        actionLabel = actionLabel,
        onAction = onAction,
    )
}

@Composable
fun FaroV5StatusBanner(
    message: String,
    tone: FaroV5StatusTone,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val (containerColor, contentColor) = faroV5ToneColors(tone)
    val semanticModifier =
        if (tone == FaroV5StatusTone.ERROR) {
            modifier.semantics {
                error(message)
            }
        } else {
            modifier
        }

    Card(
        modifier = semanticModifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors =
            CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = FaroV5Spacing.medium,
                        vertical = FaroV5Spacing.small,
                    ),
            verticalArrangement = Arrangement.spacedBy(FaroV5Spacing.compact),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
            FaroV5OptionalAction(
                actionLabel = actionLabel,
                onAction = onAction,
            )
        }
    }
}

@Composable
fun FaroV5TransientMessageHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier.semantics {
            liveRegion = LiveRegionMode.Polite
        },
    )
}

@Composable
fun FaroV5SafeArea(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        content = content,
    )
}

@Composable
private fun FaroV5StateLayout(
    title: String,
    supportingText: String?,
    modifier: Modifier,
    leading: (@Composable () -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(FaroV5Spacing.large),
        verticalArrangement = Arrangement.spacedBy(FaroV5Spacing.small),
    ) {
        leading?.invoke()
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
        supportingText
            ?.takeIf(String::isNotBlank)
            ?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        FaroV5OptionalAction(
            actionLabel = actionLabel,
            onAction = onAction,
        )
    }
}

@Composable
private fun FaroV5OptionalAction(
    actionLabel: String?,
    onAction: (() -> Unit)?,
) {
    if (!actionLabel.isNullOrBlank() && onAction != null) {
        Spacer(Modifier.height(FaroV5Spacing.compact))
        FaroV5SecondaryAction(
            label = actionLabel,
            onClick = onAction,
        )
    }
}

@Composable
private fun faroV5ToneColors(
    tone: FaroV5StatusTone,
): Pair<Color, Color> {
    val semantic = LocalFaroV5SemanticColors.current
    return when (tone) {
        FaroV5StatusTone.NEUTRAL ->
            MaterialTheme.colorScheme.surfaceVariant to
                MaterialTheme.colorScheme.onSurfaceVariant
        FaroV5StatusTone.EMPHASIS ->
            MaterialTheme.colorScheme.primaryContainer to
                MaterialTheme.colorScheme.onPrimaryContainer
        FaroV5StatusTone.SUCCESS ->
            semantic.successContainer to semantic.onSuccessContainer
        FaroV5StatusTone.WARNING ->
            semantic.warningContainer to semantic.onWarningContainer
        FaroV5StatusTone.ERROR ->
            MaterialTheme.colorScheme.errorContainer to
                MaterialTheme.colorScheme.onErrorContainer
    }
}
