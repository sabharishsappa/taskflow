package com.example.data

enum class TaskPriority(val label: String, val sortRank: Int) {
    HIGH("High", 3),
    MEDIUM("Medium", 2),
    LOW("Low", 1),
    NONE("None", 0);

    companion object {
        fun fromName(raw: String?): TaskPriority =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: NONE
    }
}

enum class TaskRecurrence(val label: String) {
    NONE("No repeat"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly");

    companion object {
        fun fromName(raw: String?): TaskRecurrence =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: NONE
    }
}

data class SubTask(
    val id: String,
    val title: String,
    val isCompleted: Boolean = false,
    val dueDateMillis: Long? = null,
    val priority: TaskPriority = TaskPriority.NONE
)

data class TaskItem(
    val id: String = "",
    val title: String = "",
    val notes: String = "",
    val dueDateMillis: Long? = null,
    val priority: TaskPriority = TaskPriority.NONE,
    val category: String = "",
    val recurrence: TaskRecurrence = TaskRecurrence.NONE,
    val subTasks: List<SubTask> = emptyList(),
    val isCompleted: Boolean = false,
    val completedAtMillis: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val completedSubTaskCount: Int
        get() = subTasks.count { it.isCompleted }
}

object TaskConstraints {
    const val MAX_TITLE_LENGTH = 180
    const val MAX_NOTES_LENGTH = 5_000
    const val MAX_CATEGORY_LENGTH = 30
    const val MAX_SUBTASKS = 20
    const val MAX_SUBTASK_LENGTH = 140

    val DEFAULT_CATEGORIES = listOf("Personal", "Work", "Health", "Errands", "Finance")
}
