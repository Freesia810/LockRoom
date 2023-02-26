package com.freesia.lockroom.ui;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.os.Handler;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.Toast;

import com.freesia.lockroom.DiaryInfo;
import com.freesia.lockroom.DiaryInfoAdapter;
import com.freesia.lockroom.DiaryViewActivity;
import com.freesia.lockroom.FileInfo;
import com.freesia.lockroom.FileInfoAdapter;
import com.freesia.lockroom.FileKit;
import com.freesia.lockroom.LocalDatabaseHelper;
import com.freesia.lockroom.LocalTask;
import com.freesia.lockroom.MainActivity;
import com.freesia.lockroom.NetworkTask;
import com.freesia.lockroom.R;
import com.freesia.lockroom.ShareInfo;

import java.util.ArrayList;
import java.util.List;

public class mydiaryFragment extends Fragment {
    private SwipeRefreshLayout refreshLayout;
    private ListView diaryList;
    private List<DiaryInfo> diaryInfoList;


    public mydiaryFragment() {
        // Required empty public constructor
    }

    public void RefreshList() {
        RefreshPage refreshPage = new RefreshPage();
        refreshPage.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,null);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View mView = inflater.inflate(R.layout.fragment_mydiary, container, false);
        refreshLayout = mView.findViewById(R.id.diaryRefreshLayout);
        diaryList = mView.findViewById(R.id.diaryList);
        ImageView addDiaryBtn = mView.findViewById(R.id.addDiaryBtn);

        RefreshList();
        refreshLayout.setOnRefreshListener(this::RefreshList);

        diaryList.setOnItemClickListener((parent, view, position, id) -> {
            DiaryInfo diaryInfo= (DiaryInfo) parent.getItemAtPosition(position);
            diaryInfo.openDiary(getActivity());
        });

        diaryList.setOnItemLongClickListener((parent, view, position, id) -> {
            DiaryInfo diaryInfo= (DiaryInfo) parent.getItemAtPosition(position);
            showDiaryMenu(view, diaryInfo);
            return true;
        });

        addDiaryBtn.setOnClickListener(this::showModeMenu);

        return mView;
    }

    @Override
    public void onResume() {
        super.onResume();
        RefreshList();
    }

    private void showDiaryMenu(View view, DiaryInfo diaryInfo) {
        PopupMenu menu = new PopupMenu(requireContext(),view);
        if (menu.getMenu() instanceof MenuBuilder) {
            //noinspection RestrictedApi
            ((MenuBuilder) menu.getMenu()).setOptionalIconsVisible(true);
        }

        menu.getMenuInflater().inflate(R.menu.diary_menu,menu.getMenu());

        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId())
            {
                case R.id.openDiary:
                    diaryInfo.openDiary(getActivity());
                    break;
                case R.id.deleteDiary:
                    diaryInfo.deleteDiary(getActivity());
                    break;
                case R.id.shareDiary:
                    diaryInfo.shareDiary(getActivity());
                    break;
                default:
                    break;
            }
            return false;
        });
        menu.setGravity(Gravity.END);

        menu.show();
    }


    private void showModeMenu(@NonNull View view){
        View popupView = LayoutInflater.from(getContext()).inflate(R.layout.select_diary_menu, null);
        ImageView linkBtn = popupView.findViewById(R.id.linkDiaryBtn);
        ImageView localBtn = popupView.findViewById(R.id.localDiaryBtn);
        PopupWindow window = new PopupWindow(popupView, 200, 450);
        window.setOutsideTouchable(true);

        linkBtn.setOnClickListener(view12 -> {
            if (!FileKit.checkConnectNetwork(requireActivity())) {
                new Handler(requireActivity().getMainLooper()).post(() -> Toast.makeText(getActivity(), "请检查连接网络", Toast.LENGTH_LONG).show());
            }
            else{
                EditText linkText = new EditText(getContext());
                AlertDialog dialog = new AlertDialog.Builder(requireContext())
                        .setTitle("输入分享链接")
                        .setView(linkText)
                        .setPositiveButton("确定", (dialogInterface, i) -> {
                            String md5 = linkText.getText().toString();

                            if(md5.matches("^[A-Za-z0-9]+$") && md5.length() == 32){
                                NetworkTask task = new NetworkTask();
                                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, getActivity(), NetworkTask.DOWNLOAD_CLOUD, md5);
                            }
                            else{
                                Toast.makeText(getActivity(), "请检查输入", Toast.LENGTH_LONG).show();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .create();
                dialog.show();
            }
            window.dismiss();
        });

        localBtn.setOnClickListener(view1 -> {
            Intent openDiary = new Intent();
            openDiary.setClass(getActivity(), DiaryViewActivity.class);
            startActivity(openDiary);
            window.dismiss();
        });


        int[] location = new int[2];
        view.getLocationOnScreen(location);
        window.showAtLocation(view, Gravity.NO_GRAVITY, location[0], location[1]-window.getHeight()-40);
    }


    private class RefreshPage extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            SQLiteDatabase database = new LocalDatabaseHelper(getActivity()).getReadableDatabase();
            Cursor cursor = database.query("diary", null,null,null,null,null,null);
            int tsIdx = cursor.getColumnIndex("timestamp");
            int titleIdx = cursor.getColumnIndex("title");
            int aesIdx = cursor.getColumnIndex("contentAES");

            diaryInfoList = new ArrayList<>();

            for(cursor.moveToFirst();!cursor.isAfterLast();cursor.moveToNext()){
                diaryInfoList.add(new DiaryInfo(cursor.getLong(tsIdx), cursor.getString(titleIdx), cursor.getString(aesIdx)));
            }
            cursor.close();
            database.close();
            return null;
        }

        @Override
        protected void onPostExecute(Void unused) {
            super.onPostExecute(unused);

            DiaryInfoAdapter adapter = new DiaryInfoAdapter(getActivity(), R.layout.diary_item, diaryInfoList);
            diaryList.setAdapter(adapter);
            refreshLayout.setRefreshing(false);
        }
    }
}