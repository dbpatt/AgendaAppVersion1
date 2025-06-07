package com.example.agendaapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashMap;
import java.util.Map;

public class SwipeToRestoreCallback extends ItemTouchHelper.SimpleCallback {
    private final TaskAdapter adapter;
    private final Context context;
    private final Drawable restoreIcon;
    private final Paint background;
    private final int cornerRadius;
    private final RecyclerView recyclerView;

    // Track animations for each position
    private final Map<Integer, AnimationState> animationStates = new HashMap<>();

    // Class to track animation state for each item
    private static class AnimationState {
        boolean isRestoring = false;
        float progress = 0f;
        ValueAnimator animator;
        Task taskToRestore;
    }

    public SwipeToRestoreCallback(TaskAdapter adapter, Context context, RecyclerView recyclerView) {
        // Enable only RIGHT swipe
        super(0, ItemTouchHelper.RIGHT);
        this.adapter = adapter;
        this.context = context;
        this.recyclerView = recyclerView;

        // Semi-transparent green background (50% opacity)
        background = new Paint();
        background.setColor(Color.parseColor("#804CAF50")); // 80 prefix = 50% opacity
        background.setStyle(Paint.Style.FILL);
        background.setAntiAlias(true);

        // Corner radius for rounded background
        cornerRadius = dpToPx(context, 8);

        // Load restore icon
        restoreIcon = ContextCompat.getDrawable(context, R.drawable.ic_restore);
        if (restoreIcon != null) {
            restoreIcon.setTint(Color.WHITE); // Make icon white for better visibility
        }
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
        return false; // We don't want drag & drop
    }

    @Override
    public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        // Disable swipe in selection mode
        if (adapter.isInSelectionMode()) {
            return 0;
        }

        int position = viewHolder.getAdapterPosition();
        if (position != RecyclerView.NO_POSITION) {
            // Don't allow swiping during restore animation
            AnimationState state = animationStates.get(position);
            if (state != null && state.isRestoring) {
                return 0;
            }
        }

        return super.getMovementFlags(recyclerView, viewHolder);
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        int position = viewHolder.getAdapterPosition();
        if (position != RecyclerView.NO_POSITION && direction == ItemTouchHelper.RIGHT) {
            // Create animation state IMMEDIATELY to prevent flickering
            AnimationState state = new AnimationState();
            state.isRestoring = true;
            state.progress = 0f;
            animationStates.put(position, state);

            // Get the task to restore
            Task taskToRestore = adapter.getTaskAt(position);

            // Start restore animation
            startRestoreAnimation(viewHolder, position, taskToRestore);
        }
    }

    @Override
    public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
        return 0.8f; // Require 100% swipe to trigger action
    }

    private void startRestoreAnimation(RecyclerView.ViewHolder viewHolder, int position, Task taskToRestore) {
        // Create animation to slide item off screen
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(300); // 300ms animation
        animator.setInterpolator(new AccelerateInterpolator());

        // Get existing or create new animation state
        final AnimationState state = animationStates.get(position);
        if (state == null) {
            // This shouldn't happen since we create it in onSwiped, but just in case
            AnimationState newState = new AnimationState();
            newState.isRestoring = true;
            newState.progress = 0f;
            newState.animator = animator;
            newState.taskToRestore = taskToRestore;
            animationStates.put(position, newState);

            // Use final reference for the lambda
            final AnimationState finalState = newState;

            animator.addUpdateListener(animation -> {
                finalState.progress = (float) animation.getAnimatedValue();
                recyclerView.invalidate(); // Force redraw
            });
        } else {
            // Update existing state
            state.animator = animator;
            state.taskToRestore = taskToRestore;

            // Use final reference for the lambda
            animator.addUpdateListener(animation -> {
                state.progress = (float) animation.getAnimatedValue();
                recyclerView.invalidate(); // Force redraw
            });
        }

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Actually restore the task when animation ends
                taskToRestore.isArchived = false;
                TaskDatabase.getInstance(context).taskDao().update(taskToRestore);

                // Update the adapter
                adapter.setTasks(TaskDatabase.getInstance(context).taskDao().getArchivedTasks());

                // Remove animation state
                animationStates.remove(position);
            }
        });

        animator.start();
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
        View itemView = viewHolder.itemView;
        int position = viewHolder.getAdapterPosition();

        // Check if item is being animated for restore
        AnimationState state = animationStates.get(position);

        if (state != null && state.isRestoring) {
            // Calculate how far to slide right (off screen)
            float screenWidth = recyclerView.getWidth();
            float slideDistance = screenWidth * state.progress;

            // Draw background
            background.setAlpha(255 - (int)(255 * state.progress)); // Fade out

            RectF backgroundRect = new RectF(
                    itemView.getLeft(),
                    itemView.getTop(),
                    itemView.getLeft() + slideDistance,
                    itemView.getBottom()
            );
            c.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, background);

            // Draw item with rightward slide
            super.onChildDraw(c, recyclerView, viewHolder, slideDistance, dY, actionState, false);
            return;
        }

        // During active swipe, don't show any visual changes
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && isCurrentlyActive) {
            // Pass 0 for dX to prevent any horizontal movement during the active swipe
            super.onChildDraw(c, recyclerView, viewHolder, 0, dY, actionState, isCurrentlyActive);
        } else if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && !isCurrentlyActive && dX > 0) {
            // Only when swipe is complete (not active) and it's a right swipe, show the restore UI

            // Draw the rounded green background
            RectF backgroundRect = new RectF(
                    itemView.getLeft(),
                    itemView.getTop(),
                    itemView.getLeft() + dX,
                    itemView.getBottom()
            );
            c.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, background);

            // Draw the restore icon
            if (restoreIcon != null) {
                int iconMargin = (itemView.getHeight() - restoreIcon.getIntrinsicHeight()) / 2;
                int iconTop = itemView.getTop() + iconMargin;
                int iconBottom = iconTop + restoreIcon.getIntrinsicHeight();
                int iconLeft = itemView.getLeft() + iconMargin;
                int iconRight = iconLeft + restoreIcon.getIntrinsicWidth();

                restoreIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                restoreIcon.draw(c);
            }

            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        } else {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }
    }

    private int dpToPx(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}