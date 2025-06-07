package com.example.agendaapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import java.util.List;

public class NotificationReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "task_notifications";

    @Override
    public void onReceive(Context context, Intent intent) {
        // Check if notifications are enabled
        SharedPreferences prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE);
        String notificationMode = prefs.getString("notification_mode", "none");

        if (notificationMode.equals("none")) return;

        String notificationType = intent.getStringExtra("notification_type");
        int taskId = intent.getIntExtra("task_id", -1);

        // Handle daily check for undated tasks
        if ("daily_undated_check".equals(notificationType)) {
            if (notificationMode.equals("all")) {
                handleDailyUndatedCheck(context);
            }
            return;
        }

        // Handle individual task notifications
        if (taskId == -1) return;

        // Check if task still exists and is not completed
        Task task = TaskDatabase.getInstance(context).taskDao().getTaskById(taskId);
        if (task == null || task.isCompleted || task.isArchived) return;

        String taskTitle = intent.getStringExtra("task_title");
        if (taskTitle == null) taskTitle = task.title;

        // Track that this notification was received/shown
        trackNotificationShown(context, taskId, notificationType);

        createNotificationChannel(context);

        String title = "";
        String message = "";

        switch (notificationType) {
            case "7_days":
                title = "Task Due Next Week";
                message = taskTitle + " is due in 7 days";
                break;
            case "3_days":
                title = "Task Due Soon";
                message = taskTitle + " is due in 3 days";
                break;
            case "1_day":
                title = "Task Due Tomorrow";
                message = taskTitle + " is due in 1 day";
                break;
            case "2_hours":
                title = "Task Due Soon";
                message = taskTitle + " is due in 2 hours";
                break;
            case "48_hours":
                title = "Task Due Soon";
                message = taskTitle + " is due in 2 days";
                break;
            case "24_hours":
                title = "Task Due Tomorrow";
                message = taskTitle + " is due in 1 day";
                break;
            case "due_now":
                title = "Task Due Now";
                message = taskTitle + " is due now";
                break;
            case "daily_pinned":
                title = "Pinned Task Reminder";
                message = "Don't forget: " + taskTitle;
                break;
            default:
                return;
        }

        showNotification(context, title, message, taskId, notificationType);
    }

    private void trackNotificationShown(Context context, int taskId, String notificationType) {
        SharedPreferences analyticsPrefs = context.getSharedPreferences("notification_analytics", Context.MODE_PRIVATE);

        // Track that this notification was shown
        analyticsPrefs.edit()
                .putLong("notification_shown_" + taskId + "_" + notificationType, System.currentTimeMillis())
                .apply();

        // Update notification effectiveness counters
        String showCountKey = "notification_show_count_" + notificationType;
        int currentCount = analyticsPrefs.getInt(showCountKey, 0);
        analyticsPrefs.edit()
                .putInt(showCountKey, currentCount + 1)
                .apply();
    }

    private void handleDailyUndatedCheck(Context context) {
        List<Task> undatedTasks = TaskDatabase.getInstance(context).taskDao().getUndatedIncompleteTasks();

        if (!undatedTasks.isEmpty()) {
            String title = "Check Your Tasks";
            String message = "You have " + undatedTasks.size() + " task" +
                    (undatedTasks.size() > 1 ? "s" : "") + " without due dates";
            showNotification(context, title, message, 0, "daily_undated_check");
        }
    }

    private void showNotification(Context context, String title, String message, int taskId, String notificationType) {
        Intent openAppIntent = new Intent(context, ViewTasksActivity.class);

        // Add tracking parameters to detect notification clicks
        openAppIntent.putExtra("notification_clicked", true);
        openAppIntent.putExtra("task_id", taskId);
        openAppIntent.putExtra("notification_type", notificationType);
        openAppIntent.putExtra("notification_timestamp", System.currentTimeMillis());

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, taskId, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManager notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Use unique notification ID based on task ID and type
        int notificationId = taskId > 0 ? 1000 + taskId : 999;
        notificationManager.notify(notificationId, builder.build());
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Task Notifications",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Notifications for due tasks");

            NotificationManager notificationManager =
                    context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}