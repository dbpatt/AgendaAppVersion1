package com.example.agendaapp;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Task.class}, version = 4)  // Update version to 4
public abstract class TaskDatabase extends RoomDatabase {
    private static volatile TaskDatabase instance;

    // Define the migration from version 1 to 2
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Add the isArchived column with a default value of 0 (false)
            database.execSQL("ALTER TABLE Task ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0");
        }
    };

    // Define the migration from version 2 to 3
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Add the description column with a default empty string
            database.execSQL("ALTER TABLE Task ADD COLUMN description TEXT DEFAULT ''");
        }
    };

    // Define the migration from version 3 to 4
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Add the isPinned column with a default value of 0 (false)
            database.execSQL("ALTER TABLE Task ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0");
        }
    };

    public abstract TaskDao taskDao();

    public static synchronized TaskDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            TaskDatabase.class,
                            "task_db"
                    )
                    .allowMainThreadQueries()
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)  // Add the new migration
                    .build();
        }
        return instance;
    }
}