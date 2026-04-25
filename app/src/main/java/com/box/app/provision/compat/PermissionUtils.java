package com.box.app.provision.compat;

import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Stub replacement for HyperCeiler's PermissionUtils.
 */
public class PermissionUtils {
    public static final String PERMISSION_GET_INSTALLED_APPS = "com.android.permission.GET_INSTALLED_APPS";

    public static boolean hasInstalledAppsPermission(Context context) {
        return context.checkSelfPermission(PERMISSION_GET_INSTALLED_APPS) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isInstalledAppsPermissionGranted(String[] permissions, int[] grantResults) {
        for (int i = 0; i < permissions.length; i++) {
            if (PERMISSION_GET_INSTALLED_APPS.equals(permissions[i])) {
                return grantResults[i] == PackageManager.PERMISSION_GRANTED;
            }
        }
        return false;
    }
}
