package com.example.agendaapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.snackbar.Snackbar;

import java.util.HashMap;
import java.util.Map;

public class SwipeToDeleteCallback extends ItemTouchHelper.SimpleCallback {
    private final TaskAdapter adapter;
    private final Context context;
    private final Drawable deleteIcon;
    private final Drawable roundedBackground;
    private final Drawable roundedDeleteButton;
    private final int deleteButtonWidth = 80; // Width of the red delete button area
    private float pulseScale = 1.0f; // Default scale
    private RecyclerView recyclerView; // Store the RecyclerView to invalidate it
    private static final String TAG = "SwipeToDeleteCallback";
    private ValueAnimator pulseAnimator; // Store animation reference to be able to cancel it
    private boolean markOnly;
    private Snackbar currentSnackbar; // Track the current Snackbar
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final boolean isArchiveMode; // Add flag to determine if we're in archive mode

    // New constant for the deep swipe threshold (60% of item width)
    private static final float DEEP_SWIPE_THRESHOLD = 0.9f;

    // Track animations for each position separately
    private final Map<Integer, AnimationState> animationStates = new HashMap<>();

    // Class to track animation state for each item
    private static class AnimationState {
        boolean isDeleting = false;
        float progress = 0f;
        ValueAnimator animator;
        Task taskToDelete;
        boolean pendingActualDeletion = false; // Flag to track if we're waiting for undo period
    }

    public SwipeToDeleteCallback(TaskAdapter adapter, Context context, boolean markOnly) {
        this(adapter, context, markOnly, false);
    }

    public SwipeToDeleteCallback(TaskAdapter adapter, Context context, boolean markOnly, boolean isArchiveMode) {
        // Only enable LEFT swipe in the ItemTouchHelper
        super(0, ItemTouchHelper.LEFT);
        this.adapter = adapter;
        this.context = context;
        this.markOnly = markOnly;
        this.isArchiveMode = isArchiveMode;

        // Create or load delete icon (white X in circle)
        deleteIcon = ContextCompat.getDrawable(context, R.drawable.delete_icon);

        // Pink background with rounded corners
        roundedBackground = ContextCompat.getDrawable(context, R.drawable.rounded_background_delete);

        // Red delete button with rounded corners
        roundedDeleteButton = ContextCompat.getDrawable(context, R.drawable.rounded_delete_button);

        Log.d(TAG, "SwipeToDeleteCallback initialized with markOnly=" + markOnly + ", isArchiveMode=" + isArchiveMode);
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
            // Don't allow swiping during delete animation
            AnimationState state = animationStates.get(position);
            if (state != null && state.isDeleting) {
                return 0;
            }

            // Only allow LEFT swipes
            Log.d(TAG, "getMovementFlags: Allowing LEFT swipe for item " + position);
            return makeMovementFlags(0, ItemTouchHelper.LEFT);
        }
        return 0;
    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
        int position = viewHolder.getAdapterPosition();
        if (position == RecyclerView.NO_POSITION) return;

        Log.d(TAG, "onSwiped: position=" + position + " direction=" +
                (direction == ItemTouchHelper.RIGHT ? "RIGHT" : "LEFT"));

        // Only handle LEFT swipes
        if (direction == ItemTouchHelper.LEFT) {
            // Store the task before visually removing it
            Task taskToDelete = adapter.getTaskAt(position);

            // Dismiss any active Snackbar
            if (currentSnackbar != null) {
                currentSnackbar.dismiss();
                currentSnackbar = null;
            }

            // Start animation immediately to slide item off screen
            startDeleteAnimation(viewHolder, position, taskToDelete);

            // Show snackbar with UNDO option
            View rootView = recyclerView.getRootView().findViewById(android.R.id.content);

            // Change message based on if we're in archive mode
            String message = isArchiveMode ?
                    "Task permanently deleted" :
                    "Task archived";

            currentSnackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_LONG);
            currentSnackbar.setDuration(1500); // 3 seconds for undo

