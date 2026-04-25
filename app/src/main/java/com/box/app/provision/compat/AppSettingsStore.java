package com.box.app.provision.compat;

import android.content.Context;

/**
 * Stub replacement for HyperCeiler's AppSettingsStore.
 * BoxApp does not use icon/scope settings from provision.
 */
public class AppSettingsStore {
    public static int getIconIndex(Context ctx) { return 0; }
    public static int getIconModeIndex(Context ctx) { return 0; }
    public static boolean isHideAppIconEnabled(Context ctx) { return false; }
    public static boolean isScopeSyncEnabled(Context ctx) { return false; }
    public static void setHideAppIconEnabled(Context ctx, boolean v) {}
    public static void setScopeSyncEnabled(Context ctx, boolean v) {}
    public static void setIconIndex(Context ctx, int v) {}
    public static void setIconModeIndex(Context ctx, int v) {}
}
