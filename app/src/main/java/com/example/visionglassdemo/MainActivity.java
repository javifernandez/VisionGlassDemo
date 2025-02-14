package com.example.visionglassdemo;

import android.app.Presentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.huawei.tool.Euler;
import com.huawei.tool.Quaternion;
import com.huawei.usblib.DisplayMode;
import com.huawei.usblib.DisplayModeCallback;
import com.huawei.usblib.VisionGlass;
import com.huawei.usblib.OnConnectionListener;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity implements OnConnectionListener {
    public static final String HUAWEI_USB_PERMISSION = "com.huawei.usblib.USB_PERMISSION";
    private boolean mIsAskingForPermission;
    private DisplayManager mDisplayManager;
    private Display mPresentationDisplay;
    private VisionGlassPresentation mActivePresentation;
    private boolean mSwitchedTo3DMode = false;
    Quaternion mQuaternion;
    private Euler mEuler;
    private Euler mEulerWolvic;


    private final BroadcastReceiver mUsbPermissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d("MainActivity", "USB permission broadcast; waiting for permission: " + mIsAskingForPermission + "; intent: " + intent.toString());

            mIsAskingForPermission = false;
            initVisionGlass();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("MainActivity", "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mIsAskingForPermission = false;
        mDisplayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);

        // Alternatively: android.hardware.usb.action.USB_DEVICE_ATTACHED, USB_DEVICE_DETACHED.
        IntentFilter usbPermissionFilter = new IntentFilter();
        usbPermissionFilter.addAction(HUAWEI_USB_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mUsbPermissionReceiver, usbPermissionFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mUsbPermissionReceiver, usbPermissionFilter);
        }

        mDisplayManager.registerDisplayListener(mDisplayListener, null);

        VisionGlass.getInstance().init(this);
        initVisionGlass();
    }


    @Override
    protected void onPause() {
        Log.d("MainActivity", "PlatformActivity onPause");
        super.onPause();
        //getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onResume() {
        Log.d("MainActivity", "PlatformActivity onResume");
        super.onResume();
        //getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Sometimes no display event is emitted so we need to call updateDisplays() from here.
        if (VisionGlass.getInstance().isConnected() && VisionGlass.getInstance().hasUsbPermission() && mActivePresentation == null) {
            updateDisplays();
        }
    }

    private void initVisionGlass() {
        Log.d("MainActivity", "initVisionGlass");
        if (!VisionGlass.getInstance().isConnected()) {
            Log.d("MainActivity", "Glasses not connected yet");
            //mViewModel.updateConnectionState(PhoneUIViewModel.ConnectionState.DISCONNECTED);
            return;
        }

        if (!VisionGlass.getInstance().hasUsbPermission()) {
            if (!mIsAskingForPermission) {
                Log.d("MainActivity", "Asking for USB permission");
                mIsAskingForPermission = true;
                //mViewModel.updateConnectionState(PhoneUIViewModel.ConnectionState.REQUESTING_PERMISSIONS);
                VisionGlass.getInstance().requestUsbPermission();
            } else {
                Log.d("MainActivity", "Waiting for USB permission");
            }
            return;
        }

        VisionGlass.getInstance().setDisplayMode(DisplayMode.vr3d, new DisplayModeCallback() {
            @Override
            public void onSuccess(DisplayMode displayMode) {
                Log.d("MainActivity", "Successfully switched to 3D mode");
                mSwitchedTo3DMode = true;
            }

            @Override
            public void onError(String s, int i) {
                Log.d("MainActivity", "Error " + i + "; failed to switch to 3D mode: " + s);
                //mViewModel.updateConnectionState(PhoneUIViewModel.ConnectionState.DISPLAY_UNAVAILABLE);
            }
        });
    }

    private void startImuService() {
        VisionGlass.getInstance().startImu((w, x, y, z) -> {
            mQuaternion = new Quaternion(w, x, y, z);
            mEuler = mQuaternion.toEuler();
            mEulerWolvic = quaternionToEulerWolvic(mQuaternion);
            runOnUiThread(() -> {
                updatePhoneUI();
                updatePresentationUI();
            });
        });
    }

    private void updatePhoneUI() {
        TextView quaternionView = findViewById(R.id.quaternion);
        quaternionView.setText(mQuaternion.toString());
        TextView eulerSDKView = findViewById(R.id.euler_sdk);
        eulerSDKView.setText(mEuler.toString());
        TextView eulerWolvicView = findViewById(R.id.euler_wolvic);
        eulerWolvicView.setText(mEulerWolvic.toString());
    }

    private void updatePresentationUI() {
        if (mActivePresentation == null) {
            return;
        }

        TextView quaternionView = mActivePresentation.findViewById(R.id.quaternion);
        quaternionView.setText(mQuaternion.toString());
        TextView eulerSDKView = mActivePresentation.findViewById(R.id.euler_sdk);
        eulerSDKView.setText(mEuler.toString());
        TextView eulerWolvicView = mActivePresentation.findViewById(R.id.euler_wolvic);
        eulerWolvicView.setText(mEulerWolvic.toString());
    }

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

    private void updateDisplays() {
        Display[] displays = mDisplayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
        Log.d("MainActivity", "updateDisplays: " + Arrays.toString(displays));

        // a display may be added before we receive the USB permission
        if (!VisionGlass.getInstance().hasUsbPermission()) {
            Log.d("MainActivity", "updateDisplays: no USB permissions yet");
            return;
        }


        if (displays.length > 0) {
            runOnUiThread(() -> showPresentation(displays[0]));
            return;
        }

        mPresentationDisplay = null;
        if (mActivePresentation != null) {
            mActivePresentation.cancel();
            mActivePresentation = null;
        }

        if (!VisionGlass.getInstance().isConnected()) {
            Log.d("MainActivity", "updateDisplays: glasses disconnected");
            //mViewModel.updateConnectionState(PhoneUIViewModel.ConnectionState.DISCONNECTED);
            return;
        }

        // This can happen after switching to 3D mode because the user has not accepted permissions
        // but also when the system is about to replace the display.
        //mViewModel.updateConnectionState(PhoneUIViewModel.ConnectionState.DISPLAY_UNAVAILABLE);
    }

    private final DisplayManager.DisplayListener mDisplayListener =
            new DisplayManager.DisplayListener() {
                private void callUpdateIfIsPresentation(int displayId) {
                    Display display = mDisplayManager.getDisplay(displayId);
                    if (display != null && (display.getFlags() & Display.FLAG_PRESENTATION) == 0) {
                        Log.d("MainActivity", "display listener: not a presentation display");
                        return;
                    }
                    Log.d("MainActivity", "display listener: calling updateDisplay with " + displayId);
                    updateDisplays();
                }

                @Override
                public void onDisplayAdded(int displayId) {
                    Log.d("MainActivity", "display listener: onDisplayAdded displayId = " + displayId);
                    callUpdateIfIsPresentation(displayId);
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    Log.d("MainActivity", "display listener: onDisplayChanged displayId = " + displayId);
                    callUpdateIfIsPresentation(displayId);
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                    Log.d("MainActivity", "display listener: onDisplayRemoved displayId = " + displayId);
                    callUpdateIfIsPresentation(displayId);
                }
            };

    private final DialogInterface.OnDismissListener mOnDismissListener =
            new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    if (dialog.equals(mActivePresentation)) {
                        mActivePresentation = null;
                    }
                }
            };
    private void showPresentation(@NonNull Display presentationDisplay) {
        if (presentationDisplay.equals(mPresentationDisplay) && mActivePresentation != null) {
            Log.d("MainActivity", "showPresentation: already showing presentation");
            //mViewModel.updateConnectionState(PhoneUIViewModel.ConnectionState.ACTIVE);
            return;
        }

        Log.d("MainActivity", "showPresentation: Starting IMU");
        startImuService();

        VisionGlassPresentation presentation = new VisionGlassPresentation(this, presentationDisplay);
        Display.Mode[] modes = presentationDisplay.getSupportedModes();
        Log.d("MainActivity", "showPresentation supported modes: " + Arrays.toString(modes));
        presentation.setPreferredDisplayMode(modes[0].getModeId());
        presentation.show();
        presentation.setOnDismissListener(mOnDismissListener);

        mPresentationDisplay = presentationDisplay;
        mActivePresentation = presentation;

        //mViewModel.updateConnectionState(PhoneUIViewModel.ConnectionState.ACTIVE);
    }

    @Override
    public void onConnectionChange(boolean b) {
        if (VisionGlass.getInstance().isConnected()) {
            Log.d("MainActivity", "onConnectionChange: Device connected");
            //if (mViewModel.getConnectionState().getValue() == PhoneUIViewModel.ConnectionState.DISCONNECTED) {
            //    mViewModel.updateConnectionState(PhoneUIViewModel.ConnectionState.CONNECTING);
            //}
            //initVisionGlass();
        } else {
            Log.d("MainActivity", "onConnectionChange: Device disconnected");

            // reset internal state when the device disconnects
            mPresentationDisplay = null;
            mActivePresentation = null;

            //mViewModel.updateConnectionState(PhoneUIViewModel.ConnectionState.DISCONNECTED);
        }
    }

    private final class VisionGlassPresentation extends Presentation {

        public VisionGlassPresentation(Context context, Display display) {
            super(context, display);
            Log.d("MainActivity", "VisionGlassPresentation constructor for display: " + display);
            setContentView(R.layout.visionglass_presentation_layout);


        }

        public void setPreferredDisplayMode(int modeId) {
            Log.d("MainActivity", "VisionGlassPresentation setPreferredDisplayMode: " + modeId);
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.preferredDisplayModeId = modeId;
            getWindow().setAttributes(params);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            // Be sure to call the super class.
            super.onCreate(savedInstanceState);
            Log.d("MainActivity", "VisionGlassPresentation onCreate");
        }
    }
}