            final int finalPosition = position;

            currentSnackbar.setAction("UNDO", view -> {
                // Cancel the deletion when "UNDO" is clicked
                cancelDeletion(finalPosition, taskToDelete);
                currentSnackbar = null;
            });

            // Set a dismiss listener to handle actual deletion when the Snackbar disappears
            currentSnackbar.addCallback(new Snackbar.Callback() {
                @Override
                public void onDismissed(Snackbar snackbar, int event) {
                    super.onDismissed(snackbar, event);

                    // If dismissed without clicking UNDO, perform actual deletion
                    if (event != Snackbar.Callback.DISMISS_EVENT_ACTION) {
                        // Perform actual deletion from the database
                        performActualDeletion(finalPosition, taskToDelete);
                    }
                }
            });

            currentSnackbar.show();
        }
    }

    // Method to start the delete animation immediately
    private void startDeleteAnimation(RecyclerView.ViewHolder viewHolder, int position, Task taskToDelete) {
        // Create a new animation state or get an existing one
        AnimationState state = animationStates.get(position);
        if (state == null) {
            state = new AnimationState();
            animationStates.put(position, state);
        }

        // Set up animation state
        state.isDeleting = true;
        state.progress = 0f;
        state.taskToDelete = taskToDelete;
        state.pendingActualDeletion = true; // Mark that we're waiting for undo period

        // Cancel any existing animation
        if (state.animator != null && state.animator.isRunning()) {
            state.animator.cancel();
        }

        // Create new animation to slide item off screen
        state.animator = ValueAnimator.ofFloat(0f, 1f);
        state.animator.setDuration(300); // Keep animation duration at 300ms
        state.animator.setInterpolator(new AccelerateInterpolator());

        final int animatingPosition = position; // Capture for lambdas

        state.animator.addUpdateListener(animation -> {
            AnimationState currentState = animationStates.get(animatingPosition);
            if (currentState != null) {
                currentState.progress = (float) animation.getAnimatedValue();

                // Force redraw for animation
                if (recyclerView != null) {
                    recyclerView.invalidate();
                }
            }
        });

        state.animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Visually remove the item from the adapter
                adapter.markItemForDeletion(animatingPosition);

                // CRITICAL: Actually remove the item from the adapter's dataset
                // This makes it disappear visually while still in the adapter's pending deletion list
                adapter.removeItem(animatingPosition);

                // Note: We don't perform the actual database deletion here
                // That happens only after the undo period expires
            }
        });

        state.animator.start();
    }

    // Method to cancel deletion and restore the item
    private void cancelDeletion(int position, Task taskToRestore) {
        AnimationState state = animationStates.get(position);
        if (state != null) {
            state.pendingActualDeletion = false;
            state.isDeleting = false;

            // Cancel any running animation
            if (state.animator != null && state.animator.isRunning()) {
                state.animator.cancel();
            }

            // Clear state
            animationStates.remove(position);
        }

        // First visually restore the item using the adapter's restore method
        adapter.restoreItem(taskToRestore, position);

        // Then clear the pending deletion state
        adapter.clearPendingDeletion(position);

        // Reload data to ensure everything is in sync
        if (isArchiveMode) {
            adapter.setTasks(TaskDatabase.getInstance(context).taskDao().getArchivedTasks());
            Toast.makeText(context, "Delete cancelled", Toast.LENGTH_SHORT).show();
        } else {
            adapter.setTasks(TaskDatabase.getInstance(context).taskDao().getSortedTasks());
            Toast.makeText(context, "Archive cancelled", Toast.LENGTH_SHORT).show();
        }
    }

    // Method to perform actual deletion from database
    private void performActualDeletion(int position, Task taskToDelete) {
        AnimationState state = animationStates.get(position);
        if (state != null && state.pendingActualDeletion) {
            if (isArchiveMode) {
                // In archive mode, actually delete the task
                TaskDatabase.getInstance(context).taskDao().delete(taskToDelete);
            } else {
                // In normal mode, archive the task instead of deleting
                taskToDelete.isArchived = true;
                TaskDatabase.getInstance(context).taskDao().update(taskToDelete);
            }

            // Clean up state
            animationStates.remove(position);

            // Force layout to refresh immediately
            if (recyclerView != null) {
                // This forces the RecyclerView to redraw and reposition items
                recyclerView.requestLayout();
            }

            // Refresh adapter data
            if (isArchiveMode) {
                adapter.setTasks(TaskDatabase.getInstance(context).taskDao().getArchivedTasks());
            } else {
                adapter.setTasks(TaskDatabase.getInstance(context).taskDao().getSortedTasks());
            }
        }
    }

    // Rest of the methods remain unchanged
    // ...

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
                            int actionState, boolean isCurrentlyActive) {

        // Store the RecyclerView for animation
        if (this.recyclerView == null) {
            this.recyclerView = recyclerView;
        }

        View itemView = viewHolder.itemView;
        int itemHeight = itemView.getHeight();
        int itemWidth = itemView.getWidth();
        int buttonWidth = dpToPx(deleteButtonWidth);

        // Calculate positions
        int right = itemView.getRight();
        int left = itemView.getLeft();
        int top = itemView.getTop();
        int bottom = itemView.getBottom();

        // Get current position
        int position = viewHolder.getAdapterPosition();
        if (position == RecyclerView.NO_POSITION) return;

        boolean isPendingDeletion = adapter.isItemPendingDeletion(position);

        // Check if we're animating a deletion for this position
        AnimationState state = animationStates.get(position);
        boolean isAnimatingDelete = state != null && state.isDeleting;

        // Handle delete animation
        if (isAnimatingDelete) {
            // Calculate how far to slide based on animation progress
            float slideDistance = itemWidth * state.progress;

            // Calculate alpha (fade out)
            int alpha = (int)(255 * (1 - state.progress));

            // Apply alpha to background and button
            if (roundedBackground != null) {
                roundedBackground.setAlpha(alpha);
            }
            if (roundedDeleteButton != null) {
                roundedDeleteButton.setAlpha(alpha);
            }
            if (deleteIcon != null) {
                deleteIcon.setAlpha(alpha);
            }

            // Draw background (pink)
            if (roundedBackground != null) {
                int adjustedLeft = (int)(left - slideDistance);
                int adjustedRight = (int)(right - slideDistance);
                roundedBackground.setBounds(adjustedLeft, top, adjustedRight, bottom);
                roundedBackground.draw(c);
            }

            // Draw delete button (red)
            if (roundedDeleteButton != null) {
                int buttonLeft = (int)(right - buttonWidth - slideDistance);
                int buttonRight = (int)(right - slideDistance);
                roundedDeleteButton.setBounds(buttonLeft, top, buttonRight, bottom);
                roundedDeleteButton.draw(c);
            }

            // Draw delete icon
            if (deleteIcon != null) {
                int iconWidth = deleteIcon.getIntrinsicWidth();
                int iconHeight = deleteIcon.getIntrinsicHeight();

                int iconTop = top + (itemHeight - iconHeight) / 2;
                int iconLeft = (int)(right - buttonWidth / 2 - iconWidth / 2 - slideDistance);

                deleteIcon.setBounds(iconLeft, iconTop, iconLeft + iconWidth, iconTop + iconHeight);
                deleteIcon.draw(c);
            }

            // Draw the item at the animated position
            float translationX = -slideDistance;
            super.onChildDraw(c, recyclerView, viewHolder, translationX, dY, actionState, false);

            return;
        }

        // Block right swipes
        if (dX > 0) {
            super.onChildDraw(c, recyclerView, viewHolder, 0, dY, actionState, isCurrentlyActive);
            return;
        }

        // For active swipe gesture, don't show any visual changes
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && isCurrentlyActive) {
            // Pass 0 for dX to prevent any horizontal movement during the active swipe
            super.onChildDraw(c, recyclerView, viewHolder, 0, dY, actionState, isCurrentlyActive);
        } else if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && !isCurrentlyActive && dX < 0) {
            // Only when swipe is complete (not active) and it's a left swipe, show the deletion UI

            // Draw the full pink background
            if (roundedBackground != null) {
                roundedBackground.setBounds(left, top, right, bottom);
                roundedBackground.draw(c);
            }

            // Draw the full delete button
            if (roundedDeleteButton != null) {
                int buttonLeft = right - buttonWidth;
                roundedDeleteButton.setBounds(buttonLeft, top, right, bottom);
                roundedDeleteButton.draw(c);
            }

            // Draw the delete icon
            if (deleteIcon != null) {
                int iconWidth = deleteIcon.getIntrinsicWidth();
                int iconHeight = deleteIcon.getIntrinsicHeight();

                int iconTop = top + (itemHeight - iconHeight) / 2;
                int iconLeft = right - buttonWidth / 2 - iconWidth / 2;

                deleteIcon.setBounds(iconLeft, iconTop, iconLeft + iconWidth, iconTop + iconHeight);
                deleteIcon.draw(c);
            }

            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        } else if (isPendingDeletion) {
            // Handle pending deletion display
            if (roundedBackground != null) {
                roundedBackground.setBounds(left, top, right, bottom);
                roundedBackground.draw(c);
            }

            if (roundedDeleteButton != null) {
                int buttonLeft = right - buttonWidth;
                roundedDeleteButton.setBounds(buttonLeft, top, right, bottom);
                roundedDeleteButton.draw(c);
            }

            if (deleteIcon != null) {
                int iconWidth = deleteIcon.getIntrinsicWidth();
                int iconHeight = deleteIcon.getIntrinsicHeight();

                int iconTop = top + (itemHeight - iconHeight) / 2;
                int iconLeft = right - buttonWidth / 2 - iconWidth / 2;

                deleteIcon.setBounds(iconLeft, iconTop, iconLeft + iconWidth, iconTop + iconHeight);
                deleteIcon.draw(c);
            }

            super.onChildDraw(c, recyclerView, viewHolder, 0, dY, actionState, isCurrentlyActive);
        } else {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }
    }

    private int dpToPx(int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    @Override
    public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
        // Use a higher threshold to require a deeper swipe for deletion
        return DEEP_SWIPE_THRESHOLD; // 60% of item width
    }

    @Override
    public float getSwipeEscapeVelocity(float defaultValue) {
        // Increase the escape velocity to make accidental swipes less likely
        return defaultValue * 0.7f;
    }

    @Override
    public float getSwipeVelocityThreshold(float defaultValue) {
        // Increase the velocity threshold for more deliberate swipes
        return defaultValue * 0.7f;
    }

    @Override
    public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);

        // This is called when a swipe action is completed or canceled
        int position = viewHolder.getAdapterPosition();
        if (position != RecyclerView.NO_POSITION) {
            Log.d(TAG, "clearView called for position " + position);

            // If this item is no longer pending deletion, ensure it gets fully reset
            if (!adapter.isItemPendingDeletion(position)) {
                // Force a full refresh of this item
                adapter.notifyItemChanged(position);
            }
        }
    }

    // Clean up animations when items are removed or recycled
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder viewHolder) {
        int position = viewHolder.getAdapterPosition();
        if (position != RecyclerView.NO_POSITION) {
            // Cancel any animations for this position
            AnimationState state = animationStates.get(position);
            if (state != null && state.animator != null && state.animator.isRunning()) {
                state.animator.cancel();
            }
            animationStates.remove(position);
        }
    }
}