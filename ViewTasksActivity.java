package com.example.agendaapp;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.kizitonwose.calendar.core.CalendarDay;
import com.kizitonwose.calendar.core.CalendarMonth;
import com.kizitonwose.calendar.core.DayPosition;
import com.kizitonwose.calendar.view.CalendarView;
import com.kizitonwose.calendar.view.MonthDayBinder;
import com.kizitonwose.calendar.view.MonthHeaderFooterBinder;
import com.kizitonwose.calendar.view.ViewContainer;

import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import android.content.res.Configuration;

import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import android.widget.LinearLayout;

import android.widget.RadioGroup;
import android.widget.RadioButton;


public class ViewTasksActivity extends AppCompatActivity {
    private TaskAdapter adapter;
    private RecyclerView recyclerView;
    private boolean isInSelectionMode = false;
    private ImageButton deleteSelectedButton;
    private ImageButton addTaskButton;
    private ImageButton archiveButton;

    // New buttons for calendar view
    private ImageButton calendarAddTaskButton;
    private ImageButton calendarDeleteButton;

    // New buttons for list view
    private ImageButton listAddTaskButton;
    private ImageButton listDeleteButton;
    private ImageButton viewToggleButtonInList;
    private ImageButton archiveButtonInList;

    // Filter views
    private TextView filterAll;
    private TextView filterCompleted;
    private TextView filterIncomplete;
    private TextView filterDated;
    private TextView filterUndated;

    // Current filter
    private TaskFilter currentFilter = TaskFilter.ALL;

    // Calendar view toggle variables
    private boolean isCalendarViewVisible = false;
    private ImageButton viewToggleButton;
    private View tasksContainer;
    private View calendarContainer;

    // Theme toggle button
    private ImageButton themeToggleButton;
    private SharedPreferences themePrefs;

    // Calendar view components
    private CalendarView calendarView;
    private TextView monthYearText;
    private TextView selectedDateText;
    private RecyclerView calendarTasksRecyclerView;
    private TaskAdapter calendarTaskAdapter;

    // Currently selected date in calendar
    private LocalDate selectedDate = LocalDate.now();
    private LocalDate today = LocalDate.now();

    // Set to keep track of days with tasks
    private Set<LocalDate> daysWithTasks = new HashSet<>();

    // Filter enum
    private enum TaskFilter {
        ALL,
        COMPLETED,
        INCOMPLETE,
        DATED,
        UNDATED
    }

    // Month header container class
    public class MonthViewContainer extends ViewContainer {
        public final ViewGroup titlesContainer;

