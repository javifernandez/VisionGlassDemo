package com.example.visionglassdemo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.location.LocationRequestCompat;

import com.huawei.tool.Euler;
import com.huawei.tool.Quaternion;
import com.huawei.usblib.VisionGlass;

public class MainActivity extends AppCompatActivity {
    public static final String HUAWEI_USB_PERMISSION = "com.huawei.usblib.USB_PERMISSION";
    private boolean mIsAskingForPermission;

    private final BroadcastReceiver mUsbPermissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d("MainActivity", "USB permission broadcast; waiting for permission: " + mIsAskingForPermission + "; intent: " + intent.toString());

            mIsAskingForPermission = false;
            startImuService();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Alternatively: android.hardware.usb.action.USB_DEVICE_ATTACHED, USB_DEVICE_DETACHED.
        IntentFilter usbPermissionFilter = new IntentFilter();
        usbPermissionFilter.addAction(HUAWEI_USB_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mUsbPermissionReceiver, usbPermissionFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mUsbPermissionReceiver, usbPermissionFilter);
        }


        VisionGlass.getInstance().init(this);
        if (VisionGlass.getInstance().isConnected()) {
            if (VisionGlass.getInstance().hasUsbPermission()) {
                startImuService();
            } else {
                VisionGlass.getInstance().requestUsbPermission();
            }
        } else {
            Toast.makeText(this, "VisionGlass not connected", Toast.LENGTH_SHORT).show();
        }
    }

    private void startImuService() {
        VisionGlass.getInstance().startImu((w, x,y,z) -> {
            Quaternion quaternion = new Quaternion(w, x, y, z);
            Euler euler = quaternion.toEuler();
            Euler eulerWolvic = quaternionToEulerWolvic(quaternion);
            runOnUiThread(() -> {
                TextView quaternionView = findViewById(R.id.quaternion);
                quaternionView.setText(quaternion.toString());
                TextView eulerSDKView = findViewById(R.id.euler_sdk);
                eulerSDKView.setText(euler.toString());
                TextView eulerWolvicView = findViewById(R.id.euler_wolvic);
                eulerWolvicView.setText(eulerWolvic.toString());
            });
        });
    };

    private Euler quaternionToEulerWolvic(Quaternion quaternion) {
        double w = quaternion.w;
        double x = quaternion.x;
        double y = quaternion.y;
        double z = quaternion.z;

        double roll = Math.atan2(2 * (w * x + y * z), 1 - 2 * (x * x + y * y));
        double pitch = Math.asin(2 * (w * y - z * x));
        double yaw = Math.atan2(2 * (w * z + x * y), 1 - 2 * (y * y + z * z));

        return new Euler(roll, pitch, yaw);
    }
}
