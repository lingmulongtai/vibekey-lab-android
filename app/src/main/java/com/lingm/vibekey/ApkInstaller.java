package com.lingm.vibekey;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.SigningInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

final class ApkInstaller {
    static final String RESULT = "com.lingm.vibekey.UPDATE_RESULT";

    static void verify(Context context, File file, String version) throws PackageManager.NameNotFoundException {
        var manager = context.getPackageManager();
        var flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES);
        PackageInfo candidate = manager.getPackageArchiveInfo(file.getAbsolutePath(), flags);
        PackageInfo installed = manager.getPackageInfo(context.getPackageName(), flags);
        if (candidate == null || !installed.packageName.equals(candidate.packageName)
                || !version.equals(candidate.versionName)
                || candidate.getLongVersionCode() <= installed.getLongVersionCode()
                || candidate.applicationInfo == null
                || (candidate.applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0
                || !sameSigners(installed.signingInfo, candidate.signingInfo)) {
            throw new SecurityException("APK package, version, build type or signing certificate mismatch");
        }
    }

    private static boolean sameSigners(SigningInfo installed, SigningInfo candidate) {
        if (installed == null || candidate == null) return false;
        var trusted = installed.getApkContentsSigners();
        var received = candidate.getApkContentsSigners();
        return trusted != null && received != null && trusted.length > 0 && trusted.length == received.length
                && new HashSet<>(Arrays.asList(trusted)).equals(new HashSet<>(Arrays.asList(received)));
    }

    static int stage(Context context, File apk) throws IOException {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        params.setSize(apk.length());
        params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);

        int id = installer.createSession(params);
        try (var session = installer.openSession(id);
             var input = new FileInputStream(apk);
             var output = session.openWrite("base.apk", 0, apk.length())) {
            input.transferTo(output);
            session.fsync(output);
        } catch (IOException | RuntimeException failure) {
            abandon(context, id);
            throw failure;
        }
        return id;
    }

    static void commit(Context context, int id) throws IOException {
        var installer = context.getPackageManager().getPackageInstaller();
        var intent = new Intent(context, UpdateInstallReceiver.class).setAction(RESULT);
        var result = PendingIntent.getBroadcast(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        try (var session = installer.openSession(id)) {
            session.commit(result.getIntentSender());
        }
    }

    static void abandon(Context context, int id) {
        try {
            context.getPackageManager().getPackageInstaller().abandonSession(id);
        } catch (IllegalArgumentException | SecurityException ignored) {
            // Android may have already finalized the session.
        }
    }
}
