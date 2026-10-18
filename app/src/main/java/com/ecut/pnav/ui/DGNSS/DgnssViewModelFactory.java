package com.ecut.pnav.ui.DGNSS;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class DgnssViewModelFactory implements ViewModelProvider.Factory {
    private final Context context;

    public DgnssViewModelFactory(Context context) {
        this.context = context;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(DgnssViewModel.class)) {
            return (T) new DgnssViewModel(context);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
