package com.box.app.provision;

import com.box.app.provision.IAnimCallback;

interface IProvisionAnim {

    boolean isAnimEnd();

    void playBackAnim(int animY);
    void playNextAnim(int i);

    void registerRemoteCallback(IAnimCallback callback);
    void unregisterRemoteCallback(IAnimCallback callback);
}
