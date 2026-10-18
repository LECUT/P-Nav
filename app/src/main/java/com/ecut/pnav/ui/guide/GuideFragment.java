package com.ecut.pnav.ui.guide;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.ecut.pnav.R;

/** 首次使用引导向导 — 3 页覆盖层 */
public class GuideFragment extends Fragment {

    private static final String PREFS_NAME = "gnss_prefs";
    private static final String PREF_GPS = "pref_gps";
    private static final String PREF_BDS = "pref_bds";
    private static final String PREF_GLONASS = "pref_glonass";
    private static final String PREF_GALILEO = "pref_galileo";

    private ViewPager2 viewPager;
    private TextView btnSkip, btnNext;
    private View dot0, dot1, dot2;
    private int currentPage = 0;
    private GuidePagerAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_guide, container, false);

        viewPager = root.findViewById(R.id.guide_view_pager);
        btnSkip = root.findViewById(R.id.guide_btn_skip);
        btnNext = root.findViewById(R.id.guide_btn_next);
        dot0 = root.findViewById(R.id.dot0);
        dot1 = root.findViewById(R.id.dot1);
        dot2 = root.findViewById(R.id.dot2);

        adapter = new GuidePagerAdapter(this);
        viewPager.setAdapter(adapter);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPage = position;
                updateDots();
                if (position == 2) {
                    btnNext.setText(R.string.guide_btn_start);
                    btnSkip.setVisibility(View.GONE);
                } else {
                    btnNext.setText(R.string.guide_btn_next);
                    btnSkip.setVisibility(View.VISIBLE);
                }
            }
        });

        btnSkip.setOnClickListener(v -> finishGuide());
        btnNext.setOnClickListener(v -> {
            if (currentPage < 2) {
                viewPager.setCurrentItem(currentPage + 1, true);
            } else {
                saveSystemSelection();
                finishGuide();
            }
        });

        updateDots();
        return root;
    }

    private void updateDots() {
        View[] dots = {dot0, dot1, dot2};
        for (int i = 0; i < dots.length; i++) {
            dots[i].setBackgroundResource(
                    i == currentPage ? R.drawable.guide_dot_active : R.drawable.guide_dot_inactive);
        }
    }

    private void saveSystemSelection() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(PREF_GPS, adapter.isGpsChecked());
        editor.putBoolean(PREF_BDS, adapter.isBdsChecked());
        editor.putBoolean(PREF_GLONASS, adapter.isGlonassChecked());
        editor.putBoolean(PREF_GALILEO, adapter.isGalileoChecked());
        editor.putBoolean("pref_guided", true);
        editor.apply();
    }

    private void finishGuide() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean("pref_guided", true).apply();

        if (getActivity() != null) {
            getActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .remove(this)
                    .commitAllowingStateLoss();
        }
    }
}
