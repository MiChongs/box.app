package com.box.app.provision.fragment;

import static com.box.app.provision.utils.NetworkManager.isInternetAvailable;
import static com.box.app.provision.utils.NetworkManager.isNetworkConnected;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;

import com.box.app.R;
import com.box.app.provision.base.OobeUtils;
import com.box.app.provision.widget.PermissionItemView;
import com.topjohnwu.superuser.Shell;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PermissionSettingsFragment extends BaseFragment {
    private static final int REQUEST_NOTIFICATION = 1201;

    private View mNextView;

    PermissionItemView mRootPermissionItem;
    PermissionItemView mNetworkPermissionItem;
    PermissionItemView mNotificationPermissionItem;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private long lastNetworkCheck = 0;

    @Override
    protected int getLayoutId() {
        return R.layout.provision_permission_layout;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mRootPermissionItem = view.findViewById(R.id.root);
        mNetworkPermissionItem = view.findViewById(R.id.network);
        mNotificationPermissionItem = view.findViewById(R.id.notification);

        mRootPermissionItem.setItemTitle(R.string.provision_permission_root);
        mNetworkPermissionItem.setItemTitle(R.string.provision_permission_internet);
        mNotificationPermissionItem.setItemTitle(R.string.provision_permission_notification);

        mNetworkPermissionItem.setEnabled(false);
        mRootPermissionItem.setEnabled(false);
        mNotificationPermissionItem.setOnClickListener(v -> requestNotificationPermission());

        checkNetwork();
        checkRooted();
        checkNotification();
        registerNetworkCallback();
    }

    private void checkRooted() {
        executor.execute(() -> {
            if (!isAdded()) return;
            Boolean granted = Shell.isAppGrantedRoot();
            boolean rooted = granted != null && granted;
            requireActivity().runOnUiThread(() -> {
                if (mRootPermissionItem != null) {
                    mRootPermissionItem.setChecked(rooted);
                }
            });
        });
    }

    private void checkNotification() {
        if (!isAdded() || mNotificationPermissionItem == null) return;
        boolean enabled = NotificationManagerCompat.from(requireContext()).areNotificationsEnabled();
        mNotificationPermissionItem.setChecked(enabled);
    }

    private void requestNotificationPermission() {
        if (!isAdded()) return;
        if (NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()) {
            checkNotification();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_NOTIFICATION
            );
        }
    }

    private void checkNetwork() {
        executor.execute(() -> {
            boolean connected = isNetworkConnected(requireContext());
            boolean internet = connected && isInternetAvailable();

            if (!isAdded()) return;

            requireActivity().runOnUiThread(() -> {
                mNetworkPermissionItem.setChecked(internet);
                setAllowNext(internet);
            });
        });
    }

    private void updateNetworkState(boolean state) {
        if (!isAdded()) return;

        requireActivity().runOnUiThread(() -> {
            mNetworkPermissionItem.setChecked(state);
            setAllowNext(state);
        });
    }

    private void setAllowNext(boolean allowNext) {
        if (!isAdded()) return;

        requireActivity().runOnUiThread(() -> {
            mNextView = OobeUtils.getNextView(getActivity());
            mNextView.setEnabled(allowNext);
            mNextView.setAlpha(allowNext ? OobeUtils.NO_ALPHA : OobeUtils.HALF_ALPHA);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        checkNetwork();
        checkRooted();
        checkNotification();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unregisterNetworkCallback();
        executor.shutdownNow();
    }

    private void registerNetworkCallback() {
        connectivityManager = (ConnectivityManager)
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) return;

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                checkNetworkDebounced();
            }

            @Override
            public void onLost(@NonNull Network network) {
                updateNetworkState(false);
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities caps) {
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    updateNetworkState(false);
                } else {
                    checkNetworkDebounced();
                }
            }
        };

        NetworkRequest request = new NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build();

        connectivityManager.registerNetworkCallback(request, networkCallback);
    }

    private void unregisterNetworkCallback() {
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {}
        }
    }

    private void checkNetworkDebounced() {
        long now = System.currentTimeMillis();
        if (now - lastNetworkCheck < 1500) return;
        lastNetworkCheck = now;
        checkNetwork();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATION) {
            checkNotification();
        }
    }
}
