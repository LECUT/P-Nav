package com.ecut.pnav.ui.DGNSS;

import static com.ecut.pnav.MainActivity.if_auto_record;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.ecut.pnav.R;
import com.ecut.pnav.ui.base.LocaleAwareActivity;

public class DGNSSActivity extends LocaleAwareActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dgnssactivity);
        setTitle(R.string.dgnss_title);

        @SuppressLint({"MissingInflatedId", "LocalSuppress"})
        Button record_Button = findViewById(R.id.btn_record3);
        if (if_auto_record) {
            record_Button.setEnabled(false);
        }
        record_Button.setOnClickListener(v -> {
            record_Button.setEnabled(false);
            showCountdownDialog();
            new Handler().postDelayed(() -> {
                if_auto_record = true;
                new Handler().postDelayed(() -> {
                    if_auto_record = false;
                    record_Button.setEnabled(true);
                    Toast.makeText(getApplicationContext(),
                            R.string.record_successfully, Toast.LENGTH_SHORT).show();
                }, 1000);
            }, 5000);
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void showCountdownDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.countdown_title);
        builder.setMessage(getString(R.string.countdown_sec, 5));
        builder.setCancelable(false);

        AlertDialog dialog = builder.create();
        dialog.show();

        new CountDownTimer(5 * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                dialog.setMessage(getString(R.string.countdown_sec, millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                dialog.dismiss();
            }
        }.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }
}
