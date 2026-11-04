package com.moneta.finance.data

import com.moneta.finance.data.db.CategoryBudgetEntity
import com.moneta.finance.data.db.CategoryEntity
import com.moneta.finance.data.db.RecurringRuleEntity
import com.moneta.finance.data.db.SettingEntity
import com.moneta.finance.data.db.TransactionEntity
import com.moneta.finance.domain.Frequency
import com.moneta.finance.domain.Money
import com.moneta.finance.domain.PaymentMethod
import com.moneta.finance.domain.TransactionType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class BackupFormatException(message: String) : Exception(message)

data class BackupData(
    val settings: List<SettingEntity>,
    val categories: List<CategoryEntity>,
    val recurringRules: List<RecurringRuleEntity>,
    val transactions: List<TransactionEntity>,
    val budgets: List<CategoryBudgetEntity>,
)

/**
 * Lossless JSON representation of the whole ledger. Amounts are written as integer minor
 * units so a backup restores to exactly the same values.
 */
object BackupCodec {
    const val FORMAT = "moneta-backup"
    const val VERSION = 1

    fun encode(data: BackupData, exportedAtMillis: Long): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("exportedAt", exportedAtMillis)
        root.put("settings", JSONArray().apply {
            data.settings.forEach { put(JSONObject().put("key", it.key).put("value", it.value)) }
        })
        root.put("categories", JSONArray().apply {
            data.categories.forEach {
                put(
                    JSONObject()
                        .put("id", it.id).put("name", it.name).put("type", it.type.name)
                        .put("iconKey", it.iconKey).put("color", it.color)
                        .put("isSystem", it.isSystem).put("sortOrder", it.sortOrder)
                )
            }
        })
        root.put("recurringRules", JSONArray().apply {
            data.recurringRules.forEach {
                put(
                    JSONObject()
                        .put("id", it.id).put("type", it.type.name).put("amountMinor", it.amountMinor)
                        .put("categoryId", it.categoryId).put("description", it.description)
                        .put("paymentMethod", it.paymentMethod.name).put("notes", it.notes)
                        .put("frequency", it.frequency.name).put("startEpochDay", it.startEpochDay)
                        .put("endEpochDay", it.endEpochDay ?: JSONObject.NULL)
                        .put("generatedCount", it.generatedCount).put("active", it.active)
                        .put("createdAt", it.createdAt)
                )
            }
        })
        root.put("transactions", JSONArray().apply {
            data.transactions.forEach {
                put(
                    JSONObject()
                        .put("id", it.id).put("type", it.type.name).put("amountMinor", it.amountMinor)
                        .put("categoryId", it.categoryId).put("epochDay", it.epochDay)
                        .put("description", it.description).put("paymentMethod", it.paymentMethod.name)
                        .put("notes", it.notes)
                        .put("recurringRuleId", it.recurringRuleId ?: JSONObject.NULL)
                        .put("occurrenceIndex", it.occurrenceIndex ?: JSONObject.NULL)
                        .put("createdAt", it.createdAt).put("updatedAt", it.updatedAt)
                )
            }
        })
        root.put("budgets", JSONArray().apply {
            data.budgets.forEach {
                put(JSONObject().put("id", it.id).put("categoryId", it.categoryId).put("amountMinor", it.amountMinor))
            }
        })
        return root.toString(2)
    }

    /** Parses and fully validates a backup; nothing is written unless this succeeds. */
    fun decode(json: String): BackupData {
        try {
            val root = JSONObject(json)
            if (root.optString("format") != FORMAT) throw BackupFormatException("This file is not a Moneta backup")
            val version = root.optInt("version", -1)
            if (version < 1 || version > VERSION) throw BackupFormatException("Unsupported backup version $version")

            val settings = root.getJSONArray("settings").objects().map {
                SettingEntity(it.getString("key"), it.getString("value"))
            }.filter { it.key in AppSettings.knownKeys }

            val categories = root.getJSONArray("categories").objects().map {
                CategoryEntity(
                    id = it.getLong("id"),
                    name = it.getString("name").trim().ifEmpty { throw BackupFormatException("Category without a name") },
                    type = enumOf<TransactionType>(it.getString("type")),
                    iconKey = it.getString("iconKey"),
                    color = it.getInt("color"),
                    isSystem = it.getBoolean("isSystem"),
                    sortOrder = it.optInt("sortOrder", 0),
                )
            }
            val categoryById = categories.associateBy { it.id }
            if (categoryById.size != categories.size) throw BackupFormatException("Duplicate category ids")
            for (type in TransactionType.entries) {
                if (categories.none { it.type == type && it.isSystem }) {
                    throw BackupFormatException("Backup is missing the default '${type.name.lowercase()}' category")
                }
            }

            fun checkCategory(id: Long, type: TransactionType, what: String) {
                val c = categoryById[id] ?: throw BackupFormatException("$what refers to a missing category")
                if (c.type != type) throw BackupFormatException("$what uses a category of the wrong type")
            }

            val rules = root.getJSONArray("recurringRules").objects().map {
                RecurringRuleEntity(
                    id = it.getLong("id"),
                    type = enumOf(it.getString("type")),
                    amountMinor = it.getLong("amountMinor"),
                    categoryId = it.getLong("categoryId"),
                    description = it.optString("description", ""),
                    paymentMethod = enumOf(it.getString("paymentMethod")),
                    notes = it.optString("notes", ""),
                    frequency = enumOf<Frequency>(it.getString("frequency")),
                    startEpochDay = it.getLong("startEpochDay"),
                    endEpochDay = if (it.isNull("endEpochDay")) null else it.getLong("endEpochDay"),
                    generatedCount = it.getInt("generatedCount"),
                    active = it.getBoolean("active"),
                    createdAt = it.getLong("createdAt"),
                ).also { r ->
                    checkAmount(r.amountMinor)
                    checkCategory(r.categoryId, r.type, "A recurring transaction")
                    if (r.generatedCount < 0) throw BackupFormatException("Invalid recurring state")
                }
            }
            val ruleIds = rules.map { it.id }.toSet()
            if (ruleIds.size != rules.size) throw BackupFormatException("Duplicate recurring ids")

            val transactions = root.getJSONArray("transactions").objects().map {
                TransactionEntity(
                    id = it.getLong("id"),
                    type = enumOf(it.getString("type")),
                    amountMinor = it.getLong("amountMinor"),
                    categoryId = it.getLong("categoryId"),
                    epochDay = it.getLong("epochDay"),
                    description = it.optString("description", ""),
                    paymentMethod = enumOf<PaymentMethod>(it.getString("paymentMethod")),
                    notes = it.optString("notes", ""),
                    recurringRuleId = if (it.isNull("recurringRuleId")) null else it.getLong("recurringRuleId"),
                    occurrenceIndex = if (it.isNull("occurrenceIndex")) null else it.getInt("occurrenceIndex"),
                    createdAt = it.getLong("createdAt"),
                    updatedAt = it.getLong("updatedAt"),
                ).also { t ->
                    checkAmount(t.amountMinor)
                    checkCategory(t.categoryId, t.type, "A transaction")
                    if (t.recurringRuleId != null && t.recurringRuleId !in ruleIds) {
                        throw BackupFormatException("A transaction refers to a missing recurring rule")
                    }
                }
            }
            if (transactions.map { it.id }.toSet().size != transactions.size) throw BackupFormatException("Duplicate transaction ids")
            val occurrences = transactions.filter { it.recurringRuleId != null }.map { it.recurringRuleId to it.occurrenceIndex }
            if (occurrences.toSet().size != occurrences.size) throw BackupFormatException("Duplicate recurring occurrences")

            val budgets = root.getJSONArray("budgets").objects().map {
                CategoryBudgetEntity(it.getLong("id"), it.getLong("categoryId"), it.getLong("amountMinor")).also { b ->
                    checkAmount(b.amountMinor)
                    checkCategory(b.categoryId, TransactionType.EXPENSE, "A budget")
                }
            }
            if (budgets.map { it.categoryId }.toSet().size != budgets.size) throw BackupFormatException("Duplicate budgets")

            return BackupData(settings, categories, rules, transactions, budgets)
        } catch (e: JSONException) {
            throw BackupFormatException("The backup file is damaged: ${e.message}")
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException("The backup file contains invalid values")
        }
    }

    private fun checkAmount(amount: Long) {
        if (amount <= 0 || amount > Money.MAX_AMOUNT_MINOR) throw BackupFormatException("The backup contains an invalid amount")
    }

    private inline fun <reified T : Enum<T>> enumOf(name: String): T = enumValueOf(name)

    private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
}
