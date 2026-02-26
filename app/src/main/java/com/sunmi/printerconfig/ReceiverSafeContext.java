package com.sunmi.printerconfig;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;

/**
 * Wraps context receiver registration so legacy SDK code works on Android 13+.
 */
public class ReceiverSafeContext extends ContextWrapper {
    public ReceiverSafeContext(Context base) {
        super(base);
    }

    @Override
    public Context getApplicationContext() {
        return this;
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return super.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        }
        return super.registerReceiver(receiver, filter);
    }

    @Override
    public Intent registerReceiver(
        BroadcastReceiver receiver,
        IntentFilter filter,
        String broadcastPermission,
        Handler scheduler
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return super.registerReceiver(
                receiver,
                filter,
                broadcastPermission,
                scheduler,
                Context.RECEIVER_NOT_EXPORTED
            );
        }
        return super.registerReceiver(receiver, filter, broadcastPermission, scheduler);
    }
}
