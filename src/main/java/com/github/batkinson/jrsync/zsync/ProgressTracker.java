package com.github.batkinson.jrsync.zsync;

public interface ProgressTracker {
    enum Stage {
        SEARCH, BUILD
    }

    void onProgress(Stage stage, int percentComplete);
}
