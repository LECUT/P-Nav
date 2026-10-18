package com.ecut.pnav.ui.data;

import android.util.Log;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;

/**
 * Utilities for cleaning up old log files.
 * Inspired by GPSTest's IOUtils.deleteOldFiles().
 */
public class FileCleanupUtil {

    private static final String TAG = "FileCleanupUtil";
    private static final int MAX_FILES_STORED = 100;

    /**
     * Deletes old files in the given directory:
     * 1. Remove all empty files (less than 1 KB) except retained files
     * 2. If more than MAX_FILES_STORED remain, delete the oldest (by name sort)
     *
     * @param baseDirectory  base directory in which to clean up files
     * @param fileNotToDelete files that should never be deleted (e.g., current log)
     */
    public static void deleteOldFiles(File baseDirectory, File... fileNotToDelete) {
        if (baseDirectory == null || !baseDirectory.exists() || !baseDirectory.isDirectory()) {
            return;
        }

        try {
            // Step 1: Remove all files smaller than 1 KB (empty/junk), except retained
            FileFilter filter = new FileToDeleteFilter(fileNotToDelete);
            File[] smallFiles = baseDirectory.listFiles(filter);
            if (smallFiles != null) {
                for (File f : smallFiles) {
                    if (f.delete()) {
                        Log.d(TAG, "Deleted small file: " + f.getName());
                    }
                }
            }

            // Step 2: Trim to MAX_FILES_STORED
            File[] allFiles = baseDirectory.listFiles();
            if (allFiles != null && allFiles.length > MAX_FILES_STORED) {
                // Sort by name (alphabetical = chronological for timestamp-based names)
                Arrays.sort(allFiles);
                int filesToDeleteCount = allFiles.length - MAX_FILES_STORED;
                for (int i = 0; i < filesToDeleteCount; i++) {
                    // Skip retained files
                    boolean isRetained = false;
                    for (File retained : fileNotToDelete) {
                        if (allFiles[i].equals(retained)) {
                            isRetained = true;
                            break;
                        }
                    }
                    if (!isRetained) {
                        if (allFiles[i].delete()) {
                            Log.d(TAG, "Deleted old file (count limit): " + allFiles[i].getName());
                        }
                    }
                }
            }

            Log.d(TAG, "File cleanup complete. Directory: " + baseDirectory.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Error during file cleanup", e);
        }
    }
}
