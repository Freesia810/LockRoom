package com.freesia.lockroom;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public class DiaryInfoAdapter extends ArrayAdapter<DiaryInfo> {
    public DiaryInfoAdapter(@NonNull Context context, int resource, @NonNull List<DiaryInfo> objects) {
        super(context, resource, objects);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        DiaryInfo diaryInfo = getItem(position);
        View fView;
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            fView = inflater.inflate(R.layout.diary_item, parent, false);
        } else {
            fView = convertView;
        }


        TextView titleText = fView.findViewById(R.id.titleTextView);


        int uiMode = getContext().getResources().getConfiguration().uiMode;
        if ((uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
            titleText.setTextColor(Color.WHITE);
        } else {
            titleText.setTextColor(Color.BLACK);
        }

        titleText.setText(diaryInfo.getDisplayedTitle());

        return fView;
    }
}
