package com.freesia.lockroom;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Pair;

import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentActivity;

import com.freesia.lockroom.ui.mydiaryFragment;
import com.freesia.lockroom.ui.privateroomFragment;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.RandomStringUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.crypto.Cipher;

public class LocalTask extends AsyncTask<Object, Void, List<Object>> {
    public static final int MOVE_IN = 1;
    public static final int MOVE_OUT = 2;
    public static final int DELETE = 3;
    public static final int RENAME = 4;
    public static final int OPEN = 5;

    public static final int DIARY_OPEN = 6;
    public static final int DIARY_SAVE = 7;
    public static final int DIARY_DELETE = 8;

    public static final int SINGLE = 0;
    public static final int OVERRRIDE = 1;


    @Override
    protected List<Object> doInBackground(Object... objects) {
        Context context = (Context) objects[0];
        int mode = (int) objects[1];
        int overrideState = SINGLE;
        Object arg1 = null;
        Object arg2 = null;

        SQLiteDatabase database;
        Cursor cursor;
        String srcPath;
        String fileName;
        DiaryInfo diaryInfo;

        switch (mode) {
            case MOVE_IN:
                Uri uri = (Uri) objects[2];
                srcPath = FileKit.Uri2Path(context, uri);
                if(!srcPath.isEmpty()) {
                    File srcFile = new File(srcPath);
                    String srcName = srcFile.getName();
                    String nameMD5 = DigestUtils.md5Hex(srcName);

                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                    String srcTime = simpleDateFormat.format(srcFile.lastModified());
                    String srcSize = FileKit.Long2Str(srcFile.length());
                    String AES_Key = RandomStringUtils.randomAlphanumeric(16);

                    database = new LocalDatabaseHelper(context).getReadableDatabase();
                    cursor = database.query("lockedfile", null,"name=?",new String[]{ srcName },null,null,null);
                    ContentValues values = new ContentValues();
                    values.put("name", srcName);
                    values.put("path", srcPath);
                    values.put("nameMD5", nameMD5);
                    values.put("aeskey", AES_Key);
                    values.put("time", srcTime);
                    values.put("size", srcSize);
                    if(cursor.getCount() == 0) {
                        //没有重名的
                        long code = database.insert("lockedfile", null, values);

                        if(code != -1) {
                            //加密
                            String targetPath = context.getExternalFilesDir("PrivateRoom").getAbsolutePath() + "/" + nameMD5;
                            try {
                                FileKit.AesFile(srcPath, targetPath, AES_Key, Cipher.ENCRYPT_MODE);
                            }
                            catch (Exception e) {
                                e.printStackTrace();
                            }

                            //删除文件
                            srcFile.delete();
                        }
                    }
                    else {
                        //有重名的
                        overrideState = OVERRRIDE;
                        arg1 = srcName;
                        arg2 = uri;
                    }
                    cursor.close();
                    database.close();
                }
                break;
            case MOVE_OUT:
                fileName = (String) objects[2];
                database = new LocalDatabaseHelper(context).getReadableDatabase();
                cursor = database.query("lockedfile", null,"name=?",new String[]{ fileName },null,null,null);
                if(cursor.getCount() != 0) {
                    //有结果
                    cursor.moveToFirst();
                    int pathIdx = cursor.getColumnIndex("path");
                    int md5Idx = cursor.getColumnIndex("nameMD5");
                    int keyIdx = cursor.getColumnIndex("aeskey");
                    String targetPath = cursor.getString(pathIdx);
                    srcPath = context.getExternalFilesDir("PrivateRoom").getAbsolutePath() + "/" + cursor.getString(md5Idx);
                    String key = cursor.getString(keyIdx);

                    File file = new File(targetPath);
                    if(file.exists()) {
                        overrideState = OVERRRIDE;
                        arg1 = targetPath;
                        arg2 = fileName;
                    }
                    else {
                        //解密
                        try {
                            SQLiteDatabase tmp = new LocalDatabaseHelper(context).getReadableDatabase();
                            tmp.execSQL("DELETE FROM lockedfile WHERE name = ?", new String[] {fileName});
                            tmp.close();
                            FileKit.AesFile(srcPath, targetPath, key, Cipher.DECRYPT_MODE);
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }

                        //删除文件
                        File del = new File(srcPath);
                        del.delete();
                    }
                }
                cursor.close();
                database.close();
                break;
            case DELETE:
                fileName = (String) objects[2];
                database = new LocalDatabaseHelper(context).getReadableDatabase();
                cursor = database.query("lockedfile", null,"name=?",new String[]{ fileName },null,null,null);
                if(cursor.getCount() != 0) {
                    //删除文件
                    String path = context.getExternalFilesDir("PrivateRoom").getAbsolutePath() + "/" + DigestUtils.md5Hex(fileName);
                    File del = new File(path);
                    del.delete();

                    //删除记录
                    database.execSQL("DELETE FROM lockedfile WHERE name = ?", new String[] {fileName});
                }
                cursor.close();
                database.close();
                break;
            case DIARY_DELETE:
                diaryInfo = (DiaryInfo) objects[2];
                database = new LocalDatabaseHelper(context).getReadableDatabase();
                cursor = database.query("diary", null,"timestamp=? and title=?",new String[]{Long.toString(diaryInfo.getTimestamp()), diaryInfo.getTitle()},null,null,null);
                if(cursor.getCount() != 0) {
                    //删除diary
                    FileKit.deleteDir(new File(diaryInfo.getEncodedPath(context)));

                    //删除记录
                    database.execSQL("DELETE FROM diary WHERE timestamp=? and title=?", new String[] {Long.toString(diaryInfo.getTimestamp()), diaryInfo.getTitle()});
                }
                cursor.close();
                database.close();
                break;
            case RENAME:
                String oldName = (String) objects[2];
                String oldMD5 = DigestUtils.md5Hex(oldName);
                String newName = (String) objects[3];
                String newMD5 = DigestUtils.md5Hex(newName);

                database = new LocalDatabaseHelper(context).getReadableDatabase();
                cursor = database.query("lockedfile", null,"name=?",new String[]{ oldName },null,null,null);
                if(cursor.getCount() != 0) {
                    cursor.moveToFirst();
                    int idx = cursor.getColumnIndex("path");
                    String oldPath = cursor.getString(idx);
                    String newPath = oldPath.substring(0, oldPath.lastIndexOf('/') + 1) + newName;


                    //重命名文件
                    File oldFile = new File(context.getExternalFilesDir("PrivateRoom").getAbsolutePath() + "/" + oldMD5);
                    File newFile = new File(context.getExternalFilesDir("PrivateRoom").getAbsolutePath() + "/" + newMD5);

                    if(!newFile.exists()) {
                        if(oldFile.renameTo(newFile))
                        {
                            //更改记录
                            database.execSQL("UPDATE lockedfile SET name = ?, nameMD5 = ?, path = ? WHERE name = ?", new String[] {newName, newMD5, newPath, oldName});
                        }
                    }
                }
                cursor.close();
                database.close();
                break;
            case OPEN:
                fileName = (String) objects[2];
                database = new LocalDatabaseHelper(context).getReadableDatabase();
                cursor = database.query("lockedfile", null,"name=?",new String[]{ fileName },null,null,null);
                if(cursor.getCount() != 0) {
                    cursor.moveToFirst();
                    String path = context.getExternalFilesDir("PrivateRoom").getAbsolutePath() + "/" + DigestUtils.md5Hex(fileName);
                    File folder = new File(context.getCacheDir().getAbsolutePath() + "/PrivateRoom");
                    if(!folder.exists()) {
                        folder.mkdir();
                    }
                    String cachePath = context.getCacheDir().getAbsolutePath() + "/PrivateRoom/" +fileName;
                    int idx = cursor.getColumnIndex("aeskey");
                    String key = cursor.getString(idx);

                    try {
                        FileKit.AesFile(path, cachePath, key, Cipher.DECRYPT_MODE);
                        arg1 = cachePath;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                cursor.close();
                database.close();
                break;
            case DIARY_OPEN:
                diaryInfo = (DiaryInfo) objects[2];
                String cachePath = context.getCacheDir().getAbsolutePath();
                File folder = new File(cachePath);
                if(!folder.exists()) {
                    folder.mkdir();
                }

                srcPath = diaryInfo.getEncodedPath(context);
                try {
                    //解密文件
                    FileKit.AesFile(srcPath+"/content.diary", cachePath+"/content.raw", diaryInfo.getContentAES(), Cipher.DECRYPT_MODE);

                    File mediaCache = new File(context.getCacheDir().getAbsolutePath()+"/media/");
                    if(!mediaCache.exists()) {
                        mediaCache.mkdir();
                    }
                    for(Pair<String, String> pair:diaryInfo.getMediaAES(context)){
                        FileKit.AesFile(srcPath+"/media/"+DigestUtils.md5Hex(pair.first), context.getCacheDir().getAbsolutePath()+"/media/"+pair.first, pair.second, Cipher.DECRYPT_MODE);
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
                arg1 = diaryInfo;
                break;
            case DIARY_SAVE:
                diaryInfo = (DiaryInfo) objects[2];
                DiaryInfo old_diary = (DiaryInfo) objects[4];
                List<Pair<String, String>> mediaList = (List<Pair<String, String>>) objects[3];

                database = new LocalDatabaseHelper(context).getReadableDatabase();
                cursor = database.query("diary", null,"timestamp=? and title=?",new String[]{Long.toString(diaryInfo.getTimestamp()), diaryInfo.getTitle()},null,null,null);
                ContentValues values = new ContentValues();
                values.put("timestamp", diaryInfo.getTimestamp());
                values.put("title", diaryInfo.getTitle());
                values.put("contentAES", diaryInfo.getContentAES());
                if(old_diary == null) {
                    //新建
                    if(cursor.getCount() != 0) {
                        overrideState = OVERRRIDE;
                    }
                }
                else {
                    //保存
                    //删除文件夹
                    FileKit.deleteDir(new File(old_diary.getEncodedPath(context)));

                    //删除数据库记录
                    database.delete("diary", "timestamp=? and title=?", new String[]{Long.toString(old_diary.getTimestamp()),  old_diary.getTitle()});
                }
                if(overrideState != OVERRRIDE){
                    //插入新的记录
                    long code = database.insert("diary", null, values);
                    if(code != -1){
                        try {
                            //新建文件夹
                            File encodeFolder = new File(diaryInfo.getEncodedPath(context));
                            if(!encodeFolder.exists()) {
                                encodeFolder.mkdir();
                            }

                            //content加密
                            FileKit.AesFile(context.getCacheDir().getAbsolutePath() + "/content.raw",
                                    diaryInfo.getEncodedPath(context) + "/content.diary",
                                    diaryInfo.getContentAES(), Cipher.ENCRYPT_MODE);
                            //new File(diaryInfo.getEncodedPath(context) + "/content.raw").delete();

                            File mediaFolder = new File(diaryInfo.getEncodedPath(context) + "/media");
                            if(!mediaFolder.exists()) {
                                mediaFolder.mkdir();
                            }

                            //把媒体文件加密移过来
                            for(Pair<String, String> media: mediaList) {
                                FileKit.AesFile(context.getCacheDir().getAbsolutePath()+"/media/"+media.first,
                                        diaryInfo.getEncodedPath(context)+"/media/"+DigestUtils.md5Hex(media.first),
                                        media.second, Cipher.ENCRYPT_MODE);
                            }

                            arg1 = diaryInfo;
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                else{
                    arg1 = diaryInfo;
                    arg2 = mediaList;
                }
                cursor.close();
                database.close();
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + mode);
        }

        List<Object> list = new ArrayList<>();
        list.add(context);
        list.add(mode);
        list.add(overrideState);
        list.add(arg1);
        list.add(arg2);
        return list;
    }

    @Override
    protected void onPostExecute(List<Object> objects) {
        super.onPostExecute(objects);

        Context context = (Context) objects.get(0);
        int mode = (int) objects.get(1);

        if((int) objects.get(2) == OVERRRIDE) {
            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setCancelable(false)
                    .setTitle(context.getResources().getString(R.string.title_override))
                    .setMessage(context.getResources().getString(R.string.msg_same_name))
                    .setPositiveButton(context.getResources().getString(R.string.apply), (dialogInterface, i) -> {
                        switch ((int) objects.get(1)) {
                            case MOVE_IN:
                                new LocalTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, objects.get(0), DELETE, objects.get(3));
                                new LocalTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, objects.get(0), MOVE_IN, objects.get(4));
                                break;
                            case MOVE_OUT:
                                new File((String) objects.get(3)).delete();
                                new LocalTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, objects.get(0), MOVE_OUT, objects.get(4));
                                break;
                            case DIARY_SAVE:
                                new LocalTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, objects.get(0), DIARY_DELETE, objects.get(3));
                                new LocalTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, objects.get(0), DIARY_SAVE, objects.get(3), objects.get(4), null);
                                break;
                            default:
                                throw new IllegalStateException("Unexpected value: " + (int) objects.get(1));
                        }
                    })
                    .setNegativeButton(context.getResources().getString(R.string.cancel),null)
                    .create();
            dialog.show();
        }
        else if((int) objects.get(2) == SINGLE) {
            switch ((int) objects.get(1)) {
                case OPEN:
                    String cachePath = (String) objects.get(3);

                    File file = new File(cachePath);
                    Intent intent = new Intent("android.intent.action.VIEW");
                    intent.setDataAndType(FileProvider.getUriForFile(context,context.getApplicationContext().getPackageName() + ".provider", file), "*/*");
                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    context.startActivity(intent);

                    break;
                case DIARY_OPEN:
                    DiaryInfo diaryInfo = (DiaryInfo) objects.get(3);

                    Intent openDiary = new Intent();
                    openDiary.setClass(context, DiaryViewActivity.class);
                    openDiary.putExtra("diaryInfo", diaryInfo);
                    context.startActivity(openDiary);
                    break;
                case DIARY_SAVE:
                    FileKit.ClearDiaryCache(context);
                    break;
                default:
                    break;
            }
        }

        if(mode != DIARY_OPEN && mode != DIARY_SAVE && mode != DIARY_DELETE) {
            FragmentActivity fragmentActivity = (FragmentActivity) objects.get(0);
            ((privateroomFragment)(Objects.requireNonNull(fragmentActivity.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_content_main)).getChildFragmentManager().getFragments().get(0))).RefreshList();
        }

        if(mode == DIARY_DELETE){
            FragmentActivity fragmentActivity = (FragmentActivity) objects.get(0);
            ((mydiaryFragment)(Objects.requireNonNull(fragmentActivity.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_content_main)).getChildFragmentManager().getFragments().get(0))).RefreshList();
        }

    }
}
