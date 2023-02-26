package com.freesia.lockroom;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Pair;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;


public class DiaryViewActivity extends AppCompatActivity {
    private TextView dateText;
    private TextView locationText;
    private String location;
    private EditText titleEdit;
    private LinearLayout contentLayout;

    private EditText lastText = null;

    private Calendar date;

    private ActivityResultLauncher<Intent> insertImage;
    private ActivityResultLauncher<Intent> insertVideo;
    private ActivityResultLauncher<Intent> replaceImage;
    private ActivityResultLauncher<Intent> replaceVideo;
    private ImageView replacedView = null;

    private DiaryInfo old_diary = null;

    private String to_be_used_MediaName = null;

    private MediaRecorder recorder;

    private AMapLocationClient mLocationClient = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_diary_view);
        dateText = findViewById(R.id.diaryDateText);
        locationText = findViewById(R.id.diaryLocationText);
        titleEdit = findViewById(R.id.diaryTitleEdit);
        contentLayout = findViewById(R.id.diaryContentLayout);
        ImageView addBtn = findViewById(R.id.addBtn);

        try {
            AMapLocationListener mAMapLocationListener = amapLocation -> {
                if (amapLocation != null && amapLocation.getErrorCode() == 0) {
                    //成功
                    String str = amapLocation.getAddress();
                    if(!str.equals("")){
                        location = str;
                        locationText.setText(String.format(getResources().getString(R.string.text_loc), location));
                    }
                }
                else{
                    //手动
                    Toast.makeText(DiaryViewActivity.this, getResources().getString(R.string.loc_error), Toast.LENGTH_SHORT).show();
                }
            };
            AMapLocationClient.updatePrivacyShow(getApplicationContext(),true,true);
            AMapLocationClient.updatePrivacyAgree(getApplicationContext(),true);
            //初始化定位
            mLocationClient = new AMapLocationClient(getApplicationContext());
            //设置定位回调监听
            mLocationClient.setLocationListener(mAMapLocationListener);
        } catch (Exception e) {
            e.printStackTrace();
        }




        date = Calendar.getInstance();
        old_diary = (DiaryInfo) getIntent().getSerializableExtra("diaryInfo");
        if(old_diary == null) {
            //新建笔记
            Objects.requireNonNull(DiaryViewActivity.this.getSupportActionBar()).setTitle("新的日记");
            titleEdit.setHint(getResources().getString(R.string.title));
            location = getResources().getString(R.string.loc_default);


            //默认新建一个文本
            lastText = new EditText(DiaryViewActivity.this);
            lastText.setHint(getResources().getString(R.string.tip_input));
            lastText.setGravity(Gravity.TOP);
            lastText.setWidth(LinearLayout.LayoutParams.MATCH_PARENT);
            lastText.setHeight(LinearLayout.LayoutParams.WRAP_CONTENT);
            lastText.setMaxLines(1024);
            lastText.setBackground(null);
            contentLayout.addView(lastText);
        }
        else {
            //打开笔记
            loadDiary(old_diary);
        }

        dateText.setText(new SimpleDateFormat("yyyy/MM/dd", Locale.CHINA).format(date.getTime()));
        dateText.setOnClickListener(view -> new DatePickerDialog(DiaryViewActivity.this, DatePickerDialog.THEME_DEVICE_DEFAULT_DARK, (datePicker, year, month, day) -> {
            date.set(year, month, day);
            dateText.setText(new SimpleDateFormat("yyyy/MM/dd", Locale.CHINA).format(date.getTime()));
        }, date.get(Calendar.YEAR), date.get(Calendar.MONTH),date.get(Calendar.DAY_OF_MONTH)).show());
        locationText.setText(String.format(getResources().getString(R.string.text_loc), location));
        locationText.setOnClickListener(view -> {
            if (!FileKit.checkConnectNetwork(getApplicationContext())) {
                //没网
                Toast.makeText(DiaryViewActivity.this, getResources().getString(R.string.tip_network), Toast.LENGTH_SHORT).show();
            }
            else{
                AMapLocationClientOption mLocationOption = new AMapLocationClientOption();
                mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
                mLocationOption.setOnceLocation(true);
                mLocationOption.setOnceLocationLatest(true);
                mLocationOption.setNeedAddress(true);
                mLocationOption.setMockEnable(true);
                mLocationOption.setHttpTimeOut(10000);
                //给定位客户端对象设置定位参数
                mLocationClient.setLocationOption(mLocationOption);
                //启动定位
                mLocationClient.startLocation();
            }
        });


        addBtn.setOnClickListener(this::showInsertMenu);

        insertImage = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if(result.getResultCode()== Activity.RESULT_OK) {
                assert result.getData() != null;
                Uri imgUri = result.getData().getData();
                File folder = new File(getCacheDir().getAbsolutePath()+"/media");
                if(!folder.exists()) {
                    folder.mkdir();
                }
                File img = FileKit.Uri2File(imgUri, DiaryViewActivity.this, getCacheDir().getAbsolutePath()+"/media", Long.toString(Calendar.getInstance().getTimeInMillis()));
                if(img != null)
                {
                    ChangeLastText();

                    ImageView imageView = new ImageView(DiaryViewActivity.this);
                    imageView.setTag("[pic:" + img.getName() + "]");
                    imageView.setImageURI(Uri.fromFile(img));
                    imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    imageView.setOnLongClickListener(view -> {
                        showMediaMenu(view);
                        return true;
                    });
                    contentLayout.addView(imageView);
                    CreateNewText();
                }
            }
        });
        insertVideo = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if(result.getResultCode()== Activity.RESULT_OK) {
                assert result.getData() != null;
                Uri vidUri = result.getData().getData();
                File folder = new File(getCacheDir().getAbsolutePath()+"/media");
                if(!folder.exists()) {
                    folder.mkdir();
                }
                File vid = FileKit.Uri2File(vidUri, DiaryViewActivity.this, getCacheDir().getAbsolutePath()+"/media");
                if(vid != null) {
                    ChangeLastText();

                    //截取第一帧作为预览
                    MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
                    metadataRetriever.setDataSource(DiaryViewActivity.this, Uri.fromFile(vid));

                    ImageView imageView = new ImageView(DiaryViewActivity.this);
                    imageView.setTag("[vid:" + vid.getName() + "]");
                    imageView.setImageBitmap(metadataRetriever.getFrameAtTime());
                    imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    imageView.setOnLongClickListener(view -> {
                        showMediaMenu(view);
                        return true;
                    });
                    imageView.setOnClickListener(view -> {
                        Intent intent = new Intent("android.intent.action.VIEW");
                        intent.setDataAndType(vidUri, "video/*");
                        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(intent);
                    });
                    contentLayout.addView(imageView);
                    CreateNewText();
                }
            }
        });


        replaceImage = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if(result.getResultCode()== Activity.RESULT_OK)
            {
                assert result.getData() != null;
                Uri imgUri = result.getData().getData();
                File folder = new File(getCacheDir().getAbsolutePath()+"/media");
                if(!folder.exists()) {
                    folder.mkdir();
                }
                File img = FileKit.Uri2File(imgUri, DiaryViewActivity.this, getCacheDir().getAbsolutePath()+"/media", Long.toString(Calendar.getInstance().getTimeInMillis()));
                if(img != null && replaceImage != null)
                {
                    ImageView imageView = replacedView;
                    imageView.setTag("[pic:" + img.getName() + "]");
                    imageView.setImageURI(Uri.fromFile(img));
                    imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    imageView.setOnLongClickListener(view -> {
                        showMediaMenu(view);
                        return true;
                    });
                    DeleteNoUsedMedia();
                }
            }
        });

        replaceVideo = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if(result.getResultCode()== Activity.RESULT_OK) {
                assert result.getData() != null;
                Uri vidUri = result.getData().getData();
                File folder = new File(getCacheDir().getAbsolutePath()+"/media");
                if(!folder.exists()) {
                    folder.mkdir();
                }
                File vid = FileKit.Uri2File(vidUri, DiaryViewActivity.this, getCacheDir().getAbsolutePath()+"/media");
                if(vid != null && replacedView != null) {
                    //截取第一帧作为预览
                    MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
                    metadataRetriever.setDataSource(DiaryViewActivity.this, Uri.fromFile(vid));

                    ImageView imageView = replacedView;
                    imageView.setTag("[vid:" + vid.getName() + "]");
                    imageView.setImageBitmap(metadataRetriever.getFrameAtTime());
                    imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    imageView.setOnLongClickListener(view -> {
                        showMediaMenu(view);
                        return true;
                    });
                    imageView.setOnClickListener(view -> {
                        Intent intent = new Intent("android.intent.action.VIEW");
                        intent.setDataAndType(vidUri, "video/*");
                        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(intent);
                    });
                    DeleteNoUsedMedia();
                }
            }
        });
    }

    private void ChangeLastText() {
        if(lastText == null) return;
        if(lastText.getText().toString().equals("")) {
            contentLayout.removeView(lastText);
        }
        else {
            //绑定事件
            lastText.setOnFocusChangeListener((view1, b) -> {
                if(!b && ((EditText)view1).getText().toString().equals("")) {
                    new AlertDialog.Builder(DiaryViewActivity.this)
                            .setMessage(getResources().getString(R.string.msg_rm_box))
                            .setPositiveButton(getResources().getString(R.string.apply), (dialogInterface, i) -> contentLayout.removeView(view1))
                            .setNegativeButton(getResources().getString(R.string.cancel), null)
                            .show();
                }
            });
        }
    }

    private void DeleteNoUsedMedia(){
        if(to_be_used_MediaName != null) {
            File temp = new File(getCacheDir().getAbsolutePath()+"/media/"+to_be_used_MediaName);
            if(temp.exists()){
                temp.delete();
            }
        }
    }

    private void CreateNewText() {
        //新建一个文本
        lastText = new EditText(DiaryViewActivity.this);
        lastText.setHint(getResources().getString(R.string.tip_input));
        lastText.setGravity(Gravity.TOP);
        lastText.setWidth(LinearLayout.LayoutParams.MATCH_PARENT);
        lastText.setHeight(LinearLayout.LayoutParams.WRAP_CONTENT);
        lastText.setMaxLines(1024);
        lastText.setBackground(null);
        contentLayout.addView(lastText);
    }

    @SuppressLint("InflateParams")
    private void showMediaMenu(View view) {
        View popupView = LayoutInflater.from(DiaryViewActivity.this).inflate(R.layout.media_menu, null);
        ImageView rmBtn = popupView.findViewById(R.id.media_removeBtn);
        ImageView rpBtn = popupView.findViewById(R.id.media_replaceBtn);
        ImageView dlBtn = popupView.findViewById(R.id.media_downloadBtn);
        PopupWindow window = new PopupWindow(popupView, 600, 200);
        window.setOutsideTouchable(true);

        rmBtn.setOnClickListener(v -> {
            String tag = (String) view.getTag();
            to_be_used_MediaName = tag.substring(5, tag.length()-1);
            view.setVisibility(View.GONE);
            DeleteNoUsedMedia();
            window.dismiss();
        });
        rpBtn.setOnClickListener(v -> {
            //判断媒体类型
            int type = 0; //0-pic 1-vid 2-rec
            String tag = (String) view.getTag();
            if(tag.startsWith("[rec:")) {
                type = 2;
            }
            else if(tag.startsWith("[vid:")) {
                type = 1;
            }
            to_be_used_MediaName = tag.substring(5, tag.length()-1);
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            switch (type) {
                case 0:
                    intent.setType("image/*");
                    replacedView = (ImageView) view;
                    replaceImage.launch(intent);
                    break;
                case 1:
                    intent.setType("video/*");
                    replacedView = (ImageView) view;
                    replaceVideo.launch(intent);
                    break;
                case 2:
                    view.setTag("[rec:start]");
                    DeleteNoUsedMedia();
                    Toast.makeText(DiaryViewActivity.this, "请重新录音", Toast.LENGTH_SHORT).show();
                    break;
            }
            window.dismiss();
        });
        dlBtn.setOnClickListener(v -> {
            String tag = (String) view.getTag();
            to_be_used_MediaName = tag.substring(5, tag.length()-1);
            File file = new File(Environment.getExternalStorageDirectory()+ "/LockRoom/" + to_be_used_MediaName);
            try {
                FileUtils.copyInputStreamToFile(new FileInputStream(getCacheDir().getAbsolutePath()+"/media/"+to_be_used_MediaName), file);
                Toast.makeText(DiaryViewActivity.this, "下载成功！路径："+file.getPath(), Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(DiaryViewActivity.this, "下载失败",Toast.LENGTH_SHORT).show();
            }
            window.dismiss();
        });

        window.showAsDropDown(view, 100, 0);
    }

    @SuppressLint("InflateParams")
    private void showInsertMenu(@NonNull View view) {
        View popupView = LayoutInflater.from(DiaryViewActivity.this).inflate(R.layout.select_media_menu, null);
        ImageView imgBtn = popupView.findViewById(R.id.imgBtn);
        ImageView vidBtn = popupView.findViewById(R.id.vidBtn);
        ImageView recBtn = popupView.findViewById(R.id.recBtn);
        PopupWindow window = new PopupWindow(popupView, 200, 650);
        window.setOutsideTouchable(true);

        imgBtn.setOnClickListener(view12 -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            insertImage.launch(intent);
        });
        vidBtn.setOnClickListener(view1 -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("video/*");
            insertVideo.launch(intent);
        });
        recBtn.setOnClickListener(view14 -> {
            ChangeLastText();

            ImageView imageView = new ImageView(DiaryViewActivity.this);
            imageView.setBackgroundResource(R.drawable.ic_rec_bg);
            imageView.setClickable(true);
            imageView.setFocusable(true);
            imageView.setTag("[rec:start]");
            imageView.setImageResource(R.drawable.ic_play);
            imageView.setPadding(0,0,350,0);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(500, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMarginStart(100);
            imageView.setLayoutParams(params);
            imageView.setOnClickListener(view13 -> {
                if(imageView.getTag().equals("[rec:start]")){
                    imageView.setImageResource(R.drawable.ic_stop);
                    String filename = Calendar.getInstance().getTimeInMillis()+".m4a";
                    imageView.setTag("[rec:recording;" + filename + "]");
                    //开始录音
                    startRecordAudio(getCacheDir().getAbsolutePath()+"/media/"+filename);
                }
                else if(((String)imageView.getTag()).startsWith("[rec:recording;")){
                    //停止录音
                    String tag = (String)imageView.getTag();
                    String filename = tag.substring(tag.indexOf(';')+1, tag.length()-1);
                    imageView.setTag("[rec:"+filename+"]");
                    imageView.setImageResource(R.drawable.ic_play);

                    stopRecordAudio();
                }
                else{
                    //打开录音
                    String tag = (String)imageView.getTag();
                    String filename = tag.substring(5, tag.length()-1);
                    File file = new File(getCacheDir().getAbsolutePath()+"/media/"+filename);
                    Intent intent = new Intent("android.intent.action.VIEW");
                    intent.setDataAndType(FileProvider.getUriForFile(DiaryViewActivity.this,getApplicationContext().getPackageName() + ".provider", file), "audio/*");
                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);
                }
            });
            imageView.setOnLongClickListener(view15 -> {
                DiaryViewActivity.this.showMediaMenu(view15);
                return true;
            });

            contentLayout.addView(imageView);

            CreateNewText();
        });


        int[] location = new int[2];
        view.getLocationOnScreen(location);
        window.showAtLocation(view, Gravity.NO_GRAVITY, location[0], location[1]-window.getHeight()-40);

    }

    void startRecordAudio(String path){
        if(recorder ==null){
            recorder =new MediaRecorder();
        }
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);//设置输出格式
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.HE_AAC);//设置编码格式
        recorder.setOutputFile(path);//设置输出路径
        try {
            recorder.prepare();//准备
            recorder.start();//开始录音
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void stopRecordAudio(){
        if(recorder !=null){
            recorder.stop();//停止录音
            recorder.reset();//重置
            recorder.release();//释放资源
            recorder =null;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN){
            new AlertDialog.Builder(this)
                    .setTitle(getResources().getString(R.string.msg_save))
                    .setNegativeButton(getResources().getString(R.string.cancel), (dialogInterface, i) -> DiaryViewActivity.this.finish())
                    .setPositiveButton(getResources().getString(R.string.apply), (dialogInterface, i) -> {
                        saveDiary();
                        DiaryViewActivity.this.finish();
                    })
                    .show();
        }
        return false;
    }

    private void saveDiary() {
        String title = titleEdit.getText().toString();
        if(title.equals("")) {
            title = "myDiary";
        }
        DiaryInfo diaryInfo = new DiaryInfo(date.getTimeInMillis(), title, RandomStringUtils.randomAlphanumeric(16));
        String dirPath = getCacheDir().getAbsolutePath();

        File folder = new File(dirPath);
        if(!folder.exists()) {
            folder.mkdir();
        }
        File contentDiary = new File(dirPath + "/content.raw");
        if(contentDiary.exists()) {
            contentDiary.delete();
        }
        List<Pair<String, String>> mediaList = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        for(int i = 0; i < contentLayout.getChildCount(); i++) {
            View view = contentLayout.getChildAt(i);
            if(view.getVisibility() == View.GONE) {
                continue;
            }
            if(view instanceof TextView) {
                contents.add(((TextView) view).getText().toString());
            }
            else if(view instanceof ImageView) {
                String tag = (String) view.getTag();
                if(tag.equals("[rec:start]") ||tag.startsWith("[rec:recording;")){
                    continue;
                }
                contents.add(tag);
                mediaList.add(new Pair<>(tag.substring(5, tag.length()-1), RandomStringUtils.randomAlphanumeric(16)));
            }
        }

        //开始写入
        try {
            FileWriter fileWriter = new FileWriter(contentDiary);
            for (Pair<String, String> media : mediaList) {
                fileWriter.write(media.first+":" + media.second + "\n");
            }
            fileWriter.write(";;\n");
            fileWriter.write("location:" + location + "\n$$\n");
            for(String line:contents) {
                fileWriter.write(line + "\n");
            }

            fileWriter.close();
        }
        catch (Exception e){
            e.printStackTrace();
        }


        LocalTask localTask = new LocalTask();
        localTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
                DiaryViewActivity.this, LocalTask.DIARY_SAVE,
                diaryInfo, mediaList, old_diary);

    }

    private void loadDiary(@NonNull DiaryInfo diaryInfo) {
        Objects.requireNonNull(DiaryViewActivity.this.getSupportActionBar()).setTitle(diaryInfo.getDisplayedTitle());
        String title = diaryInfo.getTitle();
        date.setTimeInMillis(diaryInfo.getTimestamp());
        titleEdit.setText(title);
        location = diaryInfo.getContentLocation(DiaryViewActivity.this);

        List<String> diaryContent = diaryInfo.getContentText(DiaryViewActivity.this);
        for(String part:diaryContent) {
            if(part.startsWith("[pic:") && part.endsWith("]")) {
                String mediaPath = getCacheDir().getAbsolutePath() + "/media/" + part.substring(5, part.length()-1);
                ImageView imageView = new ImageView(DiaryViewActivity.this);
                imageView.setImageBitmap(BitmapFactory.decodeFile(mediaPath));
                imageView.setTag(part);
                imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                imageView.setOnLongClickListener(view -> {
                    showMediaMenu(view);
                    return true;
                });
                contentLayout.addView(imageView);
            }
            else if(part.startsWith("[vid:") && part.endsWith("]")) {
                String mediaPath = getCacheDir().getAbsolutePath() + "/media/" + part.substring(5, part.length()-1);
                File media = new File(mediaPath);

                //截取第一帧作为预览
                MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
                metadataRetriever.setDataSource(DiaryViewActivity.this, Uri.fromFile(media));

                ImageView imageView = new ImageView(DiaryViewActivity.this);
                imageView.setTag(part);
                imageView.setImageBitmap(metadataRetriever.getFrameAtTime());
                imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                imageView.setOnLongClickListener(view -> {
                    showMediaMenu(view);
                    return true;
                });
                imageView.setOnClickListener(view -> {
                    Intent intent = new Intent("android.intent.action.VIEW");
                    intent.setDataAndType(FileProvider.getUriForFile(DiaryViewActivity.this,getApplicationContext().getPackageName() + ".provider", media), "video/*");
                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);
                });
                contentLayout.addView(imageView);
            }
            else if(part.startsWith("[rec:") && part.endsWith("]")) {
                ImageView imageView = new ImageView(DiaryViewActivity.this);
                imageView.setBackgroundResource(R.drawable.ic_rec_bg);
                imageView.setClickable(true);
                imageView.setFocusable(true);
                imageView.setTag("[rec:"+part.substring(5, part.length()-1)+"]");
                imageView.setImageResource(R.drawable.ic_play);
                imageView.setPadding(0,0,350,0);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(500, LinearLayout.LayoutParams.WRAP_CONTENT);
                params.setMarginStart(100);
                imageView.setLayoutParams(params);
                imageView.setOnClickListener(view13 -> {
                    if(imageView.getTag().equals("[rec:start]")){
                        imageView.setImageResource(R.drawable.ic_stop);
                        String filename = Calendar.getInstance().getTimeInMillis()+".m4a";
                        imageView.setTag("[rec:recording;" + filename + "]");
                        //开始录音
                        startRecordAudio(getCacheDir().getAbsolutePath()+"/media/"+filename);
                    }
                    else if(((String)imageView.getTag()).startsWith("[rec:recording;")){
                        //停止录音
                        String tag = (String)imageView.getTag();
                        String filename = tag.substring(tag.indexOf(';')+1, tag.length()-1);
                        imageView.setTag("[rec:"+filename+"]");
                        imageView.setImageResource(R.drawable.ic_play);

                        stopRecordAudio();
                    }
                    else{
                        //打开录音
                        String tag = (String)imageView.getTag();
                        String filename = tag.substring(5, tag.length()-1);
                        File file = new File(getCacheDir().getAbsolutePath()+"/media/"+filename);
                        Intent intent = new Intent("android.intent.action.VIEW");
                        intent.setDataAndType(FileProvider.getUriForFile(DiaryViewActivity.this,getApplicationContext().getPackageName() + ".provider", file), "audio/*");
                        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(intent);
                    }
                });
                imageView.setOnLongClickListener(view -> {
                    showMediaMenu(view);
                    return true;
                });
                contentLayout.addView(imageView);

            }
            else {
                EditText editText = new EditText(DiaryViewActivity.this);
                editText.setText(part);
                editText.setWidth(LinearLayout.LayoutParams.MATCH_PARENT);
                editText.setHeight(LinearLayout.LayoutParams.WRAP_CONTENT);
                editText.setGravity(Gravity.TOP);
                editText.setWidth(LinearLayout.LayoutParams.MATCH_PARENT);
                editText.setHeight(LinearLayout.LayoutParams.WRAP_CONTENT);
                editText.setMaxLines(1024);
                editText.setBackground(null);
                contentLayout.addView(editText);
                if(diaryContent.get(diaryContent.size()-1).equals(part)) {
                    lastText = editText;
                }
                else{
                    editText.setOnFocusChangeListener((view, b) -> {
                        if(!b && ((EditText)view).getText().toString().equals("")) {
                            new AlertDialog.Builder(DiaryViewActivity.this)
                                    .setMessage(getResources().getString(R.string.msg_rm_box))
                                    .setPositiveButton(getResources().getString(R.string.apply), (dialogInterface, i) -> contentLayout.removeView(view))
                                    .setNegativeButton(getResources().getString(R.string.cancel), null)
                                    .show();
                        }
                    });
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mLocationClient.stopLocation();
        mLocationClient.onDestroy();
    }
}