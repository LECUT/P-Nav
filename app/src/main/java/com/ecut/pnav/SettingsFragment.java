package com.ecut.pnav;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.ecut.pnav.ui.Ntrip.NtripFragment;
import com.ecut.pnav.ui.Sol.Solragment;
import com.ecut.pnav.ui.data.ExportDialogUtil;
import com.ecut.pnav.util.LocaleHelper;
import com.google.android.material.card.MaterialCardView;

public class SettingsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        MaterialCardView cardNtrip = view.findViewById(R.id.card_ntrip);
        MaterialCardView cardSolution = view.findViewById(R.id.card_solution);
        MaterialCardView cardLanguage = view.findViewById(R.id.card_language);
        MaterialCardView cardEphemeris = view.findViewById(R.id.card_ephemeris);
        MaterialCardView cardRawFiles = view.findViewById(R.id.card_raw_files);
        MaterialCardView cardDatabase = view.findViewById(R.id.card_database);
        TextView cardDatabaseDesc = view.findViewById(R.id.card_database_desc);
        // 动态显示位置数据/文件记录条数
        try {
            positiondatabase dbHelper = new positiondatabase(requireContext());
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            int posCount = 0, fileCount = 0;
            Cursor c1 = db.rawQuery("SELECT COUNT(*) FROM " + positiondatabase.TABLE_NAME, null);
            if (c1 != null && c1.moveToFirst()) posCount = c1.getInt(0);
            if (c1 != null) c1.close();
            Cursor c2 = db.rawQuery("SELECT COUNT(*) FROM " + positiondatabase.TABLE_FILE_RECORDS, null);
            if (c2 != null && c2.moveToFirst()) fileCount = c2.getInt(0);
            if (c2 != null) c2.close();
            db.close();
            if (cardDatabaseDesc != null) {
                cardDatabaseDesc.setText(getString(R.string.db_summary, posCount, fileCount));
            }
        } catch (Exception ignored) {}

        // NTRIP/CORS settings
        cardNtrip.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new NtripFragment())
                        .addToBackStack("ntrip")
                        .commit();
            }
        });

        // 星历下载对话框 — 自定义布局：文件列表 + 勾选删除 + 下载按钮
        cardEphemeris.setOnClickListener(v -> showEphemerisDialog());

        // 解算设置 - 打开 Solragment (原来的设置页)
        cardSolution.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new Solragment())
                        .addToBackStack("solution")
                        .commit();
            }
        });

        // 语言切换
        cardLanguage.setOnClickListener(v -> {
            String[] languages = {getString(R.string.lang_en), getString(R.string.lang_zh)};
            String[] codes = {"en", "zh"};
            String current = LocaleHelper.getPersistedLanguage(requireContext());
            int checkedItem = 0;
            for (int i = 0; i < codes.length; i++) {
                if (codes[i].equals(current)) checkedItem = i;
            }
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.language)
                    .setSingleChoiceItems(languages, checkedItem, (dialog, which) -> {
                        String newLang = codes[which];
                        if (!newLang.equals(current)) {
                            LocaleHelper.persistLanguage(requireContext(), newLang);
                            if (getActivity() != null) {
                                getActivity().recreate();
                            }
                        }
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });

        // 原始观测文件
        cardRawFiles.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new RawFilesFragment())
                        .addToBackStack("raw_files")
                        .commit();
            }
        });

        // 位置数据库 — 查看 / 导出 / 清空
        cardDatabase.setOnClickListener(v -> {
            // 分组菜单：位置数据 / 输出文件
            LinearLayout content = new LinearLayout(requireContext());
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(20), dp(8), dp(20), dp(8));

            content.addView(buildMenuGroupTitle(getString(R.string.db_group_pos)));
            content.addView(buildMenuAction(getString(R.string.db_view_pos), () -> showPositionData()));
            content.addView(buildMenuAction(getString(R.string.db_export_track), () -> ExportDialogUtil.showExportDialog(requireActivity())));
            content.addView(buildMenuAction(getString(R.string.db_clear_pos), () -> confirmClearDatabase()));

            content.addView(buildMenuGroupTitle(getString(R.string.db_group_files)));
            content.addView(buildMenuAction(getString(R.string.db_view_files), () -> showFileRecords()));
            content.addView(buildMenuAction(getString(R.string.db_clear_files), () -> confirmClearFileRecords()));

            mMenuDialog = new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.settings_database_title)
                    .setView(content)
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });

        return view;
    }

    private AlertDialog mMenuDialog;

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 分组标题 */
    private TextView buildMenuGroupTitle(String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tv.setTextColor(requireContext().getColor(R.color.primary));
        tv.setPadding(0, dp(12), 0, dp(4));
        return tv;
    }

    /** 菜单项（可点击行） */
    private TextView buildMenuAction(String text, Runnable action) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTextColor(requireContext().getColor(R.color.onSurface));
        tv.setPadding(dp(4), dp(10), dp(4), dp(10));
        tv.setClickable(true);
        tv.setFocusable(true);
        tv.setOnClickListener(a -> {
            if (mMenuDialog != null) mMenuDialog.dismiss();
            action.run();
        });
        return tv;
    }

    /** 弹窗显示数据库最近 50 条记录 — 每行紧凑，标题加粗 */
    private void showPositionData() {
        positiondatabase dbHelper = new positiondatabase(requireContext());
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(positiondatabase.TABLE_NAME, null, null, null, null, null,
                positiondatabase.COLUMN_ID + " DESC", "50");

        // 标题行（加粗、主题色）
        TextView header = new TextView(requireContext());
        header.setText(getString(R.string.db_header));
        header.setTextSize(11);
        header.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        header.setTextColor(requireContext().getColor(R.color.primary));
        header.setPadding(dp(20), dp(12), dp(20), dp(4));

        // 数据行
        StringBuilder sb = new StringBuilder();
        if (cursor != null && cursor.moveToFirst()) {
            int idxTime = cursor.getColumnIndex(positiondatabase.COLUMN_TIME);
            int idxLat  = cursor.getColumnIndex(positiondatabase.COLUMN_LAT);
            int idxLon  = cursor.getColumnIndex(positiondatabase.COLUMN_LON);
            int idxAlt  = cursor.getColumnIndex(positiondatabase.COLUMN_ALTITUDE);
            int idxState= cursor.getColumnIndex(positiondatabase.COLUMN_STATE);
            do {
                String time = cursor.getString(idxTime);
                String state = cursor.getString(idxState);
                double lat = dmsToDeg(cursor.getString(idxLat));
                double lon = dmsToDeg(cursor.getString(idxLon));
                String alt = cursor.getString(idxAlt);
                String shortTime = time != null && time.length() > 5 ? time.substring(5) : time;
                // 单行紧凑格式，避免定宽列对齐问题
                sb.append(shortTime).append("  ").append(state)
                  .append("  ").append(String.format("%.6f, %.6f", lat, lon))
                  .append("  H").append(alt).append("\n");
            } while (cursor.moveToNext());
            cursor.close();
        } else {
            sb.append(getString(R.string.no_data_found));
        }
        db.close();

        TextView tv = new TextView(requireContext());
        tv.setText(sb.toString());
        tv.setTextSize(11);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setTextColor(requireContext().getColor(R.color.onSurface));
        tv.setPadding(dp(20), 0, dp(20), dp(8));
        tv.setTextIsSelectable(true);

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(header);
        content.addView(tv);

        ScrollView scroll = new ScrollView(requireContext());
        scroll.addView(content);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.checkposdata)
                .setView(scroll)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    /** 度分秒字符串(如 "35, 58, 30") → 十进制度 */
    private static double dmsToDeg(String dms) {
        if (dms == null || dms.isEmpty()) return 0;
        String[] parts = dms.split(",");
        double deg = 0, min = 0, sec = 0;
        try {
            if (parts.length > 0) deg = Double.parseDouble(parts[0].trim());
            if (parts.length > 1) min = Double.parseDouble(parts[1].trim());
            if (parts.length > 2) sec = Double.parseDouble(parts[2].trim());
        } catch (NumberFormatException ignored) { return 0; }
        return deg + min / 60.0 + sec / 3600.0;
    }

    /** 确认清空数据库 */
    private void confirmClearDatabase() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.clean_posdata)
                .setMessage(getString(R.string.db_clear_confirm))
                .setPositiveButton(R.string.confirm, (d, w) -> {
                    positiondatabase dbHelper = new positiondatabase(requireContext());
                    SQLiteDatabase db = dbHelper.getWritableDatabase();
                    int count = db.delete(positiondatabase.TABLE_NAME, null, null);
                    db.close();
                    Toast.makeText(requireContext(),
                            getString(R.string.db_deleted, count), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** 弹窗显示输出文件记录（可点击查看内容/分享） */
    @SuppressLint("Range")
    private void showFileRecords() {
        positiondatabase dbHelper = new positiondatabase(requireContext());
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = positiondatabase.queryFileRecords(db);

        if (cursor == null || !cursor.moveToFirst()) {
            if (cursor != null) cursor.close();
            db.close();
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.file_records_title)
                    .setMessage(R.string.no_file_records)
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return;
        }

        // 收集文件路径和显示文本
        java.util.List<String> labels = new java.util.ArrayList<>();
        java.util.List<String> paths = new java.util.ArrayList<>();
        do {
            String filePath = cursor.getString(cursor.getColumnIndex(positiondatabase.COL_FILE_PATH));
            String fileName = cursor.getString(cursor.getColumnIndex(positiondatabase.COL_FILE_NAME));
            String fileType = cursor.getString(cursor.getColumnIndex(positiondatabase.COL_FILE_TYPE));
            String fileMode = cursor.getString(cursor.getColumnIndex(positiondatabase.COL_FILE_MODE));
            String createdAt = cursor.getString(cursor.getColumnIndex(positiondatabase.COL_FILE_CREATED));

            // 单行文件项：📄 文件名（类型 · 模式 · 创建时间）
            String type = fileType == null || fileType.isEmpty() ? getString(R.string.db_unknown) : fileType;
            String mode = fileMode == null || fileMode.isEmpty() ? getString(R.string.db_unknown) : fileMode;
            String created = createdAt == null ? "" : createdAt;
            String label = "📄 " + fileName + "  (" + type + " · " + mode + " · " + created + ")";
            labels.add(label);
            paths.add(filePath);
        } while (cursor.moveToNext());
        cursor.close();
        db.close();

        String[] items = labels.toArray(new String[0]);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.file_records_title)
                .setItems(items, (dialog, which) -> {
                    String path = paths.get(which);
                    showFileActionDialog(path);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** 文件操作子菜单：查看内容 / 分享 */
    private void showFileActionDialog(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            Toast.makeText(requireContext(), getString(R.string.db_file_not_found, filePath), Toast.LENGTH_SHORT).show();
            return;
        }

        String[] actions = {getString(R.string.db_view_content), getString(R.string.db_share)};
        new AlertDialog.Builder(requireContext())
                .setTitle(file.getName())
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) showFileContent(file);
                    else shareFile(file);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** 弹窗显示文本文件内容（前 200 行） */
    private void showFileContent(File file) {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null && lineCount < 200) {
                content.append(line).append("\n");
                lineCount++;
            }
            if (lineCount >= 200) {
                content.append("\n").append(getString(R.string.db_file_preview_limit));
            }
        } catch (IOException e) {
            content.append(getString(R.string.db_file_read_fail, e.getMessage()));
        }

        ScrollView scroll = new ScrollView(requireContext());
        TextView tv = new TextView(requireContext());
        tv.setText(content.toString());
        tv.setPadding(24, 16, 24, 16);
        tv.setTextSize(10);
        tv.setFontFeatureSettings("monospace");
        tv.setTextIsSelectable(true);
        scroll.addView(tv);

        new AlertDialog.Builder(requireContext())
                .setTitle(file.getName())
                .setView(scroll)
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(getString(R.string.db_file_share), (d, w) -> shareFile(file))
                .show();
    }

    /** 通过 FileProvider 分享文件 */
    private void shareFile(File file) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        String name = file.getName().toLowerCase();
        if (name.endsWith(".pos")) shareIntent.setType("text/plain");
        else if (name.endsWith(".obs") || name.endsWith(".nav")) shareIntent.setType("text/plain");
        else if (name.endsWith(".txt")) shareIntent.setType("text/plain");
        else shareIntent.setType("*/*");

        shareIntent.putExtra(Intent.EXTRA_STREAM,
                FileProvider.getUriForFile(requireContext(),
                        requireContext().getPackageName() + ".fileprovider", file));
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent,
                getString(R.string.select_file_manager)));
    }

    /** 确认清空文件记录 */
    private void confirmClearFileRecords() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.clean_file_records)
                .setMessage(R.string.clean_file_records_confirm)
                .setPositiveButton(R.string.confirm, (d, w) -> {
                    positiondatabase dbHelper = new positiondatabase(requireContext());
                    dbHelper.clearFileRecords();
                    Toast.makeText(requireContext(),
                            R.string.file_records_cleared, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ★ 将星历技术文件名映射为人类可读标签
    private String describeEphFile(String name) {
        if (name == null) return "未知文件";
        String lower = name.toLowerCase();
        if (lower.startsWith("brdm")) return "广播星历(多系统)";
        if (lower.startsWith("brdc")) {
            char sys = lower.charAt(lower.length() - 1);
            if (sys == 'n') return "广播星历 GPS";
            if (sys == 'g') return "广播星历 GLONASS";
            if (sys == 'c') return "广播星历 BDS";
            if (sys == 'l') return "广播星历 Galileo";
            return "广播星历";
        }
        if (lower.endsWith(".sp3")) return "精密星历 SP3";
        if (lower.endsWith(".clk")) return "钟差 CLK";
        if (lower.endsWith(".rnx")) return "广播星历(多系统)";
        return name;
    }

    // ★ 星历文件排序权重（广播 → 精密 → 钟差）
    private int ephSortOrder(String name) {
        String d = describeEphFile(name);
        if (d.startsWith("广播星历 GPS")) return 0;
        if (d.startsWith("广播星历 GLONASS")) return 1;
        if (d.startsWith("广播星历 BDS")) return 2;
        if (d.startsWith("广播星历 Galileo")) return 3;
        if (d.startsWith("广播星历")) return 4;
        if (d.startsWith("精密星历")) return 5;
        if (d.startsWith("钟差")) return 6;
        return 7;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /** 星历模式选择（自动/广播/NRT精密）— 更新 MainActivity.ephForceMode */
    private void bindEphModeChips(View root) {
        com.google.android.material.chip.Chip chipAuto = root.findViewById(R.id.eph_chip_auto);
        com.google.android.material.chip.Chip chipBrdc = root.findViewById(R.id.eph_chip_brdc);
        com.google.android.material.chip.Chip chipNrt = root.findViewById(R.id.eph_chip_nrt);
        if (chipAuto == null || chipBrdc == null || chipNrt == null) return;

        java.util.function.Consumer<Integer> update = mode -> {
            com.ecut.pnav.MainActivity.ephForceMode = mode;
            chipAuto.setChecked(mode == 0);
            chipBrdc.setChecked(mode == 1);
            chipNrt.setChecked(mode == 2);
        };
        update.accept(com.ecut.pnav.MainActivity.ephForceMode);

        chipAuto.setOnClickListener(v -> update.accept(0));
        chipBrdc.setOnClickListener(v -> update.accept(1));
        chipNrt.setOnClickListener(v -> update.accept(2));
    }

    private void showEphemerisDialog() {
        File ephDir = new File(requireContext().getExternalFilesDir(null), "myFolder");
        if (!ephDir.exists()) ephDir.mkdirs();
        File[] files = ephDir.listFiles();
        if (files == null) files = new File[0];
        java.util.Arrays.sort(files, (a, b) -> {
            int cmp = Integer.compare(ephSortOrder(a.getName()), ephSortOrder(b.getName()));
            return cmp != 0 ? cmp : a.getName().compareToIgnoreCase(b.getName());
        });

        View root = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_ephemeris, null);

        // ★ 星历模式选择（自动/广播/NRT精密）— 从解算配置 Tab 移入
        bindEphModeChips(root);

        LinearLayout fileList = root.findViewById(R.id.eph_file_list);
        TextView emptyHint = root.findViewById(R.id.eph_empty_hint);
        TextView btnSelectAll = root.findViewById(R.id.eph_btn_select_all);
        View btnDelete = root.findViewById(R.id.eph_btn_delete);

        final boolean[] selectAllFlag = {true};
        final java.util.List<CheckBox> checkBoxes = new java.util.ArrayList<>();
        final File[] fileRef = files;

        // 填充文件列表
        if (files.length == 0) {
            emptyHint.setVisibility(View.VISIBLE);
            btnSelectAll.setVisibility(View.GONE);
            btnDelete.setVisibility(View.GONE);
        } else {
            emptyHint.setVisibility(View.GONE);
            for (File f : files) {
                CheckBox cb = new CheckBox(requireContext());
                cb.setText(describeEphFile(f.getName()) + "  " + f.getName() + "  (" + formatSize(f.length()) + ")");
                cb.setTextSize(13);
                cb.setPadding(0, 4, 0, 4);
                cb.setChecked(true);
                checkBoxes.add(cb);
                fileList.addView(cb);
            }
        }

        // 全选/取消全选
        btnSelectAll.setOnClickListener(v -> {
            selectAllFlag[0] = !selectAllFlag[0];
            btnSelectAll.setText(selectAllFlag[0]
                    ? getString(R.string.eph_select_all)
                    : getString(R.string.eph_deselect_all));
            for (CheckBox cb : checkBoxes) cb.setChecked(selectAllFlag[0]);
        });

        // 删除选中
        btnDelete.setOnClickListener(v -> {
            int del = 0;
            for (CheckBox cb : checkBoxes) {
                if (cb.isChecked()) {
                    int idx = checkBoxes.indexOf(cb);
                    if (idx >= 0 && idx < fileRef.length && fileRef[idx].delete()) del++;
                }
            }
            Toast.makeText(requireContext(),
                    getString(R.string.eph_deleted_count, del), Toast.LENGTH_SHORT).show();
            // 关闭并重新打开对话框以刷新文件列表
        });

        // 下载按钮（广播 / 精密SP3，已移除 CLK 钟差下载）
        View.OnClickListener downloadListener = v -> {
            if (!(getActivity() instanceof MainActivity)) return;
            int opt;
            int id = v.getId();
            if (id == R.id.eph_btn_brdc) opt = 1;
            else opt = 2;
            ((MainActivity) getActivity()).downloadEphemeris(opt);
        };
        root.findViewById(R.id.eph_btn_brdc).setOnClickListener(downloadListener);
        root.findViewById(R.id.eph_btn_sp3).setOnClickListener(downloadListener);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_eph_title)
                .setView(root)
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmClearEphemeris(File ephDir) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.clear_files)
                .setMessage(R.string.eph_delete_confirm)
                .setPositiveButton(R.string.confirm, (d, w) -> {
                    if (ephDir != null && ephDir.exists()) {
                        File[] files = ephDir.listFiles();
                        int deleted = 0;
                        if (files != null) {
                            for (File f : files) {
                                if (f.delete()) deleted++;
                            }
                        }
                        Toast.makeText(requireContext(),
                                getString(R.string.db_files_deleted, deleted), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
