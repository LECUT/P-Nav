package com.ecut.pnav;

import android.app.AlertDialog;
import android.os.Build;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ecut.pnav.ui.data.FileListAdapter;
import com.ecut.pnav.util.StorageUtil;

import java.io.File;
import java.util.Arrays;

public class RawFilesFragment extends Fragment {

    private static final String TAG = "RawFilesFragment";

    private RecyclerView rvFiles;
    private TextView tvEmpty;
    private FileListAdapter adapter;

    private FileObserver fileObserver;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable debouncedRefresh = this::refreshFileList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_raw_files, container, false);

        TextView btnBack = view.findViewById(R.id.btn_back);
        TextView btnDeleteAll = view.findViewById(R.id.btn_delete_all);
        rvFiles = view.findViewById(R.id.rv_files);
        tvEmpty = view.findViewById(R.id.tv_empty);

        rvFiles.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new FileListAdapter(null, this::refreshFileList);
        rvFiles.setAdapter(adapter);

        btnBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });

        btnDeleteAll.setOnClickListener(v -> deleteAllFiles());

        startFileObserver();
        refreshFileList();

        return view;
    }

    // ==================== File Observer ====================

    private void startFileObserver() {
        File folder = StorageUtil.getDataDir(requireContext());

        final int mask = FileObserver.CREATE | FileObserver.DELETE
                | FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO | FileObserver.MOVED_FROM;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            fileObserver = new FileObserver(folder, mask) {
                @Override
                public void onEvent(int event, String path) {
                    if (path == null) return;
                    if (!isLogFile(path)) return;
                    handler.removeCallbacks(debouncedRefresh);
                    handler.postDelayed(debouncedRefresh, 300);
                }
            };
        } else {
            @SuppressWarnings("deprecation")
            android.os.FileObserver fo = new android.os.FileObserver(folder.getAbsolutePath(), mask) {
                @Override
                public void onEvent(int event, String path) {
                    if (path == null) return;
                    if (!isLogFile(path)) return;
                    handler.removeCallbacks(debouncedRefresh);
                    handler.postDelayed(debouncedRefresh, 300);
                }
            };
            fileObserver = fo;
        }
        fileObserver.startWatching();
        Log.d(TAG, "FileObserver started on " + folder.getAbsolutePath());
    }

    private void stopFileObserver() {
        if (fileObserver != null) {
            fileObserver.stopWatching();
            fileObserver = null;
        }
    }

    // ==================== File List ====================

    private void refreshFileList() {
        File folder = StorageUtil.getDataDir(requireContext());

        File[] files = folder.listFiles(f -> f.isFile() && isLogFile(f.getName()));
        if (files == null || files.length == 0) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvFiles.setVisibility(View.GONE);
            adapter.updateFiles(null);
            return;
        }

        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

        tvEmpty.setVisibility(View.GONE);
        rvFiles.setVisibility(View.VISIBLE);
        adapter.updateFiles(Arrays.asList(files));
    }

    private void deleteAllFiles() {
        File folder = StorageUtil.getDataDir(requireContext());
        File[] files = folder.listFiles(f -> f.isFile() && isLogFile(f.getName()));
        if (files == null || files.length == 0) {
            Toast.makeText(requireContext(), R.string.no_files_recorded, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_all_files)
                .setMessage(getString(R.string.raw_delete_confirm, files.length))
                .setPositiveButton(R.string.yes, (d, w) -> {
                    for (File f : files) f.delete();
                    Toast.makeText(requireContext(), R.string.file_deleted, Toast.LENGTH_SHORT).show();
                    refreshFileList();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static boolean isLogFile(String name) {
        if (name.startsWith("gnss_log_") && name.endsWith(".txt")) return true;
        if (name.startsWith("PNAV") && name.matches(".*\\.\\d{2}[o]$")) return true;
        return false;
    }

    // ==================== Lifecycle ====================

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopFileObserver();
        handler.removeCallbacks(debouncedRefresh);
    }
}
