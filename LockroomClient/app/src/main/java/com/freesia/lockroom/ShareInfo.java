package com.freesia.lockroom;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;

public class ShareInfo {
    private final File _file;
    private final int _total_times;
    private final boolean _isDiary;
    private final boolean _isPermanent;

    private String _name = null;
    private long _ts = 0;
    private String _title = null;

    private final String _key;

    public ShareInfo (Context context, int total_times, boolean isDiary, Object info, boolean isPermanent) {
        _total_times = total_times;
        _isDiary = isDiary;
        _isPermanent = isPermanent;

        if(_isDiary) {
            DiaryInfo diaryInfo = (DiaryInfo) info;
            _ts = diaryInfo.getTimestamp();
            _title = diaryInfo.getTitle();
            _key = diaryInfo.getContentAES();
            FileKit.ZipFile(diaryInfo.getEncodedPath(context), diaryInfo.getEncodedPath(context)+".zip");
            _file = new File(diaryInfo.getEncodedPath(context)+".zip");
        }
        else {
            FileInfo fileInfo = (FileInfo) info;
            _name = fileInfo.getFileName();
            _file = new File(fileInfo.getEncodedPath(context)); //构造文件对象
            //数据库查询文件的真实名字、加密AES
            SQLiteDatabase database = new LocalDatabaseHelper(context).getReadableDatabase();
            Cursor cursor = database.query("lockedfile", null,"name=?",
                    new String[]{ _name },null,null,null);
            if(cursor.getCount() != 0){
                cursor.moveToFirst();
                int idx = cursor.getColumnIndex("aeskey");
                _key = cursor.getString(idx);
            }
            else {
                _key = "";
            }
            cursor.close();
            database.close();
        }
    }

    public String getFilename(){
        if(_isDiary){
            return _ts+":"+_title;
        }
        else {
            return _name;
        }
    }
    public File getFile(){
        return _file;
    }

    public String getKey(){
        return _key;
    }
    public int getTotalTimes(){
        return _total_times;
    }
    public boolean getPermanent(){
        return _isPermanent;
    }
    public boolean getDiary(){
        return _isDiary;
    }

}
