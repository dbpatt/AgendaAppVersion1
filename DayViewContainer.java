package com.example.agendaapp;

import android.view.View;
import android.widget.TextView;

import com.kizitonwose.calendar.view.ViewContainer;

public class DayViewContainer extends ViewContainer {
    public final TextView textView;
    public final View taskIndicator;

    public DayViewContainer(View view) {
        super(view);
        textView = view.findViewById(R.id.calendarDayText);
        taskIndicator = view.findViewById(R.id.taskIndicator);
    }
}