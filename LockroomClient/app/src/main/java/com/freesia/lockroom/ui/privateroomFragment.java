package com.freesia.lockroom.ui;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.Toast;

import com.freesia.lockroom.FileInfo;
import com.freesia.lockroom.FileInfoAdapter;
import com.freesia.lockroom.FileKit;
import com.freesia.lockroom.LocalDatabaseHelper;
import com.freesia.lockroom.LocalTask;
import com.freesia.lockroom.NetworkTask;
import com.freesia.lockroom.R;
import com.freesia.lockroom.ShareInfo;


import java.util.ArrayList;
import java.util.List;


public class privateroomFragment extends Fragment {
    private SwipeRefreshLayout refreshLayout;
    private ListView fileList;
    private List<FileInfo> fileInfoList;
    private ActivityResultLauncher<Intent> openFile;


    public privateroomFragment() {
        // Required empty public constructor
    }

    public void RefreshList() {
        RefreshPage refreshPage = new RefreshPage();
        refreshPage.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void) null);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        openFile = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if(result.getResultCode()== Activity.RESULT_OK) {
                if(result.getData() != null) {
                    Uri uri = result.getData().getData();
                    LocalTask localTask = new LocalTask();
                    localTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, getActivity(), LocalTask.MOVE_IN, uri);
                }
            }
        });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View mView = inflater.inflate(R.layout.fragment_privateroom, container, false);

        refreshLayout = mView.findViewById(R.id.srLayout);
        refreshLayout.setSize(SwipeRefreshLayout.DEFAULT);
        refreshLayout.setColorSchemeColors(Color.BLUE);
        fileList = mView.findViewById(R.id.fileList);
        ImageView mvBtn = mView.findViewById(R.id.mvBtn);

        RefreshList();

        refreshLayout.setOnRefreshListener(this::RefreshList);

        mvBtn.setOnClickListener(this::showModeMenu);

        fileList.setOnItemClickListener((parent, view, position, id) -> {
            FileInfo fileInfo = (FileInfo) parent.getItemAtPosition(position);
            fileInfo.openFile(getActivity());
        });

        fileList.setOnItemLongClickListener((parent, view, position, id) -> {
            FileInfo fileInfo = (FileInfo) parent.getItemAtPosition(position);
            showFileMenu(view, fileInfo);
            return true; //保证不会进行click事件
        });

        return mView;
    }

    private void showFileMenu(View view, FileInfo fileInfo) {
        PopupMenu menu = new PopupMenu(requireContext(),view);
        if (menu.getMenu() instanceof MenuBuilder) {
            //noinspection RestrictedApi
            ((MenuBuilder) menu.getMenu()).setOptionalIconsVisible(true);
        }

        menu.getMenuInflater().inflate(R.menu.file_menu,menu.getMenu());

        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId())
            {
                case R.id.open:
                    fileInfo.openFile(getActivity());
                    break;
                case R.id.move_out:
                    fileInfo.mvoutFile(getActivity());
                    break;
                case R.id.delete:
                    fileInfo.delFile(getActivity());
                    break;
                case R.id.rename:
                    fileInfo.renameFile(getActivity());
                    break;
                case R.id.shareFile:
                    fileInfo.shareFile(getActivity());
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
        View popupView = LayoutInflater.from(getContext()).inflate(R.layout.select_file_menu, null);
        ImageView linkBtn = popupView.findViewById(R.id.linkFileBtn);
        ImageView localBtn = popupView.findViewById(R.id.localFileBtn);
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
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            openFile.launch(intent);
            window.dismiss();
        });


        int[] location = new int[2];
        view.getLocationOnScreen(location);
        window.showAtLocation(view, Gravity.NO_GRAVITY, location[0], location[1]-window.getHeight()-40);
    }

    private class RefreshPage extends AsyncTask<Void, Void, Void> {

        @Nullable
        @Override
        protected Void doInBackground(Void... voids) {
            SQLiteDatabase database = new LocalDatabaseHelper(getActivity()).getReadableDatabase();
            Cursor cursor = database.query("lockedfile", null,null,null,null,null,null);
            int nameIdx = cursor.getColumnIndex("name");
            int timeIdx = cursor.getColumnIndex("time");
            int sizeIdx = cursor.getColumnIndex("size");

            fileInfoList = new ArrayList<>();

            for(cursor.moveToFirst();!cursor.isAfterLast();cursor.moveToNext()){
                fileInfoList.add(new FileInfo(cursor.getString(nameIdx), cursor.getString(timeIdx), cursor.getString(sizeIdx)));
            }
            cursor.close();
            database.close();
            return null;
        }

        @Override
        protected void onPostExecute(Void unused) {
            super.onPostExecute(unused);

            FileInfoAdapter adapter = new FileInfoAdapter(requireActivity(), R.layout.file_item, fileInfoList);
            fileList.setAdapter(adapter);
            refreshLayout.setRefreshing(false);
        }
    }
}