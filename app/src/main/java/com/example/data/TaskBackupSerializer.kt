package com.example.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ImportBackupSummary(
    val importedCount: Int,
    val replacedPreviousCount: Int,
    val removedTaskIds: List<String>
)

object TaskBackupSerializer {
    private const val APP_IDENTIFIER = "PrismTask"
    private const val SCHEMA_VERSION = 1

    fun suggestedBackupFileName(nowMillis: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(nowMillis))
        return "prismtask-backup-$stamp.json"
    }

    fun encode(tasks: List<TaskItem>, exportedAtMillis: Long = System.currentTimeMillis()): String {
        val root = JSONObject().apply {
            put("app", APP_IDENTIFIER)
            put("version", SCHEMA_VERSION)
            put("exportedAtMillis", exportedAtMillis)
            val tasksArray = JSONArray()
            tasks.forEach { task ->
                val subtasksArray = JSONArray()
                task.subtasks.forEach { sub ->
                    subtasksArray.put(
                        JSONObject().apply {
                            put("id", sub.id)
                            put("title", sub.title)
                            put("done", sub.done)
                        }
                    )
                }
                tasksArray.put(
                    JSONObject().apply {
                        put("id", task.id)
                        put("title", task.title)
                        put("notes", task.notes)
                        put("completed", task.completed)
                        put("colorKey", task.colorKey)
                        put("dueDateMillis", task.dueDateMillis ?: JSONObject.NULL)
                        put("reminderMillis", task.reminderMillis ?: JSONObject.NULL)
                        put("repeatRule", task.repeatRule)
                        put("repeatInterval", task.repeatInterval)
                        put("subtasks", subtasksArray)
                        put("createdAtMillis", task.createdAtMillis)
                        put("updatedAtMillis", task.updatedAtMillis)
                    }
                )
            }
            put("tasks", tasksArray)
        }
        return root.toString(2)
    }

    fun decode(rawJson: String): Result<List<TaskItem>> {
        return try {
            val trimmed = rawJson.trim()
            if (trimmed.isEmpty()) {
                return Result.failure(IllegalArgumentException("The selected backup file is empty."))
            }

            val root = JSONObject(trimmed)
            if (!root.has("tasks")) {
                return Result.failure(
                    IllegalArgumentException("This file is not a valid PrismTask backup.")
                )
            }
            val tasksArray = root.optJSONArray("tasks")
                ?: return Result.failure(
                    IllegalArgumentException("Backup file contains an invalid tasks list.")
                )

            val now = System.currentTimeMillis()
            val parsedTasks = buildList(tasksArray.length()) {
                for (i in 0 until tasksArray.length()) {
                    val obj = tasksArray.optJSONObject(i) ?: continue
                    val cleanTitle = obj.optString("title", "")
                        .trim()
                        .take(TaskRepository.MAX_TITLE_LENGTH)
                    if (cleanTitle.isEmpty()) continue

                    val cleanNotes = obj.optString("notes", "")
                        .trim()
                        .take(TaskRepository.MAX_NOTES_LENGTH)

                    val rawId = obj.optString("id", "").trim().take(64)
                    val taskId = rawId.ifEmpty {
                        "task_${UUID.randomUUID().toString().replace("-", "").take(16)}"
                    }

                    val color = TaskColor.fromKey(obj.optString("colorKey", TaskColor.CORAL.name))
                    val repeatRule = RepeatRule.fromKey(obj.optString("repeatRule", RepeatRule.NONE.name))
                    val repeatInterval = obj.optLong("repeatInterval", 1L).coerceIn(1L, 365L)

                    val dueDateMillis = obj.optNullablePositiveLong("dueDateMillis")
                    val reminderMillis = obj.optNullablePositiveLong("reminderMillis")
                    val createdAt = obj.optNullablePositiveLong("createdAtMillis") ?: now
                    val updatedAt = obj.optNullablePositiveLong("updatedAtMillis") ?: now

                    val subtasksArray = obj.optJSONArray("subtasks")
                    val subtasks = if (subtasksArray != null) {
                        buildList(subtasksArray.length().coerceAtMost(TaskRepository.MAX_SUBTASKS)) {
                            for (s in 0 until subtasksArray.length()) {
                                if (size >= TaskRepository.MAX_SUBTASKS) break
                                val subObj = subtasksArray.optJSONObject(s) ?: continue
                                val subTitle = subObj.optString("title", "")
                                    .trim()
                                    .take(TaskRepository.MAX_SUBTASK_TITLE_LENGTH)
                                if (subTitle.isEmpty()) continue
                                val subId = subObj.optString("id", "")
                                    .trim()
                                    .take(64)
                                    .ifEmpty { "sub_${UUID.randomUUID().toString().take(8)}" }
                                add(
                                    SubtaskItem(
                                        id = subId,
                                        title = subTitle,
                                        done = subObj.optBoolean("done", false)
                                    )
                                )
                            }
                        }
                    } else {
                        emptyList()
                    }

                    add(
                        TaskItem(
                            id = taskId,
                            title = cleanTitle,
                            notes = cleanNotes,
                            completed = obj.optBoolean("completed", false),
                            colorKey = color.name,
                            dueDateMillis = dueDateMillis,
                            reminderMillis = reminderMillis,
                            repeatRule = repeatRule.name,
                            repeatInterval = repeatInterval,
                            subtasks = subtasks,
                            createdAtMillis = createdAt,
                            updatedAtMillis = updatedAt
                        )
                    )
                }
            }

            Result.success(parsedTasks)
        } catch (e: Exception) {
            Result.failure(
                IllegalArgumentException("Couldn't read backup file. Please choose a valid PrismTask JSON backup.")
            )
        }
    }

    private fun JSONObject.optNullablePositiveLong(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        val value = optLong(key, -1L)
        return value.takeIf { it > 0L }
    }
}
