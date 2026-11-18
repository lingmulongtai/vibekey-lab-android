package com.lingm.vibekey;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/** Process-local state shared by the two activities. All state changes run on the main thread. */
final class AppUpdates {
    private static final long CHECK_INTERVAL_MS = 15 * 60 * 1000L;
    private static final String INSTALL_SESSION = "install_session";
    private static AppUpdates instance;

    enum State { IDLE, CHECKING, CURRENT, AVAILABLE, CHECK_FAILED, DOWNLOADING, DOWNLOAD_FAILED,
        READY, INSTALLING, INSTALL_CANCELED, INSTALL_FAILED, COMPLETE }

    private final Context context;
    private final SharedPreferences settings;
    private final File cacheFile;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final java.util.concurrent.Executor worker = Executors.newSingleThreadExecutor();
    private final List<Runnable> listeners = new ArrayList<>();
    private long lastCheck = -CHECK_INTERVAL_MS;
    private State state = State.IDLE;
    private GitHubUpdates.Release release;
    private File apk;
    private int sessionId = -1;
    private Intent confirmation;
    private boolean receivedInstallerAction;
    private boolean recoveredSession;

    private AppUpdates(Context context) {
        this.context = context.getApplicationContext();
        settings = this.context.getSharedPreferences("app_updates", Context.MODE_PRIVATE);
        cacheFile = new File(this.context.getCacheDir(), "vibekey-update.apk");
        sessionId = settings.getInt(INSTALL_SESSION, -1);
        recoveredSession = sessionId >= 0;
        if (recoveredSession) state = State.INSTALLING;
        else worker.execute(() -> cacheFile.delete());
    }

    static AppUpdates get(Context context) {
        if (instance == null) instance = new AppUpdates(context);
        return instance;
    }

    State state() { return state; }
    String availableVersion() { return release == null ? null : release.version(); }
    boolean hasPackage() { return apk != null; }
    boolean canRestartInstall() { return recoveredSession && sessionId >= 0; }

    void observe(Runnable listener) {
        listeners.add(listener);
        listener.run();
    }

    void remove(Runnable listener) { listeners.remove(listener); }

    private void change(State next) {
        state = next;
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }

    void check(boolean manual) {
        long now = SystemClock.elapsedRealtime();
        if (state == State.CHECKING || state == State.DOWNLOADING || state == State.INSTALLING
                || (!manual && (now - lastCheck < CHECK_INTERVAL_MS || apk != null))) return;
        lastCheck = now;
        change(State.CHECKING);
        worker.execute(() -> {
            try {
                GitHubUpdates.Release latest = GitHubUpdates.check(BuildConfig.VERSION_NAME);
                main.post(() -> {
                    if (!java.util.Objects.equals(release, latest)) discardPackage();
                    release = latest;
                    change(latest == null ? State.CURRENT : apk == null ? State.AVAILABLE : State.READY);
                });
            } catch (Exception failure) {
                main.post(() -> change(State.CHECK_FAILED));
            }
        });
    }

    void download() {
        if (release == null || (state != State.AVAILABLE && state != State.DOWNLOAD_FAILED)) return;
        GitHubUpdates.Release requested = release;
        change(State.DOWNLOADING);
        worker.execute(() -> {
            File file = cacheFile;
            try {
                GitHubUpdates.download(requested, file);
                ApkInstaller.verify(context, file, requested.version());
                main.post(() -> {
                    apk = file;
                    change(State.READY);
                });
            } catch (Exception failure) {
                file.delete();
                main.post(() -> change(State.DOWNLOAD_FAILED));
            }
        });
    }

    void install() {
        if (apk == null || state == State.INSTALLING || state == State.CHECKING
                || !context.getPackageManager().canRequestPackageInstalls()) return;
        File file = apk;
        String version = release.version();
        receivedInstallerAction = false;
        change(State.INSTALLING);
        worker.execute(() -> {
            try {
                ApkInstaller.verify(context, file, version);
                int id = ApkInstaller.stage(context, file);
                if (!settings.edit().putInt(INSTALL_SESSION, id).commit()) {
                    ApkInstaller.abandon(context, id);
                    settings.edit().remove(INSTALL_SESSION).apply();
                    throw new java.io.IOException("Could not save installation session");
                }
                main.post(() -> {
                    sessionId = id;
                    commit(id);
                });
            } catch (Exception failure) {
                main.post(() -> {
                    if (!file.isFile()) {
                        discardPackage();
                        change(State.DOWNLOAD_FAILED);
                    } else {
                        change(State.INSTALL_FAILED);
                    }
                });
            }
        });
    }

    private void commit(int id) {
        worker.execute(() -> {
            try {
                ApkInstaller.commit(context, id);
                main.postDelayed(() -> {
                    if (sessionId == id && !receivedInstallerAction) failInstall(id);
                }, 30_000L);
            } catch (Exception failure) {
                main.post(() -> failInstall(id));
            }
        });
    }

    void installResult(int id, int status, Intent userAction) {
        if (id < 0 || id != sessionId) return;
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            if (userAction == null) {
                failInstall(id);
                return;
            }
            receivedInstallerAction = true;
            confirmation = userAction;
            change(State.INSTALLING);
            return;
        }

        clearSession();
        if (status == PackageInstaller.STATUS_SUCCESS) {
            discardPackage();
            release = null;
            change(State.COMPLETE);
        } else {
            change(status == PackageInstaller.STATUS_FAILURE_ABORTED ? State.INSTALL_CANCELED : State.INSTALL_FAILED);
        }
    }

    Intent takeConfirmation() {
        Intent action = confirmation;
        confirmation = null;
        return action;
    }

    void failConfirmation() {
        if (sessionId >= 0) failInstall(sessionId);
    }

    void restartInstall() {
        if (!canRestartInstall()) return;
        failInstall(sessionId);
        check(true);
    }

    private void failInstall(int id) {
        if (id != sessionId) return;
        clearSession();
        worker.execute(() -> ApkInstaller.abandon(context, id));
        change(State.INSTALL_FAILED);
    }

    private void clearSession() {
        sessionId = -1;
        confirmation = null;
        recoveredSession = false;
        settings.edit().remove(INSTALL_SESSION).apply();
    }

    private void discardPackage() {
        apk = null;
        worker.execute(() -> cacheFile.delete());
    }
}
