package com.moneta.finance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moneta.finance.ui.theme.Moneta

/** Stable icon keys stored in the database, mapped to vector icons. */
object CategoryIcons {
    val all: LinkedHashMap<String, ImageVector> = linkedMapOf(
        "groceries" to Icons.Filled.LocalGroceryStore,
        "restaurant" to Icons.Filled.Restaurant,
        "coffee" to Icons.Filled.LocalCafe,
        "fastfood" to Icons.Filled.Fastfood,
        "fuel" to Icons.Filled.LocalGasStation,
        "car" to Icons.Filled.DirectionsCar,
        "bus" to Icons.Filled.DirectionsBus,
        "shopping" to Icons.Filled.ShoppingBag,
        "clothes" to Icons.Filled.Checkroom,
        "bolt" to Icons.Filled.Bolt,
        "receipt" to Icons.Filled.Receipt,
        "wifi" to Icons.Filled.Wifi,
        "phone" to Icons.Filled.PhoneAndroid,
        "subscriptions" to Icons.Filled.Subscriptions,
        "home" to Icons.Filled.Home,
        "health" to Icons.Filled.LocalHospital,
        "heart" to Icons.Filled.Favorite,
        "fitness" to Icons.Filled.FitnessCenter,
        "movie" to Icons.Filled.Movie,
        "music" to Icons.Filled.MusicNote,
        "games" to Icons.Filled.SportsEsports,
        "flight" to Icons.Filled.Flight,
        "school" to Icons.Filled.School,
        "child" to Icons.Filled.ChildCare,
        "pets" to Icons.Filled.Pets,
        "beauty" to Icons.Filled.Brush,
        "insurance" to Icons.Filled.Security,
        "work" to Icons.Filled.Work,
        "store" to Icons.Filled.Store,
        "laptop" to Icons.Filled.Laptop,
        "trending" to Icons.AutoMirrored.Filled.TrendingUp,
        "bank" to Icons.Filled.AccountBalance,
        "savings" to Icons.Filled.Savings,
        "payments" to Icons.Filled.Payments,
        "gift" to Icons.Filled.CardGiftcard,
        "more" to Icons.Filled.MoreHoriz,
    )

    fun of(key: String): ImageVector = all[key] ?: Icons.Filled.MoreHoriz
}

object CategoryColors {
    val palette: List<Int> = listOf(
        0xFF2E9E6B, 0xFF0B8A5F, 0xFF1FA3B5, 0xFF3D7DD8, 0xFF5468E0, 0xFF7A5BD1,
        0xFF9C4DCC, 0xFFD9487C, 0xFFE04848, 0xFFE07A3F, 0xFFE0A526, 0xFFC79A2E,
        0xFF8A9A3B, 0xFF6D7F78, 0xFF8B6B52, 0xFF7D8A86,
    ).map { it.toInt() }
}

@Composable
fun CategoryBadge(iconKey: String, color: Int, modifier: Modifier = Modifier, size: Dp = 42.dp) {
    val tint = Color(color)
    val alpha = if (Moneta.colors.isDark) 0.24f else 0.14f
    Box(
        modifier = modifier
            .size(size)
            .background(tint.copy(alpha = alpha), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = CategoryIcons.of(iconKey),
            contentDescription = null,
            tint = if (Moneta.colors.isDark) lighten(tint) else tint,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

private fun lighten(c: Color): Color = Color(
    red = c.red + (1f - c.red) * 0.25f,
    green = c.green + (1f - c.green) * 0.25f,
    blue = c.blue + (1f - c.blue) * 0.25f,
    alpha = 1f,
)
