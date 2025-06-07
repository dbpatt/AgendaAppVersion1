package com.example.agendaapp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.os.Handler;
import android.os.Looper;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import android.view.GestureDetector;
import android.widget.EditText;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.HorizontalScrollView;
import android.text.Editable;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import java.util.Calendar;
import java.util.TimeZone;
import androidx.appcompat.app.AppCompatActivity;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {
    private static final String TAG = "TaskAdapter";
    private List<Task> tasks = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault());
    private List<Integer> itemsPendingDelete = new ArrayList<>();
    private final int deleteButtonWidth = 80; // Same width as in SwipeToDeleteCallback
    private final int TASK_ITEM_HEIGHT = 72; // Consistent height in dp for all items
    private final Handler handler = new Handler(Looper.getMainLooper());
    private RecyclerView recyclerView; // Add this to track the RecyclerView instance
    private boolean isArchiveMode = false; // Add this to track if we're in archive view

    // Multi-select support
    private boolean isSelectionMode = false;
    private final List<Task> selectedTasks = new ArrayList<>();
    private OnSelectionModeChangeListener selectionModeChangeListener;

    // Interface for notifying selection mode changes
    public interface OnSelectionModeChangeListener {
        void onSelectionModeChanged(boolean isInSelectionMode, int selectedCount);
    }

    // Add this method to set the listener
    public void setSelectionModeChangeListener(OnSelectionModeChangeListener listener) {
        this.selectionModeChangeListener = listener;
    }

    // Set archive mode flag
    public void setArchiveMode(boolean isArchiveMode) {
        this.isArchiveMode = isArchiveMode;
    }

    // Add these methods to manage the selection mode
    public void enterSelectionMode(int initialPosition) {
        if (!isSelectionMode) {
            isSelectionMode = true;
            // Clear any pending deletions
            clearPendingDeletions();
            // Select the initial item
            if (initialPosition >= 0 && initialPosition < tasks.size()) {
                Task task = tasks.get(initialPosition);
                task.isSelected = true;
                selectedTasks.add(task);
            }
            notifyDataSetChanged();
            if (selectionModeChangeListener != null) {
                selectionModeChangeListener.onSelectionModeChanged(true, selectedTasks.size());
            }
        }
    }

    public void exitSelectionMode() {
        if (isSelectionMode) {
            isSelectionMode = false;
            // Clear all selections
            for (Task task : tasks) {
                task.isSelected = false;
            }
            selectedTasks.clear();
            notifyDataSetChanged();
            if (selectionModeChangeListener != null) {
                selectionModeChangeListener.onSelectionModeChanged(false, 0);
            }
        }
    }

    public void checkAndExitSelectionMode() {
        Log.d("TaskAdapter", "checkAndExitSelectionMode: isSelectionMode=" + isSelectionMode + ", selectedTasks.size=" + selectedTasks.size());
        if (isSelectionMode && selectedTasks.isEmpty()) {
            Log.d("TaskAdapter", "Exiting selection mode");
            isSelectionMode = false;
            if (selectionModeChangeListener != null) {
                selectionModeChangeListener.onSelectionModeChanged(false, 0);
            }
            notifyDataSetChanged();
        }
    }

    public boolean isInSelectionMode() {
        return isSelectionMode;
    }

    public void refreshSelectionState() {
        if (!selectedTasks.isEmpty()) {
            // Ensure selection mode flag is set
            isSelectionMode = true;

            // Restore visual selection state from selectedTasks list
            for (Task task : tasks) {
                task.isSelected = false; // Reset all first
            }

            for (Task selectedTask : selectedTasks) {
                for (Task task : tasks) {
                    if (task.id == selectedTask.id) {
                        task.isSelected = true;
                        break;
                    }
                }
            }

            notifyDataSetChanged();
        }
    }

    public void toggleSelection(int position) {
        if (isSelectionMode && position >= 0 && position < tasks.size()) {
            Task task = tasks.get(position);
            task.isSelected = !task.isSelected;

            if (task.isSelected) {
                selectedTasks.add(task);
            } else {
                // Remove by ID instead of object reference
                selectedTasks.removeIf(selectedTask -> selectedTask.id == task.id);
            }

            notifyItemChanged(position);

            // Force check if we should exit selection mode
            checkAndExitSelectionMode();

            // If still in selection mode, notify listener of count change
            if (isSelectionMode && selectionModeChangeListener != null) {
                selectionModeChangeListener.onSelectionModeChanged(true, selectedTasks.size());
            }
        }
    }

    public List<Task> getSelectedTasks() {
        return new ArrayList<>(selectedTasks);
    }

    public void deleteSelectedTasks(Context context) {
        if (!selectedTasks.isEmpty()) {
            // Create a copy of the selected tasks for potential undo
            final List<Task> tasksToDelete = new ArrayList<>(selectedTasks);

            if (isArchiveMode) {
                // Permanently delete tasks from archive
                for (Task task : tasksToDelete) {
                    TaskDatabase.getInstance(context).taskDao().delete(task);
                }

                // Update the adapter with fresh data
                setTasks(TaskDatabase.getInstance(context).taskDao().getArchivedTasks());

                // Exit selection mode
                exitSelectionMode();

                // Show confirmation toast
                String message = tasksToDelete.size() + " task" + (tasksToDelete.size() > 1 ? "s" : "") + " permanently deleted";
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            } else {
                // Archive the tasks instead of deleting them
                for (Task task : tasksToDelete) {
                    task.isArchived = true;
                    TaskDatabase.getInstance(context).taskDao().update(task);
                }

                // Update the adapter with fresh data
                setTasks(TaskDatabase.getInstance(context).taskDao().getSortedTasks());

                // Exit selection mode
                exitSelectionMode();

                // Show undo snackbar with count
                if (recyclerView != null) {
                    String message = tasksToDelete.size() + " task" + (tasksToDelete.size() > 1 ? "s" : "") + " archived";
                    com.google.android.material.snackbar.Snackbar snackbar =
                            com.google.android.material.snackbar.Snackbar.make(
                                    recyclerView, message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG);

                    // Set specific duration to 5 seconds
                    snackbar.setDuration(5000);

                    snackbar.setAction("UNDO", v -> {
                        // Restore all archived tasks
                        for (Task task : tasksToDelete) {
                            task.isArchived = false;
                            TaskDatabase.getInstance(context).taskDao().update(task);
                        }
                        // Refresh the list
                        setTasks(TaskDatabase.getInstance(context).taskDao().getSortedTasks());

                        // Refresh calendar view if we're in ViewTasksActivity
                        if (context instanceof ViewTasksActivity) {
                            ((ViewTasksActivity) context).refreshCalendarView();
                        }

                        Toast.makeText(context, "Archive cancelled", Toast.LENGTH_SHORT).show();
                    });

                    snackbar.show();
                }
            }
        }
    }

    // Method to restore tasks from archive
    public void restoreTasksFromArchive(Context context, List<Task> tasksToRestore) {
        if (!tasksToRestore.isEmpty()) {
            for (Task task : tasksToRestore) {
                task.isArchived = false;
                TaskDatabase.getInstance(context).taskDao().update(task);
            }

            // Update the adapter with fresh data
            setTasks(TaskDatabase.getInstance(context).taskDao().getArchivedTasks());

            // Show confirmation toast
            String message = tasksToRestore.size() + " task" + (tasksToRestore.size() > 1 ? "s" : "") + " restored";
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }

    public void scheduleNotification(Context context, Task task) {
        Log.d("Notifications", "scheduleNotification called for task: " + task.title);

        // Check notification preferences
        SharedPreferences prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE);
        String notificationMode = prefs.getString("notification_mode", "none");

        Log.d("Notifications", "Notification mode: " + notificationMode);
        Log.d("Notifications", "Task dateTime: " + task.dateTime);
        Log.d("Notifications", "Task isPinned: " + task.isPinned);

        if (notificationMode.equals("none")) {
            Log.d("Notifications", "Notifications disabled, returning");
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e("Notifications", "AlarmManager is null");
            return;
        }

        // Cancel existing notifications for this task
        cancelNotificationsForTask(context, task);

        // Schedule based on task type and notification preferences
        if (task.dateTime > 0 && (notificationMode.equals("all") || notificationMode.equals("pinned_dated"))) {
            Log.d("Notifications", "Scheduling smart notifications for dated task");

            long currentTime = System.currentTimeMillis();
            long dueTime = task.dateTime;
            long timeUntilDue = dueTime - currentTime;
            long daysUntilDue = timeUntilDue / (24 * 60 * 60 * 1000);

            Log.d("Notifications", "Days until due: " + daysUntilDue);

            // Smart interval scheduling based on due date distance
            if (daysUntilDue > 7) {
                // Long term task - schedule 7 days, 3 days, 1 day, 2 hours
                scheduleIfFuture(context, task, dueTime - (7 * 24 * 60 * 60 * 1000), "7_days");
                scheduleIfFuture(context, task, dueTime - (3 * 24 * 60 * 60 * 1000), "3_days");
                scheduleIfFuture(context, task, dueTime - (24 * 60 * 60 * 1000), "1_day");
                scheduleIfFuture(context, task, dueTime - (2 * 60 * 60 * 1000), "2_hours");
            } else if (daysUntilDue > 3) {
                // Medium term - schedule 3 days, 1 day, 2 hours
                scheduleIfFuture(context, task, dueTime - (3 * 24 * 60 * 60 * 1000), "3_days");
                scheduleIfFuture(context, task, dueTime - (24 * 60 * 60 * 1000), "1_day");
                scheduleIfFuture(context, task, dueTime - (2 * 60 * 60 * 1000), "2_hours");
            } else {
                // Short term - schedule 1 day, 2 hours
                scheduleIfFuture(context, task, dueTime - (24 * 60 * 60 * 1000), "1_day");
                scheduleIfFuture(context, task, dueTime - (2 * 60 * 60 * 1000), "2_hours");
            }

            // Always schedule due time notification
            scheduleIfFuture(context, task, dueTime, "due_now");

            // Track notification scheduling for learning
            trackNotificationScheduled(context, task.id, daysUntilDue);

        } else if (task.isPinned && (notificationMode.equals("all") || notificationMode.equals("pinned_dated"))) {
            Log.d("Notifications", "Scheduling daily reminder for pinned task");
            // Schedule daily reminder at user's optimal time
            scheduleDailyReminderSmart(context, task);
        } else {
            Log.d("Notifications", "No notifications scheduled - conditions not met");
        }
    }

    private void scheduleIfFuture(Context context, Task task, long triggerTime, String notificationType) {
        if (triggerTime > System.currentTimeMillis()) {
            Log.d("Notifications", "Scheduling " + notificationType + " notification");
            scheduleTaskNotification(context, task, triggerTime, notificationType);
        } else {
            Log.d("Notifications", notificationType + " notification time has passed");
        }
    }

    private void trackNotificationScheduled(Context context, int taskId, long daysUntilDue) {
        SharedPreferences analyticsPrefs = context.getSharedPreferences("notification_analytics", Context.MODE_PRIVATE);
        analyticsPrefs.edit()
                .putLong("task_" + taskId + "_scheduled_time", System.currentTimeMillis())
                .putLong("task_" + taskId + "_days_until_due", daysUntilDue)
                .apply();
    }

    private void scheduleDailyReminderSmart(Context context, Task task) {
        // Get user's optimal notification time from analytics
        SharedPreferences analyticsPrefs = context.getSharedPreferences("notification_analytics", Context.MODE_PRIVATE);
        int optimalHour = analyticsPrefs.getInt("user_optimal_hour", 9); // Default to 9 AM

        Intent intent = new Intent(context, NotificationReceiver.class);
        intent.putExtra("task_id", task.id);
        intent.putExtra("task_title", task.title);
        intent.putExtra("notification_type", "daily_pinned");

        int requestCode = task.id * 100 + 6;
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Schedule for tomorrow at user's optimal hour
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, optimalHour);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY, pendingIntent);
        }
    }

    private void trackTaskCompletion(Context context, Task task) {
        SharedPreferences analyticsPrefs = context.getSharedPreferences("notification_analytics", Context.MODE_PRIVATE);
        long currentTime = System.currentTimeMillis();
        long scheduledTime = analyticsPrefs.getLong("task_" + task.id + "_scheduled_time", 0);

        if (scheduledTime > 0) {
            // Calculate response time
            long responseTime = currentTime - scheduledTime;

            // Update user patterns
            analyticsPrefs.edit()
                    .putLong("task_" + task.id + "_completed_time", currentTime)
                    .putLong("task_" + task.id + "_response_time", responseTime)
                    .apply();

            // Update user's optimal notification hour based on completion time
            updateOptimalNotificationTime(context, currentTime);
        }
    }

    private void updateOptimalNotificationTime(Context context, long completionTime) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(completionTime);
        int completionHour = cal.get(Calendar.HOUR_OF_DAY);

        SharedPreferences analyticsPrefs = context.getSharedPreferences("notification_analytics", Context.MODE_PRIVATE);

        // Simple learning: gradually adjust optimal hour toward completion times
        int currentOptimalHour = analyticsPrefs.getInt("user_optimal_hour", 9);
        int newOptimalHour = (currentOptimalHour + completionHour) / 2;

        analyticsPrefs.edit()
                .putInt("user_optimal_hour", newOptimalHour)
                .apply();

        Log.d("NotificationAI", "Updated optimal hour to: " + newOptimalHour);
    }

    private void scheduleTaskNotification(Context context, Task task, long triggerTime, String notificationType) {
        Intent intent = new Intent(context, NotificationReceiver.class);
        intent.putExtra("task_id", task.id);
        intent.putExtra("task_title", task.title);
        intent.putExtra("notification_type", notificationType);

        // Use consistent request code mapping
        int typeCode = 0;
        switch (notificationType) {
            case "7_days": typeCode = 1; break;
            case "3_days": typeCode = 2; break;
            case "1_day": typeCode = 3; break;
            case "2_hours": typeCode = 4; break;
            case "due_now": typeCode = 5; break;
            case "daily_pinned": typeCode = 6; break;
        }
        int requestCode = task.id * 100 + typeCode;

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        }
    }

    private void scheduleDailyReminder(Context context, Task task) {
        Intent intent = new Intent(context, NotificationReceiver.class);
        intent.putExtra("task_id", task.id);
        intent.putExtra("task_title", task.title);
        intent.putExtra("notification_type", "daily_pinned");

        int requestCode = task.id * 100 + 4;
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Schedule for tomorrow at 9 AM, then repeat daily
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 9);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY, pendingIntent);
        }
    }

    private void cancelNotificationsForTask(Context context, Task task) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        // Cancel all notification types for this task (including new ones)
        String[] notificationTypes = {"7_days", "3_days", "1_day", "2_hours", "due_now", "daily_pinned"};

        for (int i = 0; i < notificationTypes.length; i++) {
            int requestCode = task.id * 100 + (i + 1);
            Intent intent = new Intent(context, NotificationReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            alarmManager.cancel(pendingIntent);
        }
    }

    // Add this override method to track the RecyclerView instance
    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recyclerView = recyclerView;
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task, parent, false);
        TaskViewHolder holder = new TaskViewHolder(view);

        // Setup long click listener to ONLY ENTER selection mode
        holder.container.setOnLongClickListener(v -> {
            int position = holder.getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION && !isSelectionMode) {
                // Only enter selection mode, don't exit with long press
                enterSelectionMode(position);
                return true;
            }
            return false;
        });

        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task task = tasks.get(position);
        boolean isDone = task.isCompleted;
        boolean isPendingDelete = itemsPendingDelete.contains(position);

        // Apply regular background based on completion state
        if (isDone) {
            holder.container.setBackgroundResource(R.drawable.task_item_background_completed);
        } else {
            holder.container.setBackgroundResource(R.drawable.task_item_background);
        }

        // Apply text color based on pinned state
        if (task.isPinned) {
            // Red color for pinned tasks
            holder.title.setTextColor(Color.parseColor("#F44336"));
            holder.dateTime.setTextColor(Color.parseColor("#F44336"));
        } else {
            // Regular colors
            holder.title.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.task_title_text_color));
            holder.dateTime.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.task_title_text_color));
        }

        // Handle selection mode UI
        if (isSelectionMode) {
            // Use the delete selector for the checkbox in selection mode
            holder.checkBox.setButtonDrawable(R.drawable.delete_selector);
            holder.checkBox.setChecked(task.isSelected);

            // Add click listener for toggling selection
            holder.container.setOnClickListener(v -> {
                toggleSelection(position);
            });

            // Apply selection state styling - use same red background as delete mode for selected items
            if (task.isSelected) {
                holder.container.setBackgroundResource(R.drawable.task_item_background_delete);
            }

            // Reset any translations
            holder.container.setTranslationX(0);

            // Reset text container to use weight
            ViewGroup.LayoutParams params = holder.textContainer.getLayoutParams();
            if (params instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams llParams = (LinearLayout.LayoutParams) params;
                llParams.width = 0;
                llParams.weight = 1;
                holder.textContainer.setLayoutParams(llParams);
            }

            // PRESERVE strikethrough for completed tasks even in selection mode
            holder.title.setPaintFlags(isDone ?
                    holder.title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG :
                    holder.title.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            holder.dateTime.setPaintFlags(isDone ?
                    holder.dateTime.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG :
                    holder.dateTime.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));

            holder.title.setAlpha(isDone ? 0.6f : 1.0f);
            holder.dateTime.setAlpha(isDone ? 0.6f : 1.0f);

            // Reset max width
            holder.title.setMaxWidth(Integer.MAX_VALUE);
            holder.dateTime.setMaxWidth(Integer.MAX_VALUE);

            // Disable checkbox functionality in selection mode
            // Make checkbox toggle selection in selection mode
            holder.checkBox.setOnClickListener(v -> {
                toggleSelection(position);
            });

        } else if (isPendingDelete) {
            // Reset checkbox to normal appearance in non-selection modes
            holder.checkBox.setButtonDrawable(R.drawable.checkbox_selector);

            // Handle pending delete state first (highest priority)
            // Calculate exact pixel width of the delete button
            int buttonWidthPx = dpToPx(holder.itemView.getContext(), deleteButtonWidth);

            // Set translation amount to match the button width
            holder.container.setTranslationX(-buttonWidthPx * -.20f);

            // Reset any strikethrough/opacity effects
            holder.title.setPaintFlags(holder.title.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            holder.dateTime.setPaintFlags(holder.dateTime.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            holder.title.setAlpha(1.0f);
            holder.dateTime.setAlpha(1.0f);

            // Make background transparent to avoid overlap
            holder.container.setBackgroundColor(Color.TRANSPARENT);

            // DIRECT APPROACH: Constrain the width of the text container
            // Calculate available width (container width minus button width minus padding/margin)
            int containerWidth = holder.container.getWidth();
            if (containerWidth > 0) {
                int paddingReserve = dpToPx(holder.itemView.getContext(), 16); // Extra padding/safety margin
                int availableWidth = containerWidth - buttonWidthPx - paddingReserve;

                // Apply width constraint to text container
                ViewGroup.LayoutParams params = holder.textContainer.getLayoutParams();
                if (params instanceof LinearLayout.LayoutParams) {
                    LinearLayout.LayoutParams llParams = (LinearLayout.LayoutParams) params;
                    llParams.width = availableWidth;
                    llParams.weight = 0; // Override weight to use exact width
                    holder.textContainer.setLayoutParams(llParams);
                }
            }

            // Force text to use ellipsize
            holder.title.setEllipsize(TextUtils.TruncateAt.END);
            holder.title.setSingleLine(true);
            holder.dateTime.setEllipsize(TextUtils.TruncateAt.END);
            holder.dateTime.setSingleLine(true);
            holder.title.setMaxWidth(holder.textContainer.getWidth());
            holder.dateTime.setMaxWidth(holder.textContainer.getWidth());

            // Set click listener to handle tapping the delete area
            final int pos = position; // Capture for lambda
            holder.container.setOnClickListener(v -> {
                // Archive instead of delete if not in archive mode
                if (isArchiveMode) {
                    // Permanently delete the task
                    deleteItem(pos);
                    TaskDatabase.getInstance(holder.itemView.getContext())
                            .taskDao().delete(tasks.get(pos));
                    List<Task> updatedTasks = TaskDatabase.getInstance(holder.itemView.getContext())
                            .taskDao().getArchivedTasks();
                    setTasks(updatedTasks);
                    Toast.makeText(holder.itemView.getContext(), "Task permanently deleted", Toast.LENGTH_SHORT).show();
                } else {
                    // Archive the task
                    Task taskToArchive = tasks.get(pos);  // Renamed variable to avoid conflict
                    taskToArchive.isArchived = true;
                    TaskDatabase.getInstance(holder.itemView.getContext()).taskDao().update(taskToArchive);
                    List<Task> updatedTasks = TaskDatabase.getInstance(holder.itemView.getContext())
                            .taskDao().getSortedTasks();
                    setTasks(updatedTasks);

                    // Show a Snackbar with undo option
                    if (recyclerView != null) {
                        com.google.android.material.snackbar.Snackbar snackbar =
                                com.google.android.material.snackbar.Snackbar.make(
                                        recyclerView, "Task archived", com.google.android.material.snackbar.Snackbar.LENGTH_LONG);

                        snackbar.setAction("UNDO", undoView -> {
                            // Unarchive the task
                            taskToArchive.isArchived = false;
                            TaskDatabase.getInstance(holder.itemView.getContext()).taskDao().update(taskToArchive);
                            setTasks(TaskDatabase.getInstance(holder.itemView.getContext())
                                    .taskDao().getSortedTasks());

                            // Refresh calendar view if we're in ViewTasksActivity
                            if (holder.itemView.getContext() instanceof ViewTasksActivity) {
                                ((ViewTasksActivity) holder.itemView.getContext()).refreshCalendarView();
                            }

                            Toast.makeText(holder.itemView.getContext(), "Task restored", Toast.LENGTH_SHORT).show();
                        });

                        snackbar.show();
                    }
                }
            });

            // IMPROVED TOUCH LISTENER FOR UNDO - COMPLETE REWRITE
            holder.container.setOnTouchListener(new View.OnTouchListener() {
                private float startX;
                private boolean isTracking = false;
                private static final float SWIPE_THRESHOLD = 50; // Threshold for undo action

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    final int pos = holder.getBindingAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return false;

                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            startX = event.getX();
                            isTracking = true;
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            if (!isTracking) return false;

                            float deltaX = event.getX() - startX;

                            // Only handle right swipes for undo
                            if (deltaX > 0) {
                                // Calculate a smooth movement with diminishing returns
                                // Start from the current delete position
                                int buttonWidthPx = dpToPx(v.getContext(), deleteButtonWidth);
                                float baseOffset = -buttonWidthPx * -.20f; // Current position in delete state

                                // Apply movement with diminishing returns for visual feedback
                                float dragFactor = 0.5f; // Makes the drag feel more responsive
                                float newTranslation = baseOffset + (deltaX * dragFactor);

                                // Cap at 0 (original position)
                                newTranslation = Math.min(newTranslation, 0);

                                // Apply the translation immediately for visual feedback
                                holder.container.setTranslationX(newTranslation);
                                return true;
                            }
                            return false;

                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            if (!isTracking) return false;
                            isTracking = false;

                            float finalDeltaX = event.getX() - startX;

                            // If swiped right past threshold, undo the deletion
                            if (finalDeltaX > SWIPE_THRESHOLD) {
                                // IMMEDIATE STATE CHANGE: Clear pending deletion flag
                                clearPendingDeletion(pos);

                                // IMMEDIATE VISUAL FEEDBACK: Force position to normal
                                holder.container.setTranslationX(0);

                                // Properly reset the view with background, etc.
                                holder.container.setBackgroundResource(
                                        task.isCompleted ?
                                                R.drawable.task_item_background_completed :
                                                R.drawable.task_item_background
                                );

                                // Reset text container layout params
                                ViewGroup.LayoutParams params = holder.textContainer.getLayoutParams();
                                if (params instanceof LinearLayout.LayoutParams) {
                                    LinearLayout.LayoutParams llParams = (LinearLayout.LayoutParams) params;
                                    llParams.width = 0;
                                    llParams.weight = 1;
                                    holder.textContainer.setLayoutParams(llParams);
                                }

                                // Show confirmation message
                                Toast.makeText(v.getContext(), "Action cancelled", Toast.LENGTH_SHORT).show();

                                return true;
                            } else {
                                // Not enough movement, snap back to delete position
                                int buttonWidthPx = dpToPx(v.getContext(), deleteButtonWidth);
                                holder.container.animate()
                                        .translationX(-buttonWidthPx * -.20f)
                                        .setDuration(150)
                                        .start();
                                return true;
                            }
                    }
                    return false;
                }
            });
        } else {
            // Reset checkbox to normal appearance in non-selection modes
            holder.checkBox.setButtonDrawable(R.drawable.checkbox_selector);

            // Regular state (not in selection mode, not pending delete)
            // Reset any translation
            holder.container.setTranslationX(0);

            // Reset text container to use weight
            ViewGroup.LayoutParams params = holder.textContainer.getLayoutParams();
            if (params instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams llParams = (LinearLayout.LayoutParams) params;
                llParams.width = 0;
                llParams.weight = 1;
                holder.textContainer.setLayoutParams(llParams);
            }

            // Strikethrough & opacity for completed tasks
            holder.title.setPaintFlags(isDone ?
                    holder.title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG :
                    holder.title.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            holder.dateTime.setPaintFlags(isDone ?
                    holder.dateTime.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG :
                    holder.dateTime.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));

            holder.title.setAlpha(isDone ? 0.6f : 1.0f);
            holder.dateTime.setAlpha(isDone ? 0.6f : 1.0f);

            // Reset max width
            holder.title.setMaxWidth(Integer.MAX_VALUE);
            holder.dateTime.setMaxWidth(Integer.MAX_VALUE);

            // IMPORTANT: Remove any touch listeners when not in delete mode
            holder.container.setOnTouchListener(null);

            // Set checkbox to completion state
            holder.checkBox.setChecked(isDone);

            // Set the checkbox click listener
            holder.checkBox.setOnClickListener(v -> {
                task.isCompleted = !task.isCompleted;
                TaskDatabase.getInstance(holder.itemView.getContext()).taskDao().update(task);

                // Cancel notifications if task is completed, schedule if uncompleted
                if (task.isCompleted) {
                    cancelNotificationsForTask(holder.itemView.getContext(), task);
                    // Track completion for learning
                    trackTaskCompletion(holder.itemView.getContext(), task);
                } else {
                    scheduleNotification(holder.itemView.getContext(), task);
                }

                notifyItemChanged(position);
            });

            // If in archive mode, handle container click to restore task
            if (isArchiveMode) {
                holder.container.setOnClickListener(v -> {
                    // Offer to restore this single task
                    if (recyclerView != null) {
                        com.google.android.material.snackbar.Snackbar snackbar =
                                com.google.android.material.snackbar.Snackbar.make(
                                        recyclerView, "Restore this task?", com.google.android.material.snackbar.Snackbar.LENGTH_LONG);

                        snackbar.setAction("RESTORE", restoreView -> {
                            // Unarchive the task
                            task.isArchived = false;
                            TaskDatabase.getInstance(holder.itemView.getContext()).taskDao().update(task);
                            setTasks(TaskDatabase.getInstance(holder.itemView.getContext())
                                    .taskDao().getArchivedTasks());
                            Toast.makeText(holder.itemView.getContext(), "Task restored", Toast.LENGTH_SHORT).show();
                        });

                        snackbar.show();
                    }
                });
            } else {
                // Use double tap to open task detail dialog
                GestureDetector gestureDetector = new GestureDetector(holder.itemView.getContext(),
                        new GestureDetector.SimpleOnGestureListener() {
                            @Override
                            public boolean onDoubleTap(MotionEvent e) {
                                showTaskDetailDialog(holder.itemView.getContext(), task);
                                return true;
                            }

                            // Add this to ensure long presses get detected properly
                            @Override
                            public void onLongPress(MotionEvent e) {
                                // Don't handle long press here - let the container's onLongClickListener handle it
                                // Just making this method explicit to avoid any default behavior
                            }

                            // Return false for single tap to allow other events to process
                            @Override
                            public boolean onSingleTapConfirmed(MotionEvent e) {
                                return false;
                            }
                        });

                holder.container.setOnTouchListener((v, event) -> {
                    // Allow the gestureDetector to process the event, but DON'T consume it
                    // so the long press can still be detected by the container's onLongClickListener
                    gestureDetector.onTouchEvent(event);
                    return false; // Return false to NOT consume the event
                });
            }
        }

        if (task.dateTime > 0) {
            holder.dateTime.setVisibility(View.VISIBLE);
            holder.dateTime.setText("Due: " + dateFormat.format(new Date(task.dateTime)));
        } else {
            holder.dateTime.setVisibility(View.GONE);
        }

        // Set title text with capitalization
        holder.title.setText(capitalizeWords(task.title));
    }

    private void showTaskDetailDialog(Context context, Task task) {
        // Inflate the dialog layout
        View dialogView = LayoutInflater.from(context).inflate(R.layout.task_detail_dialog, null);

        // Find views
        EditText titleEditText = dialogView.findViewById(R.id.dialogTitle);
        TextView dateTimeTextView = dialogView.findViewById(R.id.dialogDateTime);
        TextView dateLabel = dialogView.findViewById(R.id.dialogDateLabel);
        ImageButton clearDateButton = dialogView.findViewById(R.id.clearDateButton);
        ImageButton pinButton = dialogView.findViewById(R.id.pinButton); // Add this line
        EditText descriptionEditText = dialogView.findViewById(R.id.descriptionEditText);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        Button saveButton = dialogView.findViewById(R.id.saveButton);

        // Set up the formatting toolbar
        LinearLayout formatToolbar = dialogView.findViewById(R.id.formatToolbar);
        setupFormattingToolbar(context, formatToolbar, descriptionEditText);

        // Create a copy of the original task data in case we cancel
        final String originalTitle = task.title;
        final String originalDescription = task.description;
        final long originalDateTime = task.dateTime;
        final boolean originalIsPinned = task.isPinned; // Add this line

        // Set the task data
        titleEditText.setText(capitalizeWords(task.title));

        // Set up pin button state
        pinButton.setSelected(task.isPinned); // Add this line

        // Store the current dateTime value for updates
        final Calendar[] selectedDateTime = {Calendar.getInstance()};
        final boolean[] hasDateTime = {task.dateTime > 0};

        // Initialize the date/time field
        if (task.dateTime > 0) {
            dateTimeTextView.setText(dateFormat.format(new Date(task.dateTime)));
            dateTimeTextView.setTextColor(ContextCompat.getColor(context, R.color.task_title_text_color));

            // Set up the calendar with existing date
            selectedDateTime[0].setTimeInMillis(task.dateTime);

            // Show the clear date button
            clearDateButton.setVisibility(View.VISIBLE);
        } else {
            dateTimeTextView.setText("Set a due date");
            dateTimeTextView.setTextColor(Color.GRAY);

            // Hide the clear button if no date is set
            clearDateButton.setVisibility(View.GONE);
        }

        // Set up pin button click listener
        pinButton.setOnClickListener(v -> {
            // Check if we're trying to pin a task and already at the limit
            if (!task.isPinned) {
                // Count current pinned tasks
                int pinnedCount = 0;
                try {
                    pinnedCount = TaskDatabase.getInstance(context).taskDao().getPinnedCount();
                } catch (Exception e) {
                    // If the query fails, count manually
                    for (Task t : tasks) {
                        if (t.isPinned) pinnedCount++;
                    }
                }

                if (pinnedCount >= 6) {
                    Toast.makeText(context, "You can only pin up to 6 tasks", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            // Toggle pin state visually
            boolean newPinState = !pinButton.isSelected();
            pinButton.setSelected(newPinState);
        });

        // Set the description
        descriptionEditText.setText(task.description);

        // Make the date/time field clickable to open date/time picker
        dateTimeTextView.setOnClickListener(v -> {
            // Build the date picker
            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select date")
                    .setSelection(hasDateTime[0] ? selectedDateTime[0].getTimeInMillis() : MaterialDatePicker.todayInUtcMilliseconds())
                    .build();

            // Date picker listener
            datePicker.addOnPositiveButtonClickListener(selection -> {
                // Update the calendar with selected date, preserving the time if it exists
                Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                calendar.setTimeInMillis(selection);

                // If we have a previous time selection, keep it
                if (hasDateTime[0]) {
                    Calendar oldTime = Calendar.getInstance();
                    oldTime.setTimeInMillis(selectedDateTime[0].getTimeInMillis());
                    calendar.set(Calendar.HOUR_OF_DAY, oldTime.get(Calendar.HOUR_OF_DAY));
                    calendar.set(Calendar.MINUTE, oldTime.get(Calendar.MINUTE));
                }

                // Adjust to local timezone
                calendar.setTimeZone(Calendar.getInstance().getTimeZone());

                // Save the selection and update the UI
                selectedDateTime[0] = calendar;
                hasDateTime[0] = true;

                // Now show the time picker
                MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                        .setTimeFormat(TimeFormat.CLOCK_12H)
                        .setHour(calendar.get(Calendar.HOUR_OF_DAY))
                        .setMinute(calendar.get(Calendar.MINUTE))
                        .setTitleText("Select time")
                        .build();

                // Time picker listener
                timePicker.addOnPositiveButtonClickListener(timeView -> {
                    // Update the calendar with the selected time
                    selectedDateTime[0].set(Calendar.HOUR_OF_DAY, timePicker.getHour());
                    selectedDateTime[0].set(Calendar.MINUTE, timePicker.getMinute());

                    // Update the displayed date/time
                    dateTimeTextView.setText(dateFormat.format(new Date(selectedDateTime[0].getTimeInMillis())));
                    dateTimeTextView.setTextColor(ContextCompat.getColor(context, R.color.task_title_text_color));

                    // Make the clear button visible now that we have a date
                    clearDateButton.setVisibility(View.VISIBLE);
                });

                timePicker.show(((AppCompatActivity) context).getSupportFragmentManager(), "TIME_PICKER");
            });

            datePicker.show(((AppCompatActivity) context).getSupportFragmentManager(), "DATE_PICKER");
        });

        // Set up clear date button
        clearDateButton.setOnClickListener(v -> {
            hasDateTime[0] = false;
            dateTimeTextView.setText("Set a due date");
            dateTimeTextView.setTextColor(Color.GRAY);
            clearDateButton.setVisibility(View.GONE);
        });

        // Create the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        // Set up cancel button
        cancelButton.setOnClickListener(v -> {
            // Dismiss without saving changes
            dialog.dismiss();
        });

        // Set up save button
        saveButton.setOnClickListener(v -> {
            // Get updated values
            String newTitle = titleEditText.getText().toString().trim();

            // Validate title
            if (newTitle.isEmpty()) {
                Toast.makeText(context, "Task name cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            // Update the task with all edited fields
            task.title = newTitle;
            task.description = descriptionEditText.getText().toString();
            task.isPinned = pinButton.isSelected();

            // Update the dateTime based on user selection
            if (hasDateTime[0]) {
                task.dateTime = selectedDateTime[0].getTimeInMillis();
            } else {
                task.dateTime = -1;
            }

            // Save the updated task to the database
            TaskDatabase.getInstance(context).taskDao().update(task);

            // Reschedule notifications for the updated task
            scheduleNotification(context, task);

            // Dismiss the dialog
            dialog.dismiss();

            // Refresh the task list to reflect changes and maintain proper ordering
            if (context instanceof ViewTasksActivity) {
                ViewTasksActivity activity = (ViewTasksActivity) context;
                if (activity.isCalendarViewVisible()) {
                    // Update the days with tasks to refresh calendar indicators
                    activity.updateDaysWithTasks();

                    // Refresh the calendar view
                    activity.refreshCalendarView();
                } else {
                    // Refresh the task list
                    activity.loadTasks();
                }
            } else {
                // Just refresh the adapter if not in ViewTasksActivity
                notifyDataSetChanged();
            }

            // Show confirmation
            if (task.isPinned != originalIsPinned) {
                Toast.makeText(context, task.isPinned ? "Task pinned to top" : "Task unpinned", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "Task updated", Toast.LENGTH_SHORT).show();
            }
        });

        // Show the dialog
        dialog.show();
    }

    // Helper method to create toolbar buttons
    private ImageButton createToolbarButton(Context context, int iconResource, String contentDescription, int buttonSize, int buttonMargin) {
        ImageButton button = new ImageButton(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(buttonSize, buttonSize);
        params.setMargins(buttonMargin, buttonMargin, buttonMargin, buttonMargin);
        button.setLayoutParams(params);
        button.setImageResource(iconResource);
        button.setBackground(ContextCompat.getDrawable(context, R.drawable.format_toolbar_button));
        button.setContentDescription(contentDescription);
        return button;
    }

    // Updated formatting toolbar method with all the features
    private void setupFormattingToolbar(Context context, LinearLayout toolbar, EditText editText) {
        // Define button size and margin
        int buttonSize = dpToPx(context, 36);
        int buttonMargin = dpToPx(context, 4);

        // 1. CHECKBOX BUTTON
        ImageButton checkboxButton = createToolbarButton(context, R.drawable.ic_checkbox, "Insert checkbox", buttonSize, buttonMargin);
        checkboxButton.setOnClickListener(v -> {
            int cursorPosition = editText.getSelectionStart();
            Editable editable = editText.getText();

            // Get the current line start position
            String text = editable.toString();
            int lineStart = text.lastIndexOf('\n', cursorPosition - 1) + 1;

            // Check if we're at the beginning of a line
            boolean atLineStart = cursorPosition == lineStart;

            // If not at the beginning of a line, add a newline first
            if (!atLineStart && cursorPosition > 0) {
                editable.insert(cursorPosition, "\n☐ ");
                editText.setSelection(cursorPosition + 3);
            } else {
                editable.insert(cursorPosition, "☐ ");
                editText.setSelection(cursorPosition + 2);
            }
        });
        toolbar.addView(checkboxButton);

        // 2. BULLET POINT BUTTON
        ImageButton bulletButton = createToolbarButton(context, R.drawable.ic_bullet_point, "Insert bullet point", buttonSize, buttonMargin);
        bulletButton.setOnClickListener(v -> {
            int cursorPosition = editText.getSelectionStart();
            Editable editable = editText.getText();

            // Get the current line start position
            String text = editable.toString();
            int lineStart = text.lastIndexOf('\n', cursorPosition - 1) + 1;

            // Check if we're at the beginning of a line
            boolean atLineStart = cursorPosition == lineStart;

            // If not at the beginning of a line, add a newline first
            if (!atLineStart && cursorPosition > 0) {
                editable.insert(cursorPosition, "\n• ");
                editText.setSelection(cursorPosition + 3);
            } else {
                editable.insert(cursorPosition, "• ");
                editText.setSelection(cursorPosition + 2);
            }
        });
        toolbar.addView(bulletButton);

        // 3. NUMBERED LIST BUTTON
        ImageButton numberedButton = createToolbarButton(context, R.drawable.ic_numbered_list, "Insert numbered item", buttonSize, buttonMargin);
        numberedButton.setOnClickListener(v -> {
            int cursorPosition = editText.getSelectionStart();
            Editable editable = editText.getText();

            // Try to determine the appropriate number
            String text = editable.toString();
            int lineStart = text.lastIndexOf('\n', cursorPosition - 1) + 1;
            boolean atLineStart = cursorPosition == lineStart;

            String[] lines = text.substring(0, cursorPosition).split("\n");
            int number = 1;

            // Check if we're continuing a numbered list
            if (lines.length > 0) {
                String lastLine = lines[lines.length - 1];
                if (lastLine.matches("^\\d+\\..*")) {
                    try {
                        number = Integer.parseInt(lastLine.substring(0, lastLine.indexOf('.'))) + 1;
                    } catch (Exception e) {
                        number = 1;
                    }
                }
            }

            String insertText = number + ". ";

            // If not at the beginning of a line, add a newline first
            if (!atLineStart && cursorPosition > 0) {
                editable.insert(cursorPosition, "\n" + insertText);
                editText.setSelection(cursorPosition + insertText.length() + 1);
            } else {
                editable.insert(cursorPosition, insertText);
                editText.setSelection(cursorPosition + insertText.length());
            }
        });
        toolbar.addView(numberedButton);

        // 4. BOLD TEXT BUTTON
        ImageButton boldButton = createToolbarButton(context, R.drawable.ic_bold, "Bold text", buttonSize, buttonMargin);
        boldButton.setOnClickListener(v -> {
            int start = editText.getSelectionStart();
            int end = editText.getSelectionEnd();

            // Only work if text is selected
            if (start != end) {
                Spannable spannable = new SpannableString(editText.getText());

                // Check if the selection is already bold
                boolean hasBold = false;
                StyleSpan[] spans = spannable.getSpans(start, end, StyleSpan.class);
                for (StyleSpan span : spans) {
                    if (span.getStyle() == Typeface.BOLD) {
                        // Remove the bold formatting
                        spannable.removeSpan(span);
                        hasBold = true;
                    }
                }

                // If no bold was found or removed, add bold formatting
                if (!hasBold) {
                    spannable.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }

                // Replace the text in the EditText with the styled text
                editText.setText(spannable);
                editText.setSelection(start, end); // Maintain the selection
            } else {
                // Optionally show a toast if no text is selected
                Toast.makeText(context, "Please select text to format", Toast.LENGTH_SHORT).show();
            }
        });
        toolbar.addView(boldButton);

        // 5. ITALIC TEXT BUTTON
        ImageButton italicButton = createToolbarButton(context, R.drawable.ic_italic, "Italic text", buttonSize, buttonMargin);
        italicButton.setOnClickListener(v -> {
            int start = editText.getSelectionStart();
            int end = editText.getSelectionEnd();

            // Only work if text is selected
            if (start != end) {
                Spannable spannable = new SpannableString(editText.getText());

                // Check if the selection is already italic
                boolean hasItalic = false;
                StyleSpan[] spans = spannable.getSpans(start, end, StyleSpan.class);
                for (StyleSpan span : spans) {
                    if (span.getStyle() == Typeface.ITALIC) {
                        // Remove the italic formatting
                        spannable.removeSpan(span);
                        hasItalic = true;
                    }
                }

                // If no italic was found or removed, add italic formatting
                if (!hasItalic) {
                    spannable.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }

                // Replace the text in the EditText with the styled text
                editText.setText(spannable);
                editText.setSelection(start, end); // Maintain the selection
            } else {
                // Optionally show a toast if no text is selected
                Toast.makeText(context, "Please select text to format", Toast.LENGTH_SHORT).show();
            }
        });
        toolbar.addView(italicButton);

        // 6. INDENT/TAB BUTTON
        ImageButton tabButton = createToolbarButton(context, R.drawable.ic_indent, "Indent text", buttonSize, buttonMargin);
        tabButton.setOnClickListener(v -> {
            int cursorPosition = editText.getSelectionStart();
            Editable editable = editText.getText();

            // Get the current line start position
            String text = editable.toString();
            int lineStart = text.lastIndexOf('\n', cursorPosition - 1) + 1;

            // Insert indent at the beginning of the line
            editable.insert(lineStart, "    ");
            editText.setSelection(cursorPosition + 4);
        });
        toolbar.addView(tabButton);

        // Handle Enter key presses to auto-indent lists
        editText.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                int cursorPosition = editText.getSelectionStart();
                Editable editable = editText.getText();
                String text = editable.toString();

                // Find the start of the current line
                int lineStart = text.lastIndexOf('\n', cursorPosition - 1) + 1;
                String currentLine = text.substring(lineStart, cursorPosition);

                // Check for list markers at the beginning of the line
                if (currentLine.matches("^\\s*[•☐☑]\\s.*")) {
                    // Extract the bullet and its indentation
                    String indent = "";
                    int i = 0;
                    while (i < currentLine.length() && Character.isWhitespace(currentLine.charAt(i))) {
                        indent += currentLine.charAt(i);
                        i++;
                    }

                    char bulletType = currentLine.charAt(i);
                    editable.insert(cursorPosition, "\n" + indent + bulletType + " ");
                    editText.setSelection(cursorPosition + 3 + indent.length());
                    return true; // Key event handled
                }
                // Check for numbered lists
                else if (currentLine.matches("^\\s*\\d+\\.\\s.*")) {
                    // Extract the number and indentation
                    String indent = "";
                    int i = 0;
                    while (i < currentLine.length() && Character.isWhitespace(currentLine.charAt(i))) {
                        indent += currentLine.charAt(i);
                        i++;
                    }

                    // Find where the number ends
                    int j = i;
                    while (j < currentLine.length() && Character.isDigit(currentLine.charAt(j))) {
                        j++;
                    }

                    if (j < currentLine.length() && currentLine.charAt(j) == '.') {
                        try {
                            int number = Integer.parseInt(currentLine.substring(i, j));
                            String newPrefix = indent + (number + 1) + ". ";
                            editable.insert(cursorPosition, "\n" + newPrefix);
                            editText.setSelection(cursorPosition + 1 + newPrefix.length());
                            return true; // Key event handled
                        } catch (NumberFormatException e) {
                            // If the number parsing fails, just do a normal newline
                        }
                    }
                }
            }
            return false; // Let the system handle the key event
        });

        // Set up touch listener to detect taps on checkboxes
        editText.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                int offset = editText.getOffsetForPosition(event.getX(), event.getY());
                if (offset >= 0) {
                    Editable text = editText.getText();
                    // Check if we tapped on or near a checkbox character
                    if (offset < text.length()) {
                        // Look for checkbox characters near the tap
                        int start = Math.max(0, offset - 1);
                        int end = Math.min(text.length(), offset + 1);
                        String nearby = text.subSequence(start, end).toString();

                        // Check if there's a checkbox to toggle
                        if (nearby.contains("☐")) {
                            // Find the exact position
                            int checkboxPos = text.toString().lastIndexOf("☐", offset);
                            if (checkboxPos >= 0) {
                                // Replace unchecked with checked
                                text.replace(checkboxPos, checkboxPos + 1, "☑");
                                return true; // Handled the touch
                            }
                        } else if (nearby.contains("☑")) {
                            // Find the exact position
                            int checkboxPos = text.toString().lastIndexOf("☑", offset);
                            if (checkboxPos >= 0) {
                                // Replace checked with unchecked
                                text.replace(checkboxPos, checkboxPos + 1, "☐");
                                return true; // Handled the touch
                            }
                        }
                    }
                }
            }
            return false; // Not handled, let other touch events work
        });
    }

    // Helper method to capitalize the first letter of each word
    private String capitalizeWords(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder result = new StringBuilder();
        String[] words = text.split("\\s");

        for (String word : words) {
            if (!word.isEmpty()) {
                String firstLetter = word.substring(0, 1).toUpperCase();
                String restOfWord = word.length() > 1 ? word.substring(1) : "";
                result.append(firstLetter).append(restOfWord).append(" ");
            }
        }

        return result.toString().trim();
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains("swipe_state")) {
            // Just do a full rebind to keep things consistent
            onBindViewHolder(holder, position);
        } else {
            // Regular full bind
            onBindViewHolder(holder, position);
        }
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
        itemsPendingDelete.clear();
        notifyDataSetChanged();
    }

    public Task getTaskAt(int position) {
        return tasks.get(position);
    }

    public void markItemForDeletion(int position) {
        // Don't allow marking for deletion in selection mode
        if (!isSelectionMode) {
            itemsPendingDelete.add(position);
            notifyItemChanged(position, "swipe_state"); // Use payload to maintain swipe state
        }
    }

    public boolean isItemPendingDeletion(int position) {
        return itemsPendingDelete.contains(position);
    }

    public void deleteItem(int position) {
        if (position >= 0 && position < tasks.size()) {
            Task taskToDelete = tasks.get(position);
            itemsPendingDelete.remove(Integer.valueOf(position));
            // The calling activity will reload all tasks after deletion
        }
    }

    public void clearPendingDeletion(int position) {
        if (itemsPendingDelete.contains(position)) {
            itemsPendingDelete.remove(Integer.valueOf(position));
            notifyItemChanged(position);
        }
    }

    public void clearPendingDeletions() {
        itemsPendingDelete.clear();
        notifyDataSetChanged();
    }

    public void removeItem(int position) {
        if (position >= 0 && position < tasks.size()) {
            tasks.remove(position);
            notifyItemRemoved(position);
        }
    }

    public void restoreItem(Task task, int position) {
        if (position >= 0 && position <= tasks.size()) {
            tasks.add(position, task);
            notifyItemInserted(position);
        }
    }

    private int dpToPx(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkBox;
        TextView title;
        TextView dateTime;
        LinearLayout container;
        LinearLayout textContainer;

        TaskViewHolder(View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.taskCheckbox);
            title = itemView.findViewById(R.id.taskTitle);
            dateTime = itemView.findViewById(R.id.taskDateTime);
            container = itemView.findViewById(R.id.taskContainer);
            textContainer = itemView.findViewById(R.id.textContainer);
        }
    }
}