package com.freesia.lockroom;


import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;


import org.apache.commons.io.FileUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.SecretKeySpec;

public class FileKit {
    public static String Uri2Path(Context context, Uri uri) {
        if (uri == null) {
            return null;
        }

        if(ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
            return uri.getPath();
        }
        else if(ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            String authority = uri.getAuthority();

            if(authority.startsWith("com.android.externalstorage")) {
                return Environment.getExternalStorageDirectory() + "/" + uri.getPath().split(":")[1];
            }
            else {
                String idStr;
                if(authority.equals("media")) {
                    idStr = uri.toString().substring(uri.toString().lastIndexOf('/') + 1);
                }
                else if(authority.startsWith("com.android.providers")) {
                    String rawPath = uri.getPath();
                    if(rawPath.indexOf(':') == -1) {
                        return "";
                    }
                    else {
                        String type = rawPath.split(":")[0].substring(rawPath.split(":")[0].lastIndexOf('/') + 1);

                        if(type.equals("raw")) {
                            return rawPath.split(":")[1];
                        }
                        else {
                            idStr = rawPath.split(":")[1];
                        }
                    }
                }
                else {
                    String rawPath = uri.getPath();

                    return Environment.getExternalStorageDirectory() + rawPath.substring(rawPath.indexOf("/0/") + 2);
                }

                ContentResolver contentResolver = context.getContentResolver();
                Cursor cursor = contentResolver.query(MediaStore.Files.getContentUri("external"),
                        new String[] {MediaStore.Files.FileColumns.DATA},
                        "_id=?",
                        new String[]{idStr}, null);
                if (cursor != null) {
                    cursor.moveToFirst();
                    try {
                        int idx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA);

                        return cursor.getString(idx);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        cursor.close();
                    }
                }
            }
        }
        return null;
    }

    public static File Uri2File(Uri uri, Context context, String path, String name) {
        File file = null;
        if(uri == null)
        {
            return null;
        }
        if (uri.getScheme().equals(ContentResolver.SCHEME_FILE))
        {
            file = new File(uri.getPath());
        }
        else if (uri.getScheme().equals(ContentResolver.SCHEME_CONTENT))
        {
            ContentResolver contentResolver = context.getContentResolver();
            Cursor cursor = contentResolver.query(uri, null, null, null, null);
            String displayName="";
            if (cursor.moveToFirst())
            {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                displayName = cursor.getString(index);
            }
            cursor.close();
            try
            {
                InputStream is = contentResolver.openInputStream(uri);
                displayName = name + displayName.substring(displayName.lastIndexOf('.'));
                file = new File(path, displayName);
                FileUtils.copyInputStreamToFile(is, file);
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return file;
    }


    public static File Uri2File(Uri uri, Context context, String path) {
        File file = null;
        if(uri == null)
        {
            return null;
        }
        if (uri.getScheme().equals(ContentResolver.SCHEME_FILE))
        {
            file = new File(uri.getPath());
        }
        else if (uri.getScheme().equals(ContentResolver.SCHEME_CONTENT))
        {
            ContentResolver contentResolver = context.getContentResolver();
            Cursor cursor = contentResolver.query(uri, null, null, null, null);
            String displayName="";
            if (cursor.moveToFirst())
            {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                displayName = cursor.getString(index);
            }
            cursor.close();
            try
            {
                InputStream is = contentResolver.openInputStream(uri);
                file = new File(path, displayName);
                FileUtils.copyInputStreamToFile(is, file);
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return file;
    }

    public static void AesFile(String srcPath, String dstPath, String key, int mode)
            throws Exception {
        File srcFile = new File(srcPath);
        if(!srcFile.exists()) {
            throw new Exception("Not such file");
        }
        File dstFile = new File(dstPath);
        dstFile.createNewFile();
        InputStream in = new FileInputStream(srcFile);
        OutputStream out = new FileOutputStream(dstFile);
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),
                "AES/ECB/PKCS5Padding");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING");
        cipher.init(mode, secretKeySpec);
        CipherOutputStream cipherOutputStream = new CipherOutputStream(out, cipher);
        byte[] cache = new byte[1024];
        int nRead;
        while ((nRead = in.read(cache)) != -1) {
            cipherOutputStream.write(cache, 0, nRead);
            cipherOutputStream.flush();
        }
        cipherOutputStream.close();
        out.close();
        in.close();
    }

    public static String Long2Str(long size) {
        if(size <=0 || size >= 1024 * 1024 * 1024)
        {
            return "0B";
        }

        double dSize;

        try {
            dSize = size;
        } catch (Exception e) {
            e.printStackTrace();
            return "0B";
        }

        double divideBasic = 1024;
        if (size < 1024) {
            //1kb以内
            if (size < 1000) {
                return size + "B";
            } else {
                //大于1000B,转化为kb
                return String.format(Locale.getDefault(),"%.2f", dSize / divideBasic) + "K";
            }
        } else if (size < 1024 * 1024) {
            //1M以内
            if (size < 1024 * 1000) {
                return String.format(Locale.getDefault(),"%.2f", dSize / divideBasic) + "K";
            } else {
                //大于1000Kb,转化为M
                return String.format(Locale.getDefault(),"%.2f", dSize / divideBasic / divideBasic) + "M";
            }
        } else {
            //1TB以内
            if (size < 1024 * 1024 * 1000) {
                return String.format(Locale.getDefault(),"%.2f", dSize / divideBasic / divideBasic) + "M";
            } else {
                //大于1000Mb,转化为G
                return String.format(Locale.getDefault(),"%.2f", dSize / divideBasic / divideBasic / divideBasic) + "G";
            }
        }
    }

    public static void deleteDir(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory())
            return;
        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (file.isFile())
                file.delete();
            else if (file.isDirectory())
                deleteDir(file);
        }
        dir.delete();
    }

    public static void ClearDiaryCache(Context context) {
        File cnt = new File(context.getCacheDir().getAbsolutePath() + "/content.raw");
        cnt.delete();
        File dir = new File(context.getCacheDir().getAbsoluteFile() + "/media");
        deleteDir(dir);
    }

    public static void ClearPrivateRoomCache(Context context) {
        File dir = new File(context.getCacheDir().getAbsolutePath() + "/PrivateRoom");
        deleteDir(dir);
    }

    private static void compress(File sourceFile, ZipOutputStream zos, String name) throws IOException {
        byte[] buf = new byte[1024];
        if(sourceFile.isFile()){
            // 压缩单个文件，压缩后文件名为当前文件名
            zos.putNextEntry(new ZipEntry(name));
            // copy文件到zip输出流中
            int len;
            FileInputStream in = new FileInputStream(sourceFile);
            while ((len = in.read(buf)) != -1){
                zos.write(buf, 0, len);
            }
            zos.closeEntry();
            in.close();
        } else {
            File[] listFiles = sourceFile.listFiles();
            if(listFiles == null || listFiles.length == 0){
                // 空文件夹的处理(创建一个空ZipEntry)
                zos.putNextEntry(new ZipEntry(name + "/"));
                zos.closeEntry();
            }else {
                // 递归压缩文件夹下的文件
                for (File file : listFiles) {
                    compress(file, zos, name + "/" + file.getName());
                }
            }
        }
    }

    public static void ZipFile(String srcPath, String dstPath) {
        try {
            FileOutputStream out = new FileOutputStream(dstPath);
            ZipOutputStream zipOutputStream = new ZipOutputStream(out);
            File sourceFile = new File(srcPath);

            compress(sourceFile, zipOutputStream, sourceFile.getName());

            zipOutputStream.flush();
            zipOutputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void unpackZip(String path, String dstpath)
    {
        InputStream is;
        ZipInputStream zis;
        try {
            String filename;
            is = new FileInputStream(path);
            zis = new ZipInputStream(new BufferedInputStream(is));
            ZipEntry ze;
            byte[] buffer = new byte[1024];
            int count;

            while ((ze = zis.getNextEntry()) != null) {
                filename = ze.getName();

                // Need to create directories if not exists, or
                // it will generate an Exception...
                if (ze.isDirectory()) {
                    File fmd = new File(dstpath +"/"+ filename);
                    fmd.mkdirs();
                    continue;
                }
                File file = new File(dstpath +"/"+ filename);
                if (!file.exists()) {
                    Objects.requireNonNull(file.getParentFile()).mkdirs();
                    file.createNewFile();

                }
                FileOutputStream fout = new FileOutputStream(dstpath +"/"+ filename);

                while ((count = zis.read(buffer)) != -1) {
                    fout.write(buffer, 0, count);
                }

                fout.close();
                zis.closeEntry();
            }

            zis.close();
        }
        catch(IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean checkConnectNetwork(Context context) {
        ConnectivityManager conn = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo net = conn.getActiveNetworkInfo();
        return net != null && net.isConnected();
    }
}
