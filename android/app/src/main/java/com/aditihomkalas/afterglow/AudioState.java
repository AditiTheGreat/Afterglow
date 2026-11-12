package com.aditihomkalas.afterglow;

public final class AudioState {
    public static volatile String status = "Ready";
    public static volatile float level = 0f;
    public static volatile float peak = 0f;
    public static volatile boolean active = false;
    public static volatile float[] frequencies = new float[128];

    private AudioState() {}
}
