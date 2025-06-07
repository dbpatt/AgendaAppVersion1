package com.example.agendaapp;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Ignore;

@Entity
public class Task {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String title;
    public String description; // Field for task description
    public long dateTime; // -1 if no date/time selected
    public boolean isCompleted;
    public long createdAt; // used for sorting undated tasks

    public boolean isArchived = false;

    // Add the isPinned field
    public boolean isPinned = false;

    @Ignore // Ignore this field for Room database
    public boolean isSelected = false; // Used for multi-selection functionality

    // Default no-arg constructor for Room
    public Task() {
    }

    // Primary constructor with description - this will be used by Room
    public Task(String title, String description, long dateTime, boolean isCompleted, long createdAt) {
        this.title = title;
        this.description = description;
        this.dateTime = dateTime;
        this.isCompleted = isCompleted;
        this.createdAt = createdAt;
    }

    // Add a constructor without description for backward compatibility
    @Ignore // Mark with @Ignore so Room doesn't get confused about which constructor to use
    public Task(String title, long dateTime, boolean isCompleted, long createdAt) {
        this(title, "", dateTime, isCompleted, createdAt);
    }
}