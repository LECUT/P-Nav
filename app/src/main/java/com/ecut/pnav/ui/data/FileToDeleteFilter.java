package com.ecut.pnav.ui.data;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.List;

/**
 * File filter that marks files for deletion if they are not in the retained list
 * AND are smaller than the minimum usable size.
 * Inspired by GPSTest's FileToDeleteFilter.
 */
public class FileToDeleteFilter implements FileFilter {

    private final List<File> retainedFiles;
    private static final int MINIMUM_USABLE_FILE_SIZE_BYTES = 1000; // 1 KB

    public FileToDeleteFilter(File... retainedFiles) {
        this.retainedFiles = Arrays.asList(retainedFiles);
    }

    /**
     * Returns true to delete the file, false to keep it.
     * Deletes files that are NOT in the retained list AND are smaller than 1 KB.
     */
    @Override
    public boolean accept(File pathname) {
        if (pathname == null || !pathname.exists()) return false;
        if (retainedFiles.contains(pathname)) return false;
        return pathname.length() < MINIMUM_USABLE_FILE_SIZE_BYTES;
    }
}