        public MonthViewContainer(View view) {
            super(view);
            titlesContainer = view.findViewById(R.id.calendarHeaderContainer);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize theme preferences FIRST
        themePrefs = getSharedPreferences("theme_prefs", MODE_PRIVATE);

        // Apply saved theme preference BEFORE setContentView()
        String savedTheme = themePrefs.getString("theme_mode", "system");
        switch (savedTheme) {
            case "light":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case "dark":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case "system":
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }

        setContentView(R.layout.activity_view_tasks);

        // Check if app was opened via notification click
        Intent intent = getIntent();
        if (intent.getBooleanExtra("notification_clicked", false)) {
            int taskId = intent.getIntExtra("task_id", -1);
            String notificationType = intent.getStringExtra("notification_type");
            long notificationTime = intent.getLongExtra("notification_timestamp", 0);

            // Track that this specific notification was clicked
            trackNotificationClick(taskId, notificationType, notificationTime);
        }

        // Setup RecyclerView
        recyclerView = findViewById(R.id.recyclerTasks);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Initialize adapter
        adapter = new TaskAdapter();
        adapter.setArchiveMode(false); // Make sure we're in regular mode, not archive mode
        recyclerView.setAdapter(adapter);

        // Initialize buttons
        deleteSelectedButton = findViewById(R.id.deleteSelectedButton);
        addTaskButton = findViewById(R.id.addTaskButton);
        archiveButton = findViewById(R.id.archiveButton);
        themeToggleButton = findViewById(R.id.themeToggleButton);

        // Initialize calendar view buttons
        calendarAddTaskButton = findViewById(R.id.calendarAddTaskButton);
        calendarDeleteButton = findViewById(R.id.calendarDeleteButton);

        // Initialize list view buttons
        listAddTaskButton = findViewById(R.id.listAddTaskButton);
        listDeleteButton = findViewById(R.id.listDeleteButton);
        viewToggleButtonInList = findViewById(R.id.viewToggleButtonInList);
        archiveButtonInList = findViewById(R.id.archiveButtonInList);

        // Initialize notification settings button
        ImageButton notificationSettingsButton = findViewById(R.id.notificationSettingsButton);
        if (notificationSettingsButton != null) {
            notificationSettingsButton.setOnClickListener(v -> showNotificationSettingsDialog());
        }

        // Initialize filter views
        filterAll = findViewById(R.id.filterAll);
        filterCompleted = findViewById(R.id.filterCompleted);
        filterIncomplete = findViewById(R.id.filterIncomplete);
        filterDated = findViewById(R.id.filterDated);
        filterUndated = findViewById(R.id.filterUndated);

        // Set filter click listeners
        filterAll.setOnClickListener(v -> setFilter(TaskFilter.ALL));
        filterCompleted.setOnClickListener(v -> setFilter(TaskFilter.COMPLETED));
        filterIncomplete.setOnClickListener(v -> setFilter(TaskFilter.INCOMPLETE));
        filterDated.setOnClickListener(v -> setFilter(TaskFilter.DATED));
        filterUndated.setOnClickListener(v -> setFilter(TaskFilter.UNDATED));

        updateFilterUI();

        // Set up theme toggle button with null check
        if (themeToggleButton != null) {
            // Use static gear icon instead of toggling icons
            themeToggleButton.setImageResource(R.drawable.ic_palette);
            themeToggleButton.setOnClickListener(v -> showThemeDialog());
        }

        // Set selection mode listener
        adapter.setSelectionModeChangeListener(new TaskAdapter.OnSelectionModeChangeListener() {
            @Override
            public void onSelectionModeChanged(boolean isInSelectionMode, int selectedCount) {
                ViewTasksActivity.this.isInSelectionMode = isInSelectionMode;
                updateSelectionUI(isInSelectionMode, selectedCount);
            }
        });

        // Set up delete selected button click listener
        deleteSelectedButton.setOnClickListener(v -> {
            adapter.deleteSelectedTasks(this);
        });

        // Set up calendar delete button click listener
        calendarDeleteButton.setOnClickListener(v -> {
            calendarTaskAdapter.deleteSelectedTasks(this);

            // After deletion, update the calendar view to reflect changes
            updateDaysWithTasks();
            if (calendarView != null) {
                calendarView.notifyCalendarChanged();
            }

            // Refresh tasks for the selected date
            loadTasksForLocalDate(selectedDate);
        });

        // Set up list delete button click listener
        listDeleteButton.setOnClickListener(v -> {
            adapter.deleteSelectedTasks(this);
        });

        // Set up archive button listeners
        archiveButton.setOnClickListener(v -> {
            startActivity(new Intent(ViewTasksActivity.this, ArchiveActivity.class));
        });

        archiveButtonInList.setOnClickListener(v -> {
            startActivity(new Intent(ViewTasksActivity.this, ArchiveActivity.class));
        });

        // Set up add task button click listener
        addTaskButton.setOnClickListener(v -> {
            if (!isInSelectionMode) {
                showQuickAddTaskDialog();
            }
        });

        // Set up calendar add task button click listener
        calendarAddTaskButton.setOnClickListener(v -> {
            if (!isInSelectionMode) {
                showQuickAddTaskDialog();
            }
        });

        // Set up list add task button click listener
        listAddTaskButton.setOnClickListener(v -> {
            if (!isInSelectionMode) {
                showQuickAddTaskDialog();
            }
        });

        // Setup swipe to delete
        SwipeToDeleteCallback swipeToDeleteCallback = new SwipeToDeleteCallback(adapter, this, true, false);
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(swipeToDeleteCallback);
        itemTouchHelper.attachToRecyclerView(recyclerView);

        // Initialize toggle views
        viewToggleButton = findViewById(R.id.viewToggleButton);
        tasksContainer = findViewById(R.id.tasksContainer);

        // Set up toggle button listeners
        viewToggleButton.setOnClickListener(v -> toggleView());
        viewToggleButtonInList.setOnClickListener(v -> toggleView());

        // Find calendar view components
        calendarContainer = findViewById(R.id.calendarContainer);
        calendarView = findViewById(R.id.calendarView);
        monthYearText = findViewById(R.id.monthYearText);
        selectedDateText = findViewById(R.id.selectedDateText);
        calendarTasksRecyclerView = findViewById(R.id.calendarTasksRecyclerView);

        // Set up calendar recycler view
        if (calendarTasksRecyclerView != null) {
            calendarTasksRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            calendarTaskAdapter = new TaskAdapter();
            calendarTaskAdapter.setArchiveMode(false);
            calendarTasksRecyclerView.setAdapter(calendarTaskAdapter);

            // Set selection mode listener for calendar task adapter
            calendarTaskAdapter.setSelectionModeChangeListener(new TaskAdapter.OnSelectionModeChangeListener() {
                @Override
                public void onSelectionModeChanged(boolean isInSelectionMode, int selectedCount) {
                    ViewTasksActivity.this.isInSelectionMode = isInSelectionMode;
                    updateCalendarSelectionUI(isInSelectionMode, selectedCount);
                }
            });
        }

        // Set up new calendar if it exists
        if (calendarView != null) {
            setupCalendar();
        }

        // Load tasks with initial filter
        loadTasks();

        // Check if we should show the quick add dialog immediately
        if (getIntent().getBooleanExtra("showQuickAddDialog", false)) {
            // Use post to ensure the activity is fully created before showing dialog
            recyclerView.post(() -> showQuickAddTaskDialog());
        }

        // Find and mark days with tasks
        updateDaysWithTasks();

        // Add this near the end of onCreate after initializing the buttons
        // Hide original header buttons since we now use the in-list buttons
        archiveButton.setVisibility(View.GONE);
        viewToggleButton.setVisibility(View.GONE);  // Always hide the original toggle button

        // Initialize the calendar view toggle button
        ImageButton calendarViewToggleButton = findViewById(R.id.calendarViewToggleButton);
        if (calendarViewToggleButton != null) {
            calendarViewToggleButton.setOnClickListener(v -> toggleView());
        }
    }

