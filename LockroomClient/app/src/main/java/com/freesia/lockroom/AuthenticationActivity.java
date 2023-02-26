package com.freesia.lockroom;

import android.annotation.SuppressLint;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.freesia.lockroom.databinding.ActivityAuthenticationBinding;

import org.apache.commons.codec.digest.DigestUtils;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class AuthenticationActivity extends AppCompatActivity {
    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler(Looper.myLooper());
    private View mContentView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar
            if (Build.VERSION.SDK_INT >= 30) {
                mContentView.getWindowInsetsController().hide(
                        WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            } else {
                // Note that some of these constants are new as of API 16 (Jelly Bean)
                // and API 19 (KitKat). It is safe to use them, as they are inlined
                // at compile-time and do nothing on earlier devices.
                mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            }
        }
    };
    private View mControlsView;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            mControlsView.setVisibility(View.VISIBLE);
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = this::hide;

    private boolean isSafe = false;
    private int failNum = 0;
    private ActivityResultLauncher<Intent> keyGuardLauncher;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        com.freesia.lockroom.databinding.ActivityAuthenticationBinding binding = ActivityAuthenticationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mVisible = true;
        mControlsView = binding.fullscreenContentControls;
        mContentView = binding.fullscreenContent;

        // Set up the user interaction to manually show or hide the system UI.
        mContentView.setOnClickListener(view -> toggle());


        keyGuardLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
            if(result.getResultCode()== Activity.RESULT_OK)
            {
                isSafe = true;
                Intent intent = new Intent();
                intent.setClass(this, MainActivity.class);
                startActivity(intent);
                AuthenticationActivity.this.finish();
            }
            else {
                Toast.makeText(AuthenticationActivity.this,
                        getResources().getString(R.string.is_safe),Toast.LENGTH_SHORT).show();
                AuthenticationActivity.this.finish();
            }
        });
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide();
    }

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mControlsView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    private void show() {
        // Show the system bar
        if (Build.VERSION.SDK_INT >= 30) {
            mContentView.getWindowInsetsController().show(
                    WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
        } else {
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in delay milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide() {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, 100);
    }

    private void LocalSafetyCheck() {
        SQLiteDatabase database = new LocalDatabaseHelper(this).getReadableDatabase();
        Cursor cursor = database.query("authentication", null,null,null,null,null,null);
        if(cursor.getCount() == 0) {
            //没设置过密码
            View mView = LayoutInflater.from(AuthenticationActivity.this).inflate(R.layout.content_confirmpassword, null);
            final EditText setPWText = mView.findViewById(R.id.setPasswordText);
            final EditText confirmPWText = mView.findViewById(R.id.confirmPasswordText);
            setPWText.setLines(1);
            setPWText.setTransformationMethod(PasswordTransformationMethod.getInstance());
            confirmPWText.setLines(1);
            confirmPWText.setTransformationMethod(PasswordTransformationMethod.getInstance());

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setCancelable(false)
                    .setTitle(getResources().getString(R.string.safe_set_pw))
                    .setView(mView)
                    .setPositiveButton(getResources().getString(R.string.apply), null)
                    .setNegativeButton(getResources().getString(R.string.cancel), (dialogInterface, i) -> {
                        Toast.makeText(AuthenticationActivity.this, getResources().getString(R.string.tip_set_pw),Toast.LENGTH_SHORT).show();
                        AuthenticationActivity.this.finish();
                    })
                    .create();
            dialog.setOnShowListener(dialogInterface -> {
                Button posBtn = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                posBtn.setOnClickListener(view -> {
                    String setPW = setPWText.getText().toString();
                    String confirmPW = confirmPWText.getText().toString();
                    if(setPW.equals(confirmPW) && setPW.length() > 0) {
                        SQLiteDatabase tmp = new LocalDatabaseHelper(this).getReadableDatabase();
                        ContentValues values = new ContentValues();
                        values.put("MD5", DigestUtils.md5Hex(setPW));
                        long code = tmp.insert("authentication", null, values);

                        if(code == -1) {
                            Toast.makeText(AuthenticationActivity.this, getResources().getString(R.string.pw_fail),Toast.LENGTH_SHORT).show();
                        }
                        else {
                            Toast.makeText(AuthenticationActivity.this, getResources().getString(R.string.pw_succeed),Toast.LENGTH_SHORT).show();
                            isSafe = true;
                            dialog.dismiss();

                            Intent intent = new Intent();
                            intent.setClass(this, MainActivity.class);
                            startActivity(intent);
                            AuthenticationActivity.this.finish();
                        }
                        tmp.close();
                    }
                    else {
                        Toast.makeText(AuthenticationActivity.this, getResources().getString(R.string.check_input),Toast.LENGTH_SHORT).show();
                    }
                });
            });
            dialog.show();
        }
        else {
            //设置过密码
            cursor.moveToFirst();
            int idx = cursor.getColumnIndex("MD5");
            String targetMD5 = cursor.getString(idx);

            FrameLayout frameLayout = new FrameLayout(this);
            FrameLayout.LayoutParams params = new  FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.leftMargin = (int)(getResources().getDisplayMetrics().density * 25 + 0.5f);
            params.rightMargin = (int)(getResources().getDisplayMetrics().density * 25 + 0.5f);
            final EditText editText = new EditText(this);
            editText.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
            editText.setLines(1);
            editText.setHint(getResources().getString(R.string.tip_input_pw));
            editText.setLayoutParams(params);
            frameLayout.addView(editText);

            editText.setTransformationMethod(PasswordTransformationMethod.getInstance());

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setCancelable(false)
                    .setTitle(getResources().getString(R.string.safe_veri))
                    .setView(frameLayout)
                    .setPositiveButton(getResources().getString(R.string.apply), null)
                    .setNegativeButton(getResources().getString(R.string.cancel), (dialogInterface, i) -> {
                        Toast.makeText(AuthenticationActivity.this, getResources().getString(R.string.is_safe),Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .create();
            dialog.setOnShowListener(dialogInterface -> {
                Button posBtn = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                posBtn.setOnClickListener(view -> {
                    if(editText.getText().toString().length() == 0) {
                        Toast.makeText(AuthenticationActivity.this, getResources().getString(R.string.tip_input_pw),Toast.LENGTH_SHORT).show();
                    }
                    else {
                        String inputMD5 = DigestUtils.md5Hex(editText.getText().toString());

                        if(inputMD5.equals(targetMD5)) {
                            failNum = 0;
                            dialog.dismiss();

                            Intent intent = new Intent();
                            intent.setClass(this, MainActivity.class);
                            startActivity(intent);
                            finish();
                        }
                        else {
                            Toast.makeText(AuthenticationActivity.this, getResources().getString(R.string.tip_error_pw),Toast.LENGTH_SHORT).show();
                            failNum++;
                            if(failNum >= 4) {
                                finish();
                            }
                        }
                    }
                });
            });
            dialog.show();
        }
        cursor.close();
        database.close();
    }

    private void SystemSafetyCheck() {
        KeyguardManager keyguardManager = (KeyguardManager)
                getSystemService(Context.KEYGUARD_SERVICE);
        Intent intent = keyguardManager.
                createConfirmDeviceCredentialIntent(
                getResources().getString(R.string.is_safe), null);
        if (intent != null)
        {
            keyGuardLauncher.launch(intent);
        }
        else {
            LocalSafetyCheck();
        }
    }
    private void SafetyCheck(){
        if(!isSafe){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                SystemSafetyCheck();
            }else{
                //不使用系统默认安全识别
                LocalSafetyCheck();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        SafetyCheck();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}