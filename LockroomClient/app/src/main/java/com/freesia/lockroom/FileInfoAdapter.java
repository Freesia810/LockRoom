package com.freesia.lockroom;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public class FileInfoAdapter extends ArrayAdapter<FileInfo> {
    public FileInfoAdapter(@NonNull Context context, int resource, @NonNull List<FileInfo> objects) {
        super(context, resource, objects);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        FileInfo fileInfo = getItem(position);
        View fView;
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            fView = inflater.inflate(R.layout.file_item, parent, false);
        } else {
            fView = convertView;
        }

        ImageView fileIcon = fView.findViewById(R.id.fileIcon);
        TextView nameText = fView.findViewById(R.id.nameTextView);
        TextView timeText = fView.findViewById(R.id.timeTextView);
        TextView sizeText = fView.findViewById(R.id.sizeTextView);

        int uiMode = getContext().getResources().getConfiguration().uiMode;
        if ((uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
            nameText.setTextColor(Color.WHITE);
        } else {
            nameText.setTextColor(Color.BLACK);
        }

        String suffix = fileInfo.getFileName().substring(fileInfo.getFileName().lastIndexOf(".") + 1).toLowerCase();

        switch (suffix) {
            case "png" : case "jpg" : case "jpeg" : case "gif" : case "bmp" :
                fileIcon.setImageResource(R.drawable.ic_file_type_pic);
                break;
            case "mp4" : case "mkv" : case "avi" : case "mpeg" : case "flv" : case "m3u8" : case "rmvb" : case "3gp" :
                fileIcon.setImageResource(R.drawable.ic_file_type_video);
                break;
            case "txt" : case "doc" : case "docx" : case "pdf" :
                fileIcon.setImageResource(R.drawable.ic_file_type_text);
                break;
            case "zip" : case "rar" : case "7z" :
                fileIcon.setImageResource(R.drawable.ic_file_type_zip);
                break;
            case "mp3" : case "wav" : case "flac" : case "m4a" : case "aac" :
                fileIcon.setImageResource(R.drawable.ic_file_type_music);
                break;
            default:
                fileIcon.setImageResource(R.drawable.ic_file_type_file);
                break;
        }

        sizeText.setText(fileInfo.getFileSize());
        nameText.setText(fileInfo.getFileName());
        timeText.setText(fileInfo.getFileTime());

        return fView;
    }
}
