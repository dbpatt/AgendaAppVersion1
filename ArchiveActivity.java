package com.example.agendaapp;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ArchiveActivity extends AppCompatActivity implements TaskAdapter.OnSelectionModeChangeListener {
    private RecyclerView recyclerView;
    private TaskAdapter adapter;
    private TextView emptyArchiveView;
    private ImageButton deleteSelectedButton;
    private boolean isInSelectionMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_archive);

        recyclerView = findViewById(R.id.recyclerArchived);
        emptyArchiveView = findViewById(R.id.emptyArchiveView);

        // Set up back button
        ImageButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new TaskAdapter();
        adapter.setArchiveMode(true); // Important: Set archive mode flag
        adapter.setSelectionModeChangeListener(this);
        recyclerView.setAdapter(adapter);

        // Setup button for multi-selection delete action
        deleteSelectedButton = findViewById(R.id.deleteSelectedButton);

        deleteSelectedButton.setOnClickListener(v -> {
            if (adapter.isInSelectionMode()) {
                adapter.deleteSelectedTasks(this);
            }
        });

        // Setup swipe to restore (right swipe)
        // In ArchiveActivity.java when setting up the swipe callback:
        SwipeToRestoreCallback swipeToRestoreCallback = new SwipeToRestoreCallback(adapter, this, recyclerView);
        ItemTouchHelper restoreHelper = new ItemTouchHelper(swipeToRestoreCallback);
        restoreHelper.attachToRecyclerView(recyclerView);

        // Load archived tasks
        loadArchivedTasks();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadArchivedTasks();
    }

    private void loadArchivedTasks() {
        List<Task> archivedTasks = TaskDatabase.getInstance(this).taskDao().getArchivedTasks();
        adapter.setTasks(archivedTasks);

        // Show/hide empty state view
        if (archivedTasks.isEmpty()) {
            emptyArchiveView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyArchiveView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onSelectionModeChanged(boolean isInSelectionMode, int selectedCount) {
        this.isInSelectionMode = isInSelectionMode;

        if (isInSelectionMode) {
            deleteSelectedButton.setVisibility(View.VISIBLE);
        } else {
            deleteSelectedButton.setVisibility(View.GONE);
        }
    }

    @Override
    public void onBackPressed() {
        if (isInSelectionMode) {
            // Exit selection mode when back is pressed
            adapter.exitSelectionMode();
        } else {
            super.onBackPressed();
        }
    }
}