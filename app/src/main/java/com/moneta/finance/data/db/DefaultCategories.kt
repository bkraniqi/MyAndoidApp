package com.moneta.finance.data.db

import com.moneta.finance.domain.TransactionType

object DefaultCategories {
    private fun c(name: String, type: TransactionType, icon: String, color: Long, order: Int, system: Boolean = false) =
        CategoryEntity(name = name, type = type, iconKey = icon, color = color.toInt(), isSystem = system, sortOrder = order)

    fun all(): List<CategoryEntity> = expense() + income()

    fun expense(): List<CategoryEntity> {
        val t = TransactionType.EXPENSE
        return listOf(
            c("Food & groceries", t, "groceries", 0xFF2E9E6BL, 1),
            c("Restaurants & coffee", t, "restaurant", 0xFFE07A3FL, 2),
            c("Transportation & fuel", t, "fuel", 0xFF3D7DD8L, 3),
            c("Shopping", t, "shopping", 0xFFD9487CL, 4),
            c("Bills & utilities", t, "bolt", 0xFFE0A526L, 5),
            c("Rent & housing", t, "home", 0xFF7A5BD1L, 6),
            c("Health", t, "health", 0xFFE04848L, 7),
            c("Entertainment", t, "movie", 0xFF9C4DCCL, 8),
            c("Travel", t, "flight", 0xFF1FA3B5L, 9),
            c("Education", t, "school", 0xFF5468E0L, 10),
            c("Other", t, "more", 0xFF7D8A86L, 99, system = true),
        )
    }

    fun income(): List<CategoryEntity> {
        val t = TransactionType.INCOME
        return listOf(
            c("Salary", t, "work", 0xFF0B8A5FL, 1),
            c("Business income", t, "store", 0xFF2F7FCFL, 2),
            c("Freelance", t, "laptop", 0xFF8E59D6L, 3),
            c("Investments", t, "trending", 0xFFC79A2EL, 4),
            c("Gifts", t, "gift", 0xFFD9487CL, 5),
            c("Other income", t, "more", 0xFF7D8A86L, 99, system = true),
        )
    }
}
