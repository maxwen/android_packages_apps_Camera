/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.camera;

import android.graphics.drawable.Drawable;
import android.util.Log;

import com.android.camera.CameraPreference.OnPreferenceChangedListener;
import com.android.camera.drawable.TextDrawable;
import com.android.camera.ui.PieItem;
import com.android.camera.ui.PieItem.OnClickListener;
import com.android.camera.ui.PieRenderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PieController {

    private static String TAG = "CAM_piecontrol";

    protected static final int MODE_PHOTO = 0;
    protected static final int MODE_VIDEO = 1;

    protected CameraActivity mActivity;
    protected PreferenceGroup mPreferenceGroup;
    protected OnPreferenceChangedListener mListener;
    protected PieRenderer mRenderer;
    private List<ListPreference> mPreferences;
    private Map<ListPreference, PieItem> mPreferenceMap;
    private Map<ListPreference, String> mOverrides;

    public void setListener(OnPreferenceChangedListener listener) {
        mListener = listener;
    }

    public PieController(CameraActivity activity, PieRenderer pie) {
        mActivity = activity;
        mRenderer = pie;
        mPreferences = new ArrayList<ListPreference>();
        mPreferenceMap = new HashMap<ListPreference, PieItem>();
        mOverrides = new HashMap<ListPreference, String>();
    }

    public void initialize(PreferenceGroup group) {
        mRenderer.clearItems();
        setPreferenceGroup(group);
    }

    public void onSettingChanged(ListPreference pref) {
        if (mListener != null) {
            mListener.onSharedPreferenceChanged();
        }
    }

    protected void setCameraId(int cameraId) {
        ListPreference pref = mPreferenceGroup.findPreference(CameraSettings.KEY_CAMERA_ID);
        pref.setValue("" + cameraId);
    }

    protected PieItem makeItem(int resId) {
        // We need a mutable version as we change the alpha
        Drawable d = mActivity.getResources().getDrawable(resId).mutate();
        return new PieItem(d, 0);
    }

    protected PieItem makeItem(CharSequence value) {
        TextDrawable drawable = new TextDrawable(mActivity.getResources(), value);
        return new PieItem(drawable, 0);
    }

    public void addPrefItem(PieItem item, String prefKey) {
        final ListPreference pref =
                (ListPreference) mPreferenceGroup.findPreference(prefKey);
        if (pref == null) return;
        mPreferences.add(pref);
        mPreferenceMap.put(pref, item);
    }
    
    public void addItem(String prefKey, float center, float sweep) {
        final ListPreference pref =
                (ListPreference) mPreferenceGroup.findPreference(prefKey);
        if (pref == null) return;
        
        if (!(pref instanceof IconListPreference)){
            return;
        }      
        final IconListPreference iconPref = (IconListPreference)pref;
        
        int[] iconIds = iconPref.getLargeIconIds();
        int resid = -1;
        if (!iconPref.getUseSingleIcon() && iconIds != null) {
            // Each entry has a corresponding icon.
            int index = iconPref.findIndexOfValue(iconPref.getValue());
            resid = iconIds[index];
        } else {
            // The preference only has a single icon to represent it.
            resid = iconPref.getSingleIcon();
        }
        PieItem item = makeItem(resid);
        // use center and sweep to determine layout
        item.setFixedSlice(center, sweep);
        mRenderer.addItem(item);
        mPreferences.add(iconPref);
        mPreferenceMap.put(iconPref, item);
        int nOfEntries = iconPref.getEntries().length;
        if (nOfEntries > 1) {
            for (int i = 0; i < nOfEntries; i++) {
                PieItem inner = null;
                if (iconIds != null) {
                    inner = makeItem(iconIds[i]);
                } else {
                    inner = makeItem(iconPref.getEntries()[i]);
                }
                item.addItem(inner);
                final int index = i;
                inner.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(PieItem item) {
                        iconPref.setValueIndex(index);
                        reloadPreference(iconPref);
                        onSettingChanged(iconPref);
                    }
                });
            }
        }
    }

    public void setPreferenceGroup(PreferenceGroup group) {
        mPreferenceGroup = group;
    }

    public void reloadPreferences() {
        mPreferenceGroup.reloadValue();
        for (ListPreference pref : mPreferenceMap.keySet()) {
            reloadPreference(pref);
        }
    }

    private void reloadPreference(ListPreference pref) {
        if (!(pref instanceof IconListPreference)){
            return;
        }      
        IconListPreference iconPref = (IconListPreference)pref;

        if (iconPref.getUseSingleIcon()) return;
        PieItem item = mPreferenceMap.get(iconPref);
        String overrideValue = mOverrides.get(iconPref);
        int[] iconIds = iconPref.getLargeIconIds();
        if (iconIds != null) {
            // Each entry has a corresponding icon.
            int index;
            if (overrideValue == null) {
                index = iconPref.findIndexOfValue(iconPref.getValue());
            } else {
                index = iconPref.findIndexOfValue(overrideValue);
                if (index == -1) {
                    // Avoid the crash if camera driver has bugs.
                    Log.e(TAG, "Fail to find override value=" + overrideValue);
                    iconPref.print();
                    return;
                }
            }
            item.setImageResource(mActivity, iconIds[index]);
        } else {
            // The preference only has a single icon to represent it.
            item.setImageResource(mActivity, iconPref.getSingleIcon());
        }
    }

    // Scene mode may override other camera settings
    public void overrideSettings(final String ... keyvalues) {
        if (keyvalues.length % 2 != 0) {
            throw new IllegalArgumentException();
        }
        for (ListPreference pref : mPreferenceMap.keySet()) {
            override(pref, keyvalues);
        }
    }

    private void override(ListPreference pref, final String ... keyvalues) {
        mOverrides.remove(pref);
        for (int i = 0; i < keyvalues.length; i += 2) {
            String key = keyvalues[i];
            String value = keyvalues[i + 1];
            if (key.equals(pref.getKey())) {
                mOverrides.put(pref, value);
                PieItem item = mPreferenceMap.get(pref);
                item.setEnabled(value == null);
                break;
            }
        }
        reloadPreference(pref);
    }
}
