package com.freesia.lockroom;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

public class LocalDatabaseHelper extends SQLiteOpenHelper {
    private static final String DatabaseName = "lockroom.db";
    private static final int DB_VERSION = 1;

    public LocalDatabaseHelper(@Nullable Context context) {
        super(context, DatabaseName, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        String sql1 = "CREATE TABLE authentication (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, MD5 CHAR(32))";
        sqLiteDatabase.execSQL(sql1);
        String sql2 = "CREATE TABLE lockedfile (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name VARCHAR(255), path VARCHAR(4096), nameMD5 CHAR(32), aeskey CHAR(16), time CHAR(20), size VARCHAR(20))";
        sqLiteDatabase.execSQL(sql2);
        String sql3 = "CREATE TABLE diary (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timestamp INTEGER, title VARCHAR(255), contentAES CHAR(16))";
        sqLiteDatabase.execSQL(sql3);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        if(i1 > i){
            String sql1 = "DROP TABLE IF EXISTS authentication";
            sqLiteDatabase.execSQL(sql1);
            String sql2 = "DROP TABLE IF EXISTS lockedfile";
            sqLiteDatabase.execSQL(sql2);
            String sql3 = "DROP TABLE IF EXISTS diary";
            sqLiteDatabase.execSQL(sql3);
            onCreate(sqLiteDatabase);
        }
    }
}
