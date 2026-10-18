package com.ecut.pnav.ui.quality;

import androidx.lifecycle.ViewModel;

/**
 * Quality analysis computation has been moved to native C code (gnss_quality.c).
 * QualityFragment now calls JNI native methods directly.
 * This class is kept as a minimal placeholder for compatibility.
 */
public class QualityViewModel extends ViewModel {
}
