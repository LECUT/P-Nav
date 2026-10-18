package com.ecut.pnav.ui.Spp;

import static com.ecut.pnav.MainActivity.if_auto_record;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.ecut.pnav.R;
import com.ecut.pnav.ui.base.LocaleAwareActivity;

public class SPPActivity extends LocaleAwareActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            EdgeToEdge.enable(this);
            setContentView(R.layout.activity_sppactivity);
            setTitle(R.string.spp_title);

            @SuppressLint({"MissingInflatedId", "LocalSuppress"})
            Button record_Button = findViewById(R.id.btn_record2);
            if (if_auto_record) {
                record_Button.setEnabled(false);
            }
            record_Button.setOnClickListener(v -> {
                record_Button.setEnabled(false);
                if_auto_record = true;
                new Handler().postDelayed(() -> {
                    record_Button.setEnabled(true);
                    Toast.makeText(getApplicationContext(),
                            R.string.record_successfully, Toast.LENGTH_SHORT).show();
                }, 1000);
            });

            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        } catch (Exception e) {
            Log.e("SPPActivity", "Crash in onCreate", e);
            Toast.makeText(this, "SPP Error: " + e.toString(), Toast.LENGTH_LONG).show();
            finish();
        }
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
