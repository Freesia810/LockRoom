package com.freesia.lockroom;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Environment;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

import com.freesia.lockroom.ui.mydiaryFragment;
import com.freesia.lockroom.ui.privateroomFragment;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.MultipartReader;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class NetworkTask  extends AsyncTask<Object, Double, List<Object>> {
    public static final int UPLOAD_CLOUD = 0;
    public static final int DOWNLOAD_CLOUD = 1;
    private static final String host = "http://47.102.200.110/lockroom";

    @Override
    protected List<Object> doInBackground(Object... objects) {
        Context context = (Context) objects[0];
        int mode = (int) objects[1];
        List<Object> list = new ArrayList<>();
        list.add(context);
        list.add(mode);
        String res;
        switch (mode) {
            case UPLOAD_CLOUD:
                ShareInfo info = (ShareInfo) objects[2];
                MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
                builder.addPart( Headers.of("Content-Disposition", "form-data; name=\"file\";filename=\""+info.getFile().getName()+"\""),
                        RequestBody.create(info.getFile(), MediaType.parse("multipart/form-data")));
                builder.addFormDataPart("filename", info.getFilename());
                builder.addFormDataPart("times", Integer.toString(info.getTotalTimes()));
                builder.addFormDataPart("isPermanent", info.getPermanent()? "true":"false");
                builder.addFormDataPart("key", info.getKey());
                builder.addFormDataPart("isDiary", info.getDiary()? "true":"false");

                RequestBody body = builder.build();
                Request request = new Request.Builder().url(host+"/upload").post(body).build();
                OkHttpClient client = new OkHttpClient();
                Call call = client.newCall(request);
                try {
                    Response response = call.execute();
                    res = response.body().string();
                    list.add(res);
                } catch (Exception e) {
                    e.printStackTrace();
                    list.add("");
                }

                break;
            case DOWNLOAD_CLOUD:
                String shareMD5 = (String) objects[2];
                JSONObject jsonObject = null;
                try {
                    jsonObject = new JSONObject().put("md5", shareMD5);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                assert jsonObject != null;
                RequestBody requestBody = RequestBody.create(jsonObject.toString(), MediaType.parse("application/json"));
                request = new Request.Builder().url(host+"/download").post(requestBody).build();
                client = new OkHttpClient();
                call = client.newCall(request);

                String aeskey = "";
                String filename = "";
                long filesize = 0;
                try {
                    Response response = call.execute();
                    MultipartReader multipartReader = new MultipartReader(response.body());
                    while (true){
                        MultipartReader.Part part = multipartReader.nextPart();
                        if(part == null){
                            multipartReader.close();
                            break;
                        }
                        String head = part.headers().toString();
                        String name = head.substring(head.indexOf('\"') + 1 );
                        name = name.substring(0, name.indexOf('\"'));

                        switch (name){
                            case "file":
                                InputStream inputStream = part.body().inputStream();
                                File file = new File(context.getCacheDir().getAbsolutePath()+"/temp");
                                FileOutputStream fos = new FileOutputStream(file);
                                byte[] buf = new byte[2048];
                                int len;
                                while ((len = inputStream.read(buf)) != -1) {
                                    fos.write(buf, 0, len);
                                }
                                fos.flush();
                                fos.close();
                                filesize = file.length();
                                break;
                            case "filename":
                                filename = part.body().readUtf8Line();
                                break;
                            case "aeskey":
                                aeskey = part.body().readUtf8Line();
                                break;
                            case "isDiary":
                                String isDiary = part.body().readUtf8Line();
                                switch (Objects.requireNonNull(isDiary)){
                                    case "true":
                                        assert filename != null;
                                        DiaryInfo diaryInfo = new DiaryInfo(Long.parseLong(filename.substring(0,filename.indexOf(':'))),filename.substring(filename.indexOf(':')+1),aeskey);
                                        list.add(diaryInfo);
                                        break;
                                    case "false":
                                        FileInfo fileInfo = new FileInfo(filename, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Calendar.getInstance().getTime()), FileKit.Long2Str(filesize));
                                        list.add(fileInfo);
                                        list.add(aeskey);
                                        break;
                                    case "times_error":
                                        list.add("times_error");
                                        break;
                                    case "error":
                                        list.add("error");
                                        break;
                                }
                                break;
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    list.add("unknown_error");
                }
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + mode);
        }

        return list;
    }

    @Override
    protected void onPostExecute(List<Object> objects) {
        super.onPostExecute(objects);
        Context context = (Context) objects.get(0);
        int mode = (int) objects.get(1);
        switch (mode){
            case UPLOAD_CLOUD:
                String shareMD5 = (String)objects.get(2);
                if(shareMD5.equals("")){
                    Toast.makeText(context, context.getResources().getString(R.string.msg_share_failed), Toast.LENGTH_SHORT).show();
                }
                else{
                    try {
                        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData mClipData = ClipData.newPlainText("Label", shareMD5);
                        cm.setPrimaryClip(mClipData);
                        Toast.makeText(context, context.getResources().getString(R.string.msg_share_ok), Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            case DOWNLOAD_CLOUD:
                if(objects.get(2) instanceof String){
                    String code = (String) objects.get(2);
                    switch (code) {
                        case "error":
                            Toast.makeText(context, "链接错误", Toast.LENGTH_SHORT).show();
                            break;
                        case "times_error":
                            Toast.makeText(context, "链接分享次数用尽", Toast.LENGTH_SHORT).show();
                            break;
                        case "unknown_error":
                            Toast.makeText(context, "未知错误", Toast.LENGTH_SHORT).show();
                            break;
                    }
                }
                else if(objects.get(2) instanceof FileInfo){
                    FileInfo info = (FileInfo) objects.get(2);
                    String aeskey = (String) objects.get(3);
                    //加入数据库
                    SQLiteDatabase database = new LocalDatabaseHelper(context).getReadableDatabase();
                    Cursor cursor = database.query("lockedfile", null,"name=?",new String[]{ info.getFileName() },null,null,null);
                    if(cursor.getCount() != 0) {
                        //有重名
                        info.ResetName();
                    }
                    else{
                        //没有重名
                        //插入数据库
                        ContentValues values = new ContentValues();
                        values.put("name", info.getFileName());
                        values.put("path", Environment.getExternalStorageDirectory()+ "/Lockroom/" + info.getFileName());
                        values.put("nameMD5", DigestUtils.md5Hex(info.getFileName()));
                        values.put("aeskey", aeskey);
                        values.put("time", info.getFileTime());
                        values.put("size", info.getFileSize());
                        database.insert("lockedfile", null, values);
                        File file = new File(context.getCacheDir().getAbsolutePath()+"/temp");
                        File dstFile = new File(context.getExternalFilesDir("PrivateRoom").getAbsolutePath() + "/" + DigestUtils.md5Hex(info.getFileName()));

                        try {
                            FileUtils.copyFile(file, dstFile);
                            file.delete();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    cursor.close();
                    database.close();

                    FragmentActivity fragmentActivity = (FragmentActivity) objects.get(0);
                    ((privateroomFragment)(Objects.requireNonNull(fragmentActivity.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_content_main)).getChildFragmentManager().getFragments().get(0))).RefreshList();
                }
                else if(objects.get(2) instanceof DiaryInfo){
                    DiaryInfo info = (DiaryInfo) objects.get(2);
                    //加入数据库
                    SQLiteDatabase database = new LocalDatabaseHelper(context).getReadableDatabase();
                    Cursor cursor = database.query("diary", null,"timestamp=? and title=?",new String[]{ Long.toString(info.getTimestamp()), info.getTitle() },null,null,null);
                    if(cursor.getCount() != 0) {
                        //有重名
                        info.ResetTitle();
                    }
                    else {
                        //没有重名
                        ContentValues values = new ContentValues();
                        values.put("timestamp", info.getTimestamp());
                        values.put("title", info.getTitle());
                        values.put("contentAES", info.getContentAES());
                        database.insert("diary", null, values);

                        //解压、复制文件
                        FileKit.unpackZip(context.getCacheDir().getAbsolutePath()+"/temp", context.getExternalFilesDir("MyDiary").getAbsolutePath());
                    }
                    cursor.close();
                    database.close();

                    FragmentActivity fragmentActivity = (FragmentActivity) objects.get(0);
                    ((mydiaryFragment)(Objects.requireNonNull(fragmentActivity.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_content_main)).getChildFragmentManager().getFragments().get(0))).RefreshList();
                }

                break;
        }
    }
}