    private void showThemeDialog() {
        // Create dialog
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_theme_selector);

        // Allow dialog to close when clicking outside
        dialog.setCanceledOnTouchOutside(true);

        // Set dialog window properties
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(null);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = getResources().getDimensionPixelSize(R.dimen.dialog_width);
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
        }

        // Get the option views
        LinearLayout lightModeOption = dialog.findViewById(R.id.lightModeOption);
        LinearLayout darkModeOption = dialog.findViewById(R.id.darkModeOption);
        LinearLayout systemModeOption = dialog.findViewById(R.id.systemModeOption);

        // Get text views for color changes
        TextView lightText = lightModeOption.findViewById(R.id.lightText);
        TextView darkText = darkModeOption.findViewById(R.id.darkText);
        TextView systemText = systemModeOption.findViewById(R.id.systemText);

        // Track selected theme
        final String[] selectedTheme = {null};

        // Set initial highlight based on current saved theme
        String savedTheme = themePrefs.getString("theme_mode", "system");
        switch (savedTheme) {
            case "light":
                lightText.setTextColor(Color.parseColor("#FCD57E"));
                darkText.setTextColor(getResources().getColor(R.color.task_title_text_color, getTheme()));
                systemText.setTextColor(getResources().getColor(R.color.task_title_text_color, getTheme()));
                break;
            case "dark":
                lightText.setTextColor(getResources().getColor(R.color.task_title_text_color, getTheme()));
                darkText.setTextColor(Color.parseColor("#2B84AA"));
                systemText.setTextColor(getResources().getColor(R.color.task_title_text_color, getTheme()));
                break;
            case "system":
            default:
                lightText.setTextColor(getResources().getColor(R.color.task_title_text_color, getTheme()));
                darkText.setTextColor(getResources().getColor(R.color.task_title_text_color, getTheme()));
                systemText.setTextColor(Color.parseColor("#4CAF50"));
                break;
        }

        // Set click listeners
        lightModeOption.setOnClickListener(view -> {
            // Pulse animation
            view.animate()
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .setDuration(150)
                    .withEndAction(() -> {
                        view.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(150);
                    });

            // Reset all text colors first
            lightText.setTextColor(getResources().getColor(R.color.task_title_text_color, getTheme()));
            darkText.setTextColor(getResources().getColor(R.color.task_title_text_color, getTheme()));
            systemText.setTextColor(getResources().getColor(R.color.task_title_text_color, getTheme()));

            // Change only this text color to match icon
            lightText.setTextColor(Color.parseColor("#FCD57E"));

            // Save selection
            selectedTheme[0] = "light";
        });

        darkModeOption.setOnClickListener(view -> {
            // Pulse animation
            view.animate()
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .setDuration(150)
                    .withEndAction(() -> {
                        view.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(150);
                    });

            // Reset all text colors first
            lightText.setTextColor(getResources().getColor(R.color.task_title_text_color, getTheme()));
            darkText.setTextColor(getResources().getColor(R.color.task_title_text_color, getTheme()));
            systemText.setTextColor(getResources().getColor(R.color.task_title_text_color, getTheme()));

            // Change only this text color to match icon
            darkText.setTextColor(Color.parseColor("#2B84AA"));

            // Save selection
            selectedTheme[0] = "dark";
        });

        systemModeOption.setOnClickListener(view -> {
            // Pulse animation
            view.animate()
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .setDuration(150)
                    .withEndAction(() -> {
                        view.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(150);
                    });

            // Reset all text colors first
            lightText.setTextColor(getResources().getColor(R.color.task_title_text_color, getTheme()));
            darkText.setTextColor(getResources().getColor(R.color.task_title_text_color, getTheme()));
            systemText.setTextColor(getResources().getColor(R.color.task_title_text_color, getTheme()));

            // Change only this text color to match icon
            systemText.setTextColor(Color.parseColor("#4CAF50"));

            // Save selection
            selectedTheme[0] = "system";
        });

