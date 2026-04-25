package com.box.app.provision.compat;

/**
 * Stub for fan.appcompat.app.GroupButtonsConfig (excluded with fan.miuix:appcompat).
 */
public class GroupButtonsConfig {
    public static Builder createBuilder() { return new Builder(); }

    public static class Builder {
        public Builder setButton(int index, CharSequence text) { return this; }
        public Builder setButton(int index, CharSequence text, Object icon, Object listener, boolean underline, boolean extra) { return this; }
        public GroupButtonsConfig build() { return new GroupButtonsConfig(); }
    }
}
