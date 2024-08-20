package com.sapuseven.untis.ui.widgets

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.unit.ColorProvider
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import com.sapuseven.untis.widgets.toGlanceTextStyle

@Composable
fun WidgetListViewHeader(
	modifier: GlanceModifier = GlanceModifier,
	dayColorScheme: ColorScheme,
	nightColorScheme: ColorScheme,
	headlineContent: String,
	supportingContent: String? = null,
) {
	val primary = ColorProvider(
		day = dayColorScheme.primary,
		night = nightColorScheme.primary,
	)

	val onPrimary = ColorProvider(
		day = dayColorScheme.onPrimary,
		night = nightColorScheme.onPrimary,
	)

	WidgetListItem(
		modifier = modifier,
		headlineContent = headlineContent,
		supportingContent = supportingContent,
		surfaceColor = primary,
		textColor = onPrimary,
		/*trailingContent = {
			Image(
				provider = ImageProvider(R.drawable.base_appwidget_reload),
				contentDescription = context.resources.getString(R.string.all_reload),
				modifier = GlanceModifier.padding(start = 16.dp)
			)
		}*/
	)
}

@Composable
fun WidgetListView(
	modifier: GlanceModifier,
	dayColorScheme: ColorScheme,
	nightColorScheme: ColorScheme,
	items: List<WidgetListItemModel>,
	onClickAction: Action,
) {
	/*val surface = ColorProvider(
		day = dayColorScheme.surface,
		night = nightColorScheme.surface,
	)

	val onSurface = ColorProvider(
		day = dayColorScheme.onSurface,
		night = nightColorScheme.onSurface,
	)*/

	// Color for regular lessons
	val regularTextColor = ColorProvider(
		day = dayColorScheme.onSurface,
		night = nightColorScheme.onSurface,
	)

	val regularBackgroundColor = ColorProvider(
		day = dayColorScheme.surface,
		night = nightColorScheme.surface,
	)

	// Color for irregular lessons
	val irregularTextColor = ColorProvider(
		day = Color(159, 127, 0),  // Change to your preferred color
		night = Color(255, 255, 63)
	)

	val irregularBackgroundColor = ColorProvider(
		day = dayColorScheme.surface,
		night = nightColorScheme.surface,
	)

	// Color for canceled lessons
	val canceledTextColor = ColorProvider(
		day = Color(223, 0, 0),  // Change to your preferred color
		night = Color(255, 31, 31)
	)

	val canceledBackgroundColor = ColorProvider(
		day = dayColorScheme.surface,
		night = nightColorScheme.surface,
	)

	LazyColumn(
		modifier = modifier
	) {
		//items(items) {
		items(items) { it ->
			// Determine text and background colors based on item status
			val (textColor, backgroundColor) = when (it.status) {
				"irregular" -> Pair(irregularTextColor, irregularBackgroundColor)
				"canceled" -> Pair(canceledTextColor, canceledBackgroundColor)
				else -> Pair(regularTextColor, regularBackgroundColor)
			}

			if (!it.isHidden) {
				WidgetListItem(
					// TODO: The current version (alpha05) has a bug which prevents click events from children to be registered; see https://issuetracker.google.com/issues/242397933
					// TODO: Use actionStartActivity on timetable item click, otherwise show reload action
					modifier = GlanceModifier.clickable(onClickAction),
					leadingContent = it.leadingContent,
					headlineContent = it.headlineContent,
					supportingContent = it.supportingContent,
					surfaceColor = backgroundColor,
					textColor = textColor
				)
			}
		}
	}
}

@Composable
private fun WidgetListItem(
	modifier: GlanceModifier = GlanceModifier,
	headlineContent: String,
	supportingContent: String? = null,
	surfaceColor: ColorProvider,
	textColor: ColorProvider,
	typography: Typography = MaterialTheme.typography,
	leadingContent: @Composable ((surfaceColor: ColorProvider, textColor: ColorProvider) -> Unit)? = null,
	trailingContent: @Composable (() -> Unit)? = null,
) {
	Row(
		modifier = modifier
			.fillMaxWidth()
			.background(surfaceColor)
			.padding(horizontal = 16.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		leadingContent?.let {
			Box(
				contentAlignment = Alignment.Center,
				modifier = GlanceModifier
					.padding(end = 16.dp)
			) {
				leadingContent(surfaceColor, textColor)
			}
		}

		Column(
			modifier = GlanceModifier
				.defaultWeight()
				.padding(vertical = 8.dp)
		) {
			Text(
				headlineContent,
				style = typography.bodyLarge.toGlanceTextStyle(textColor)
			)
			supportingContent?.let {
				Text(
					supportingContent,
					style = typography.bodyMedium.toGlanceTextStyle(textColor)
				)
			}
		}

		trailingContent?.invoke()
	}
}

data class WidgetListItemModel(
	val headlineContent: String,
	val supportingContent: String,
	val leadingContent: @Composable ((surfaceColor: ColorProvider, textColor: ColorProvider) -> Unit)?,
	val status: String = "regular",
	val isHidden: Boolean = false,
)
