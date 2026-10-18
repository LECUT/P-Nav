package com.ecut.pnav.ui.guide;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ecut.pnav.R;
import com.google.android.material.card.MaterialCardView;

class GuidePagerAdapter extends RecyclerView.Adapter<GuidePagerAdapter.PageViewHolder> {

    private static final int[] LAYOUTS = {
            R.layout.guide_page_welcome,
            R.layout.guide_page_system,
            R.layout.guide_page_done
    };

    private final GuideFragment fragment;
    private boolean gpsChecked = true, bdsChecked, glonassChecked, galileoChecked;

    GuidePagerAdapter(GuideFragment fragment) {
        this.fragment = fragment;
    }

    boolean isGpsChecked() { return gpsChecked; }
    boolean isBdsChecked() { return bdsChecked; }
    boolean isGlonassChecked() { return glonassChecked; }
    boolean isGalileoChecked() { return galileoChecked; }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(LAYOUTS[viewType], parent, false);
        return new PageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        if (position == 1) {
            setupSystemPage(holder.itemView);
        }
    }

    private void setupSystemPage(View view) {
        setupCard(view, R.id.guide_chk_gps, R.id.guide_cb_gps, gpsChecked, v -> {
            gpsChecked = !gpsChecked;
            ((CheckBox) view.findViewById(R.id.guide_cb_gps)).setChecked(gpsChecked);
        });
        setupCard(view, R.id.guide_chk_bds, R.id.guide_cb_bds, bdsChecked, v -> {
            bdsChecked = !bdsChecked;
            ((CheckBox) view.findViewById(R.id.guide_cb_bds)).setChecked(bdsChecked);
        });
        setupCard(view, R.id.guide_chk_glonass, R.id.guide_cb_glonass, glonassChecked, v -> {
            glonassChecked = !glonassChecked;
            ((CheckBox) view.findViewById(R.id.guide_cb_glonass)).setChecked(glonassChecked);
        });
        setupCard(view, R.id.guide_chk_galileo, R.id.guide_cb_galileo, galileoChecked, v -> {
            galileoChecked = !galileoChecked;
            ((CheckBox) view.findViewById(R.id.guide_cb_galileo)).setChecked(galileoChecked);
        });
    }

    private void setupCard(View view, int cardId, int cbId, boolean checked, View.OnClickListener listener) {
        MaterialCardView card = view.findViewById(cardId);
        CheckBox cb = view.findViewById(cbId);
        if (card != null) {
            card.setOnClickListener(v -> {
                if (listener != null) listener.onClick(v);
            });
        }
        if (cb != null) {
            cb.setChecked(checked);
        }
    }

    @Override
    public int getItemCount() { return 3; }

    @Override
    public int getItemViewType(int position) { return position; }

    static class PageViewHolder extends RecyclerView.ViewHolder {
        PageViewHolder(@NonNull View itemView) { super(itemView); }
    }
}
