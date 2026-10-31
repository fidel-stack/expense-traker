package com.xyrospace.expensetracker.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xyrospace.expensetracker.data.local.CategoryEntity

fun parseCategoryColor(hex: String): Color = try {
    Color(AndroidColor.parseColor(hex))
} catch (_: IllegalArgumentException) {
    Color.Gray
}

/** Positive/income color that keeps contrast in both light and dark themes. */
@Composable
fun incomeAmountColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)

fun categoryIconFor(icon: String): ImageVector = when (icon) {
    "restaurant" -> Icons.Filled.Restaurant
    "directions_bus" -> Icons.Filled.DirectionsBus
    "shopping_bag" -> Icons.Filled.ShoppingBag
    "receipt_long" -> Icons.AutoMirrored.Filled.ReceiptLong
    "phone_android" -> Icons.Filled.PhoneAndroid
    "medical_services" -> Icons.Filled.MedicalServices
    "movie" -> Icons.Filled.Movie
    "school" -> Icons.Filled.School
    "home" -> Icons.Filled.Home
    "payments" -> Icons.Filled.Payments
    "groups" -> Icons.Filled.Groups
    "more_horiz" -> Icons.Filled.MoreHoriz
    else -> Icons.AutoMirrored.Filled.HelpOutline
}

@Composable
fun CategoryAvatar(
    category: CategoryEntity?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val color = category?.let { parseCategoryColor(it.color) } ?: MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = category?.let { categoryIconFor(it.icon) } ?: Icons.AutoMirrored.Filled.HelpOutline,
            contentDescription = category?.name,
            tint = color,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}
