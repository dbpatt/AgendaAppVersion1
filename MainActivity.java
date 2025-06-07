package com.example.agendaapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void goToAddTask(View view) {
        // Instead of going to AddTaskActivity, go to ViewTasks
        // and add an extra to indicate we want to show the quick add dialog
        Intent intent = new Intent(this, ViewTasksActivity.class);
        intent.putExtra("showQuickAddDialog", true);
        startActivity(intent);
    }

    public void goToViewTasks(View view) {
        Intent intent = new Intent(this, ViewTasksActivity.class);
        startActivity(intent);
    }
}