package com.freesia.lockroom;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.apache.commons.codec.digest.DigestUtils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DiaryInfo implements Serializable {
    private final long _timestamp;
    private String _title;
    private final String _contentAES;


    public DiaryInfo(long timestamp, String title, String contentAES) {
        _timestamp = timestamp;
        _title = title;
        _contentAES = contentAES;
    }

    public String getDisplayedTitle(){
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(_timestamp)) + " " + _title;
    }

    public void openDiary(Context context) {
        LocalTask task = new LocalTask();
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, context, LocalTask.DIARY_OPEN, this);
    }

    public void deleteDiary(Context context) {
        LocalTask task = new LocalTask();
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, context, LocalTask.DIARY_DELETE, this);
    }

    public void shareDiary(Context context){
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
                            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, context, NetworkTask.UPLOAD_CLOUD, new ShareInfo(context, times, true, this, isPermanent));
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

    public String getTitle() {
        return _title;
    }

    public String getContentAES() {
        return _contentAES;
    }

    public long getTimestamp() {
        return _timestamp;
    }

    public String getContentLocation(Context context) {
        List<String> meta = getMetaRaw(context);
        String str = meta.get(meta.lastIndexOf(";;")+1);
        if(str.equals("$$")) {
            return "";
        }
        else {
            return str.substring(str.lastIndexOf(':')+1);
        }
    }

    public List<Pair<String, String>> getMediaAES(Context context) {
        List<String> meta = getMetaRaw(context);
        List<Pair<String, String>> res = new ArrayList<>();
        for(String line:meta) {
            if(line.equals(";;")) {
                break;
            }
            else {
                res.add(new Pair<>(line.substring(0, line.lastIndexOf(':')), line.substring(line.lastIndexOf(':') + 1)));
            }
        }
        return res;
    }

    public String getEncodedPath(Context context) {
        return context.getExternalFilesDir("MyDiary").getAbsolutePath() + "/" + getTimestamp() + DigestUtils.md5Hex(getTitle());
    }

    private List<String> getMetaRaw(Context context) {
        List<String> res = new ArrayList<>();
        //读取文件
        try {
            InputStream inputStream = new FileInputStream(context.getCacheDir().getAbsolutePath()+"/content.raw");
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String line;

            while (( line = bufferedReader.readLine()) != null) {
                if(line.equals("$$")) {
                    break;
                }
                else {
                    res.add(line);
                }
            }
            inputStream.close();
        }
        catch (Exception e){
            e.printStackTrace();
        }

        return res;
    }

    public void ResetTitle(){
        _title = _title + Calendar.getInstance().getTimeInMillis();
    }


    public List<String> getContentText(Context context) {
        List<String> res = new ArrayList<>();
        //读取文件
        try {
            InputStream inputStream = new FileInputStream(context.getCacheDir().getAbsolutePath()+"/content.raw");
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            StringBuilder temp= new StringBuilder();
            boolean isText = false;

            while (( line = bufferedReader.readLine()) != null) {
                if(isText) {
                    if((line.startsWith("[pic:") || line.startsWith("[vid:") || line.startsWith("[rec:")) && line.endsWith("]")) {
                        if(!temp.toString().equals("")) {
                            res.add(temp.substring(0, temp.length()-1));
                            temp = new StringBuilder();
                        }
                        res.add(line);
                    }
                    else {
                        temp.append(line).append("\n");
                    }
                }
                if(line.equals("$$")) {
                    isText = true;
                }
            }
            if(!temp.toString().equals("")) {
                res.add(temp.substring(0, temp.length()-1));
            }
            inputStream.close();
        }
        catch (Exception e){
            e.printStackTrace();
        }

        return res;
    }
}
