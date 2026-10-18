package com.ecut.pnav.ui.PPP;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class PppViewModelFactory implements ViewModelProvider.Factory {
    private final Context context;

    public PppViewModelFactory(Context context) {
        this.context = context;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(PppViewModel.class)) {
            return (T) new PppViewModel(context);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
