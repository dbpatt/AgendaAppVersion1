package com.example.agendaapp;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface TaskDao {
    @Insert
    long insert(Task task);  // Changed from void to long to return generated ID

    @Update
    void update(Task task);

    @Delete
    void delete(Task task);

    // Get task by ID
    @Query("SELECT * FROM task WHERE id = :taskId")
    Task getTaskById(int taskId);

    // Get undated incomplete tasks
    @Query("SELECT * FROM task WHERE dateTime = -1 AND isCompleted = 0 AND isArchived = 0")
    List<Task> getUndatedIncompleteTasks();

    // Updated query to prioritize pinned tasks
    @Query("SELECT * FROM Task WHERE isArchived = 0 ORDER BY " +
            "isPinned DESC, " +  // First sort by pinned status
            "CASE WHEN dateTime > 0 THEN 0 ELSE 1 END, " +
            "CASE WHEN dateTime > 0 THEN dateTime ELSE createdAt END ASC")
    List<Task> getSortedTasks();

    // Get completed tasks (non-archived)
    @Query("SELECT * FROM Task WHERE isArchived = 0 AND isCompleted = 1 ORDER BY " +
            "isPinned DESC, " +  // First sort by pinned status
            "CASE WHEN dateTime > 0 THEN 0 ELSE 1 END, " +
            "CASE WHEN dateTime > 0 THEN dateTime ELSE createdAt END ASC")
    List<Task> getCompletedTasks();

    // Get incomplete tasks (non-archived)
    @Query("SELECT * FROM Task WHERE isArchived = 0 AND isCompleted = 0 ORDER BY " +
            "isPinned DESC, " +  // First sort by pinned status
            "CASE WHEN dateTime > 0 THEN 0 ELSE 1 END, " +
            "CASE WHEN dateTime > 0 THEN dateTime ELSE createdAt END ASC")
    List<Task> getIncompleteTasks();

    // Get dated tasks (non-archived)
    @Query("SELECT * FROM Task WHERE isArchived = 0 AND dateTime > 0 ORDER BY isPinned DESC, dateTime ASC")
    List<Task> getDatedTasks();

    // Get undated tasks (non-archived)
    @Query("SELECT * FROM Task WHERE isArchived = 0 AND dateTime <= 0 ORDER BY isPinned DESC, createdAt ASC")
    List<Task> getUndatedTasks();

    // Get tasks for date range
    @Query("SELECT * FROM Task WHERE isArchived = 0 AND dateTime BETWEEN :startTime AND :endTime ORDER BY isPinned DESC, dateTime ASC")
    List<Task> getTasksForDateRange(long startTime, long endTime);

    // Get all archived tasks
    @Query("SELECT * FROM Task WHERE isArchived = 1 ORDER BY " +
            "isPinned DESC, " +  // First sort by pinned status
            "CASE WHEN dateTime > 0 THEN 0 ELSE 1 END, " +
            "CASE WHEN dateTime > 0 THEN dateTime ELSE createdAt END ASC")
    List<Task> getArchivedTasks();

    // Archive a task by ID
    @Query("UPDATE Task SET isArchived = 1 WHERE id = :taskId")
    void archiveTask(int taskId);

    // Unarchive a task by ID
    @Query("UPDATE Task SET isArchived = 0 WHERE id = :taskId")
    void unarchiveTask(int taskId);

    // Permanently delete all archived tasks
    @Query("DELETE FROM Task WHERE isArchived = 1")
    void deleteAllArchived();

    // Helper method to count pinned tasks
    @Query("SELECT COUNT(*) FROM Task WHERE isArchived = 0 AND isPinned = 1")
    int getPinnedCount();
}