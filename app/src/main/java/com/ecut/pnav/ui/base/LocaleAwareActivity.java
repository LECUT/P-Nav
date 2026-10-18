package com.ecut.pnav.ui.base;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.ecut.pnav.util.LocaleHelper;

public abstract class LocaleAwareActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(@NonNull Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }
}
