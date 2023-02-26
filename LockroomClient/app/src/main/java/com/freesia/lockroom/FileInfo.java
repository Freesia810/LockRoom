package com.freesia.lockroom;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.apache.commons.codec.digest.DigestUtils;

import java.util.Calendar;

public class FileInfo {
    private String fileName;
    private final String fileSize;
    private final String fileTime;

    public String getFileName() {
        return fileName;
    }

    public String getFileSize() {
        return fileSize;
    }

    public String getFileTime() {
        return fileTime;
    }

    public FileInfo(String fileName, String fileTime, String fileSize)
    {
        this.fileName = fileName;
        this.fileTime = fileTime;
        this.fileSize = fileSize;
    }

    public void openFile(Context context){
        LocalTask openTask = new LocalTask();
        openTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, context, LocalTask.OPEN, getFileName());
    }

    public void mvoutFile(Context context){
        LocalTask openTask = new LocalTask();
        openTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, context, LocalTask.MOVE_OUT, getFileName());
    }

    public void delFile(Context context){
        LocalTask openTask = new LocalTask();
        openTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, context, LocalTask.DELETE, getFileName());
    }

    public void shareFile(Context context){
        if (!FileKit.checkConnectNetwork(context)) {
            new Handler(context.getMainLooper()).post(() -> Toast.makeText(context, context.getResources().getString(R.string.tip_network), Toast.LENGTH_LONG).show());
        }
        else{
            View mView = LayoutInflater.from(context).inflate(R.layout.content_shareinfo, null);
            final EditText setTimesText = mView.findViewById(R.id.setTimesText);
            final CheckBox checkPermanent = mView.findViewById(R.id.setPermanentBox);

            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setTitle(context.getResources().getString(R.string.title_share))
                    .setView(mView)
                    .setIcon(R.drawable.ic_share)
                    .setPositiveButton(context.getResources().getString(R.string.apply), (dialogInterface, i) -> {
                        int times = Integer.parseInt(setTimesText.getText().toString());
                        boolean isPermanent = checkPermanent.isChecked();
                        if(times > 0){
                            NetworkTask task = new NetworkTask();
                            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, context, NetworkTask.UPLOAD_CLOUD, new ShareInfo(context, times, false, this, isPermanent));
                        }
                        else{
                            Toast.makeText(context, context.getResources().getString(R.string.check_input), Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton(context.getResources().getString(R.string.cancel), null)
                    .create();
            dialog.show();
        }
    }

    public void renameFile(Context context){
        final EditText editText = new EditText(context);
        editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(50)});
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(editText)
                .setIcon(R.drawable.ic_rename)
                .setNegativeButton(context.getResources().getString(R.string.cancel), null)
                .setTitle(context.getResources().getString(R.string.title_rename));
        editText.setText(getFileName());
        builder.setPositiveButton(context.getResources().getString(R.string.apply), (dialog, which) -> {
            String newName = editText.getText().toString();
            LocalTask reTask = new LocalTask();
            reTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, context, LocalTask.RENAME, getFileName(), newName);
        });
        builder.show();
    }

    public void ResetName(){
        String postfix = fileName.substring(fileName.lastIndexOf('.'));
        String prefix = fileName.substring(0, fileName.lastIndexOf('.'));

        fileName = prefix + Calendar.getInstance().getTimeInMillis() + postfix;
    }

    public String getEncodedPath(Context context) {
        return context.getExternalFilesDir("PrivateRoom").getAbsolutePath() + "/" + DigestUtils.md5Hex(getFileName());
    }
}
