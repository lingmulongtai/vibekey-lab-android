package com.lingm.vibekey;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

public final class UpdateInstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!ApkInstaller.RESULT.equals(intent.getAction())) return;
        int id = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        Intent confirmation = status == PackageInstaller.STATUS_PENDING_USER_ACTION
                ? intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class) : null;
        AppUpdates.get(context).installResult(id, status, confirmation);
    }
}