        // Apply theme when dialog is dismissed
        dialog.setOnDismissListener(dialogInterface -> {
            if (selectedTheme[0] != null) {
                switch (selectedTheme[0]) {
                    case "light":
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                        themePrefs.edit().putString("theme_mode", "light").apply();
                        break;
                    case "dark":
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                        themePrefs.edit().putString("theme_mode", "dark").apply();
                        break;
                    case "system":
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                        themePrefs.edit().putString("theme_mode", "system").apply();
                        break;
                }
                recreate();
            }
        });

        dialog.show();
    }

    // Public method for TaskAdapter to call after undo operations
    public void refreshCalendarView() {
        // Only refresh if calendar view is visible
        if (isCalendarViewVisible) {
            // Update days with tasks
            updateDaysWithTasks();

            // Refresh calendar
            if (calendarView != null) {
                calendarView.notifyCalendarChanged();
            }

            // Reload tasks for the selected date
            loadTasksForLocalDate(selectedDate);
        }
    }

    private void setupCalendar() {
        YearMonth currentMonth = YearMonth.now();
        YearMonth startMonth = currentMonth.minusMonths(12);
        YearMonth endMonth = currentMonth.plusMonths(36);

        DayOfWeek firstDayOfWeek = WeekFields.of(Locale.getDefault()).getFirstDayOfWeek();

        calendarView.setup(startMonth, endMonth, firstDayOfWeek);
        calendarView.scrollToMonth(currentMonth);

        // Set day binder
        calendarView.setDayBinder(new MonthDayBinder<DayViewContainer>() {
            @Override
            public DayViewContainer create(View view) {
                return new DayViewContainer(view);
            }

            @Override
            public void bind(DayViewContainer container, CalendarDay day) {
                container.textView.setText(String.valueOf(day.getDate().getDayOfMonth()));

                // Set text color based on day position
                if (day.getPosition() == DayPosition.MonthDate) {
                    container.textView.setTextColor(getResources().getColor(R.color.task_title_text_color, getTheme()));

                    // Priority: Selected date first, then today's date
                    if (day.getDate().equals(selectedDate)) {
                        // If this is the selected date, use yellow background
                        container.textView.setBackgroundResource(R.drawable.selected_day_background);
                    }
                    // If not selected but is today's date, use green background
                    else if (day.getDate().equals(today)) {
                        container.textView.setBackgroundResource(R.drawable.current_day_background);
                    } else {
                        container.textView.setBackground(null);
                    }

                    // Show task indicator if this day has tasks
                    LocalDate localDate = day.getDate();
                    if (daysWithTasks.contains(localDate)) {
                        container.taskIndicator.setVisibility(View.VISIBLE);
                    } else {
                        container.taskIndicator.setVisibility(View.GONE);
                    }

                } else {
                    // Dates from another month
                    container.textView.setTextColor(Color.parseColor("#C0BBDDFF"));
                    container.textView.setBackground(null);
                    container.taskIndicator.setVisibility(View.GONE);
                }

                // Handle click event
                container.textView.setOnClickListener(v -> {
                    if (day.getPosition() == DayPosition.MonthDate) {
                        // Deselect previous selection
                        calendarView.notifyDateChanged(selectedDate);

                        // Update selected date
                        selectedDate = day.getDate();

                        // Update UI for selected date
                        calendarView.notifyDateChanged(selectedDate);

                        // Update month/year text and task list
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault());
                        selectedDateText.setText("Tasks for " + selectedDate.format(formatter));

                        // Load tasks for selected date
                        loadTasksForLocalDate(selectedDate);
                    }
                });
            }
        });

        // Set month header binder for weekday labels
        calendarView.setMonthHeaderBinder(new MonthHeaderFooterBinder<MonthViewContainer>() {
            @Override
            public MonthViewContainer create(View view) {
                return new MonthViewContainer(view);
            }

            @Override
            public void bind(MonthViewContainer container, CalendarMonth month) {
                // If you want to use container.titlesContainer, make sure you have the
                // proper layout with a ViewGroup with the correct ID
                if (container.titlesContainer.getTag() == null) {
                    container.titlesContainer.setTag(month.getYearMonth());

                    // Add weekday headers - e.g., S, M, T, W, T, F, S
                    int daysInWeek = 7;
                    for (int i = 0; i < daysInWeek; i++) {
                        DayOfWeek dayOfWeek = WeekFields.of(Locale.getDefault())
                                .getFirstDayOfWeek().plus(i);
                        TextView textView = (TextView)container.titlesContainer.getChildAt(i);
                        String title = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault());
                        // Shorten to first letter if needed
                        title = title.charAt(0) + "";
                        textView.setText(title);
                    }
                }
            }
        });

        // Set month scrolled listener
        calendarView.setMonthScrollListener(calendarMonth -> {
            YearMonth month = calendarMonth.getYearMonth();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault());
            monthYearText.setText(month.format(formatter));
            return null;
        });

        // Set initial month/year text
        YearMonth month = YearMonth.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault());
        monthYearText.setText(month.format(formatter));

        // Set initial selected date text
        DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault());
        selectedDateText.setText("Tasks for " + selectedDate.format(dayFormatter));

        // Load tasks for initial date
        loadTasksForLocalDate(selectedDate);
    }

    // Make this method public so it can be called from TaskAdapter
    public void updateDaysWithTasks() {
        // Clear existing days
        daysWithTasks.clear();

        // Get all tasks with dates
        List<Task> datedTasks = TaskDatabase.getInstance(this).taskDao().getDatedTasks();

        // Add each task date to the set
        for (Task task : datedTasks) {
            if (task.dateTime > 0) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(task.dateTime);

                LocalDate taskDate = LocalDate.of(
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH) + 1,
                        cal.get(Calendar.DAY_OF_MONTH)
                );

                daysWithTasks.add(taskDate);
            }
        }

        // Refresh calendar if visible
        if (isCalendarViewVisible && calendarView != null) {
            calendarView.notifyCalendarChanged();
        }
    }

    private void loadTasksForLocalDate(LocalDate date) {
        // Convert LocalDate to Calendar milliseconds
        Calendar calendar = Calendar.getInstance();
        calendar.set(date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth(), 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long startTime = calendar.getTimeInMillis();

        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        long endTime = calendar.getTimeInMillis();

        // Get tasks for this date range
        List<Task> tasksForDate = TaskDatabase.getInstance(this).taskDao().getTasksForDateRange(startTime, endTime);

        // Extra safety: Filter out any tasks without valid dates
        List<Task> validDatedTasks = new ArrayList<>();
        for (Task task : tasksForDate) {
            if (task.dateTime > 0) {
                validDatedTasks.add(task);
            }
        }

        // Update adapter with only valid dated tasks
        if (calendarTaskAdapter != null) {
            calendarTaskAdapter.setTasks(validDatedTasks);
        }
    }

    private void toggleView() {
        isCalendarViewVisible = !isCalendarViewVisible;

        if (isCalendarViewVisible) {
            // Check if we can find the container
            if (calendarContainer == null) {
                Toast.makeText(this, "Error: Calendar container is null", Toast.LENGTH_LONG).show();
                return;
            }

            tasksContainer.setVisibility(View.GONE);
            calendarContainer.setVisibility(View.VISIBLE);

            // Change to list icon and keep visible when in calendar view
            //viewToggleButton.setImageResource(R.drawable.ic_list);
            //viewToggleButton.setVisibility(View.VISIBLE);

            // Hide archive button
            archiveButton.setVisibility(View.GONE);

            // Rest of your calendar view code...
            isInSelectionMode = false;
            calendarAddTaskButton.setVisibility(View.VISIBLE);
            calendarDeleteButton.setVisibility(View.GONE);

            // Initialize calendar if needed
            if (calendarView != null) {
                try {
                    setupCalendar();
                } catch (Exception e) {
                    Toast.makeText(this, "Error initializing calendar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }

            updateDaysWithTasks();
            if (calendarView != null) {
                calendarView.notifyDateChanged(selectedDate);
            }
            loadTasksForLocalDate(selectedDate);
        } else {
            // Switch to list view
            tasksContainer.setVisibility(View.VISIBLE);
            calendarContainer.setVisibility(View.GONE);

            // Update the list view toggle button icon
            viewToggleButtonInList.setImageResource(R.drawable.ic_calendar);

            // Hide original header buttons when in list view
            viewToggleButton.setVisibility(View.GONE);
            archiveButton.setVisibility(View.GONE);

            // Reset selection mode when switching views
            isInSelectionMode = false;
            updateSelectionUI(false, 0);

            // Refresh task list
            loadTasks();
        }
    }

    public void goToAddTask(View view) {
        // Only allow adding tasks when not in selection mode
        if (!isInSelectionMode) {
            showQuickAddTaskDialog();
        }
    }

    private void showQuickAddTaskDialog() {
        // Create custom dialog
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_quick_add_task);

        // Set dialog width to match your existing popups but allow height to adjust
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(null);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = getResources().getDimensionPixelSize(R.dimen.dialog_width);
            // Use WRAP_CONTENT for height to allow dialog to expand
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
        }

        // Initialize dialog elements
        final EditText taskNameInput = dialog.findViewById(R.id.editTextTaskName);
        final TextView dueDateText = dialog.findViewById(R.id.dueDateText);
        final TextView dueTimeText = dialog.findViewById(R.id.dueTimeText);
        final Button setDateButton = dialog.findViewById(R.id.setDateButton);
        final Button cancelButton = dialog.findViewById(R.id.cancelButton);
        final Button saveButton = dialog.findViewById(R.id.saveButton);

        // Initially disable the setDate and save buttons
        setDateButton.setEnabled(false);
        saveButton.setEnabled(false);

        // Apply alpha to show disabled state visually
        setDateButton.setAlpha(0.5f);
        saveButton.setAlpha(0.5f);

        // Add a text change listener to ensure dialog resizes as text grows
        // and to enable/disable buttons based on text input
        taskNameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                // Force the dialog to recalculate its size based on content
                if (dialog.getWindow() != null) {
                    dialog.getWindow().setLayout(
                            getResources().getDimensionPixelSize(R.dimen.dialog_width),
                            WindowManager.LayoutParams.WRAP_CONTENT
                    );
                }

                // Enable/disable buttons based on whether text is entered
                boolean hasText = !s.toString().trim().isEmpty();
                setDateButton.setEnabled(hasText);
                saveButton.setEnabled(hasText);

                // Update visual appearance
                setDateButton.setAlpha(hasText ? 1.0f : 0.5f);
                saveButton.setAlpha(hasText ? 1.0f : 0.5f);
            }
        });

        // Variables to store selected date/time
        final Calendar[] selectedDateTime = {Calendar.getInstance()};
        final boolean[] hasDateTime = {false};

        // Set initial date from calendar view if in calendar mode
        if (isCalendarViewVisible) {
            // Set date from the selected date in calendar
            Calendar cal = Calendar.getInstance();
            cal.set(selectedDate.getYear(), selectedDate.getMonthValue() - 1, selectedDate.getDayOfMonth());
            selectedDateTime[0] = cal;

            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
            dueDateText.setText(dateFormat.format(selectedDateTime[0].getTime()));
            hasDateTime[0] = true;
        }

        // Set up date button click listener
        setDateButton.setOnClickListener(v -> {
            // Create the date picker
            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select Date")
                    .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                    .setTheme(R.style.MaterialCalendarTheme)
                    .build();

            // Set up the callback when date is selected
            datePicker.addOnPositiveButtonClickListener(selection -> {
                // Handle the date selection
                selectedDateTime[0] = Calendar.getInstance();

                // FIXED: Handle UTC time properly to prevent day shift
                // MaterialDatePicker gives dates at midnight UTC
                // Extract date components from UTC time and set them correctly in local time
                Calendar utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                utcCalendar.setTimeInMillis(selection);

                // Extract the year, month and day from the UTC calendar
                int year = utcCalendar.get(Calendar.YEAR);
                int month = utcCalendar.get(Calendar.MONTH);
                int dayOfMonth = utcCalendar.get(Calendar.DAY_OF_MONTH);

                // Set the date components in the local calendar
                selectedDateTime[0].set(Calendar.YEAR, year);
                selectedDateTime[0].set(Calendar.MONTH, month);
                selectedDateTime[0].set(Calendar.DAY_OF_MONTH, dayOfMonth);
                // Reset time to beginning of day
                selectedDateTime[0].set(Calendar.HOUR_OF_DAY, 0);
                selectedDateTime[0].set(Calendar.MINUTE, 0);
                selectedDateTime[0].set(Calendar.SECOND, 0);
                selectedDateTime[0].set(Calendar.MILLISECOND, 0);

                // Display the selected date
                SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
                dueDateText.setText(dateFormat.format(selectedDateTime[0].getTime()));

                // Now show time picker
                MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                        .setTimeFormat(TimeFormat.CLOCK_12H)
                        .setHour(12)
                        .setMinute(0)
                        .setTitleText("Select Time")
                        .setTheme(R.style.MaterialTimePickerTheme)
                        .build();

                timePicker.addOnPositiveButtonClickListener(timeV -> {
                    selectedDateTime[0].set(Calendar.HOUR_OF_DAY, timePicker.getHour());
                    selectedDateTime[0].set(Calendar.MINUTE, timePicker.getMinute());

                    // Display the selected time on a separate line
                    SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
                    dueTimeText.setText(timeFormat.format(selectedDateTime[0].getTime()));
                    dueTimeText.setVisibility(View.VISIBLE);

                    hasDateTime[0] = true;
                });

                timePicker.show(getSupportFragmentManager(), "TIME_PICKER");
            });

            datePicker.show(getSupportFragmentManager(), "DATE_PICKER");
        });

        // Set up cancel button
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        // Set up save button
        saveButton.setOnClickListener(v -> {
            // Get task name
            String taskName = taskNameInput.getText().toString().trim();
            if (taskName.isEmpty()) {
                Toast.makeText(ViewTasksActivity.this, "Task name cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            // Create new task
            Task newTask = new Task();
            newTask.title = taskName;
            newTask.description = "";
            newTask.isCompleted = false;
            newTask.createdAt = System.currentTimeMillis();

            // Set due date if selected
            if (hasDateTime[0]) {
                newTask.dateTime = selectedDateTime[0].getTimeInMillis();
            } else {
                newTask.dateTime = -1;
            }

            // Save task to database and get the generated ID
            long taskId = TaskDatabase.getInstance(ViewTasksActivity.this).taskDao().insert(newTask);

            // Set the ID on the task object
            newTask.id = (int) taskId;

            // Now schedule notifications with the correct ID
            adapter.scheduleNotification(ViewTasksActivity.this, newTask);

            // Update days with tasks
            updateDaysWithTasks();

            // Refresh the appropriate view
            if (isCalendarViewVisible) {
                loadTasksForLocalDate(selectedDate);
            } else {
                loadTasks();
            }

            // Dismiss dialog
            dialog.dismiss();

            Toast.makeText(ViewTasksActivity.this, "Task added", Toast.LENGTH_SHORT).show();
        });

        // Show the dialog
        dialog.show();

        // Set focus to the task name input field
        taskNameInput.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Refresh task data
        updateDaysWithTasks();

        // Update the appropriate view
        if (isCalendarViewVisible) {
            loadTasksForLocalDate(selectedDate);
            if (calendarTaskAdapter != null && calendarTaskAdapter.isInSelectionMode()) {
                calendarTaskAdapter.refreshSelectionState();
            }
        } else {
            loadTasks();
            if (adapter != null && adapter.isInSelectionMode()) {
                adapter.refreshSelectionState();
            }
        }
    }

    // Public method to load tasks so TaskAdapter can call it when editing tasks
    public void loadTasks() {
        List<Task> tasks;

        // Load tasks based on current filter
        switch (currentFilter) {
            case COMPLETED:
                tasks = TaskDatabase.getInstance(this).taskDao().getCompletedTasks();
                break;
            case INCOMPLETE:
                tasks = TaskDatabase.getInstance(this).taskDao().getIncompleteTasks();
                break;
            case DATED:
                tasks = TaskDatabase.getInstance(this).taskDao().getDatedTasks();
                break;
            case UNDATED:
                tasks = TaskDatabase.getInstance(this).taskDao().getUndatedTasks();
                break;
            case ALL:
            default:
                tasks = TaskDatabase.getInstance(this).taskDao().getSortedTasks();
                break;
        }

        adapter.setTasks(tasks);
    }

    private void setFilter(TaskFilter filter) {
        // Update current filter
        currentFilter = filter;

        // Update filter UI
        updateFilterUI();

        // Reload tasks with new filter
        loadTasks();
    }

    private void updateFilterUI() {
        // Reset all filters to normal state
        filterAll.setBackgroundResource(R.drawable.filter_chip_normal);
        filterAll.setTextColor(getResources().getColor(R.color.filter_chip_normal_text, getTheme()));

        filterCompleted.setBackgroundResource(R.drawable.filter_chip_normal);
        filterCompleted.setTextColor(getResources().getColor(R.color.filter_chip_normal_text, getTheme()));

        filterIncomplete.setBackgroundResource(R.drawable.filter_chip_normal);
        filterIncomplete.setTextColor(getResources().getColor(R.color.filter_chip_normal_text, getTheme()));

        filterDated.setBackgroundResource(R.drawable.filter_chip_normal);
        filterDated.setTextColor(getResources().getColor(R.color.filter_chip_normal_text, getTheme()));

        filterUndated.setBackgroundResource(R.drawable.filter_chip_normal);
        filterUndated.setTextColor(getResources().getColor(R.color.filter_chip_normal_text, getTheme()));

        // Highlight selected filter
        switch (currentFilter) {
            case ALL:
                filterAll.setBackgroundResource(R.drawable.filter_chip_selected);
                filterAll.setTextColor(Color.parseColor("#2B84AA"));
                break;
            case COMPLETED:
                filterCompleted.setBackgroundResource(R.drawable.filter_chip_selected);
                filterCompleted.setTextColor(Color.parseColor("#2B84AA"));
                break;
            case INCOMPLETE:
                filterIncomplete.setBackgroundResource(R.drawable.filter_chip_selected);
                filterIncomplete.setTextColor(Color.parseColor("#2B84AA"));
                break;
            case DATED:
                filterDated.setBackgroundResource(R.drawable.filter_chip_selected);
                filterDated.setTextColor(Color.parseColor("#2B84AA"));
                break;
            case UNDATED:
                filterUndated.setBackgroundResource(R.drawable.filter_chip_selected);
                filterUndated.setTextColor(Color.parseColor("#2B84AA"));
                break;
        }
    }

    private void updateSelectionUI(boolean isInSelectionMode, int selectedCount) {
        // Original buttons
        deleteSelectedButton.setVisibility(View.GONE);

        // Only hide viewToggleButton if not in calendar view
        if (!isCalendarViewVisible) {
            viewToggleButton.setVisibility(View.GONE);
        }

        archiveButton.setVisibility(View.GONE);

        // New list view buttons visibility
        if (isInSelectionMode) {
            listAddTaskButton.setVisibility(View.GONE);
            listDeleteButton.setVisibility(View.VISIBLE);
            archiveButtonInList.setVisibility(View.GONE); // Hide archive button in selection mode
        } else {
            listAddTaskButton.setVisibility(View.VISIBLE);
            listDeleteButton.setVisibility(View.GONE);
            archiveButtonInList.setVisibility(View.VISIBLE); // Show archive button when not in selection mode
        }
    }

    // Method to update the calendar view buttons based on selection mode
    // Method to update the calendar view buttons based on selection mode
    private void updateCalendarSelectionUI(boolean isInSelectionMode, int selectedCount) {
        if (isInSelectionMode) {
            calendarAddTaskButton.setVisibility(View.GONE);
            calendarDeleteButton.setVisibility(View.VISIBLE);
        } else {
            calendarAddTaskButton.setVisibility(View.VISIBLE);
            calendarDeleteButton.setVisibility(View.GONE);

            // When exiting selection mode, refresh the calendar to update task indicators
            updateDaysWithTasks();
            if (calendarView != null) {
                calendarView.notifyCalendarChanged();
            }

            // Refresh the tasks for the selected date
            loadTasksForLocalDate(selectedDate);
        }
    }

    @Override
    public void onBackPressed() {
        if (isInSelectionMode) {
            // Exit selection mode when back is pressed
            if (isCalendarViewVisible) {
                calendarTaskAdapter.exitSelectionMode();
            } else {
                adapter.exitSelectionMode();
            }
        } else if (isCalendarViewVisible) {
            // Switch back to task list view when in calendar view
            toggleView();
        } else {
            super.onBackPressed();
        }
    }

    // Method to check if calendar view is currently visible
    public boolean isCalendarViewVisible() {
        return isCalendarViewVisible;
    }

    private void showNotificationSettingsDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_notification_settings);

        // Set dialog window properties
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(null);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = getResources().getDimensionPixelSize(R.dimen.dialog_width);
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
        }

        // Get current setting
        SharedPreferences prefs = getSharedPreferences("notification_prefs", MODE_PRIVATE);
        String currentMode = prefs.getString("notification_mode", "none");

        // Set up radio buttons
        RadioGroup radioGroup = dialog.findViewById(R.id.notificationRadioGroup);
        RadioButton radioNone = dialog.findViewById(R.id.radioNone);
        RadioButton radioAll = dialog.findViewById(R.id.radioAll);
        RadioButton radioPinnedDated = dialog.findViewById(R.id.radioPinnedDated);

        // Set current selection
        switch (currentMode) {
            case "none":
                radioNone.setChecked(true);
                break;
            case "all":
                radioAll.setChecked(true);
                break;
            case "pinned_dated":
                radioPinnedDated.setChecked(true);
                break;
        }

        // Set up buttons
        Button cancelButton = dialog.findViewById(R.id.cancelButton);
        Button saveButton = dialog.findViewById(R.id.saveButton);

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        saveButton.setOnClickListener(v -> {
            String selectedMode = "none";
            int checkedId = radioGroup.getCheckedRadioButtonId();

            if (checkedId == R.id.radioAll) {
                selectedMode = "all";
            } else if (checkedId == R.id.radioPinnedDated) {
                selectedMode = "pinned_dated";
            }

            // Save setting
            prefs.edit().putString("notification_mode", selectedMode).apply();
            Toast.makeText(this, "Notification settings saved", Toast.LENGTH_SHORT).show();

            // Reschedule notifications
            rescheduleAllNotifications();

            dialog.dismiss();
        });

        dialog.show();
    }

    private void rescheduleAllNotifications() {
        List<Task> allTasks = TaskDatabase.getInstance(this).taskDao().getSortedTasks();
        for (Task task : allTasks) {
            if (!task.isCompleted) {
                adapter.scheduleNotification(this, task);
            }
        }
    }
    private void trackNotificationClick(int taskId, String notificationType, long notificationTime) {
        SharedPreferences analyticsPrefs = getSharedPreferences("notification_analytics", MODE_PRIVATE);

        // Record the click
        analyticsPrefs.edit()
                .putLong("clicked_" + taskId + "_" + notificationType, System.currentTimeMillis())
                .putLong("notification_sent_" + taskId + "_" + notificationType, notificationTime)
                .apply();

        // Update click effectiveness counters
        String clickCountKey = "notification_click_count_" + notificationType;
        int currentCount = analyticsPrefs.getInt(clickCountKey, 0);
        analyticsPrefs.edit()
                .putInt(clickCountKey, currentCount + 1)
                .apply();

        // Track user's active hours based on when they click notifications
        updateUserActiveHours();
    }

    private void updateUserActiveHours() {
        SharedPreferences analyticsPrefs = getSharedPreferences("notification_analytics", MODE_PRIVATE);

        // Track what hour user is most active (for optimal notification timing)
        Calendar cal = Calendar.getInstance();
        int currentHour = cal.get(Calendar.HOUR_OF_DAY);

        String hourKey = "active_hour_" + currentHour;
        int hourCount = analyticsPrefs.getInt(hourKey, 0);
        analyticsPrefs.edit()
                .putInt(hourKey, hourCount + 1)
                .apply();
    }
}