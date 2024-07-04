package com.utils.usbdetectionlibrary;

import androidx.appcompat.app.AppCompatActivity;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.XmlResourceParser;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.HashMap;

public class USBManager extends AppCompatActivity {

    private static final String ACTION_USB_PERMISSION = "com.example.gfs4400integration.USB_PERMISSION";
    private UsbManager usbManager;
    private PendingIntent permissionIntent;
    private UsbDeviceConnection connection;
    private UsbEndpoint endpoint;
    private UsbDevice device;
    private int vendorId;
    private int productId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usbmanager);
    }

    private void initialiseUSB(){
        Intent intent = new Intent(ACTION_USB_PERMISSION);
        intent.setPackage(getPackageName());
        permissionIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Register the BroadcastReceiver
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(usbReceiver, filter);
        }
        checkConnectedDevices();
    }
    private void checkConnectedDevices() {
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();

        for (UsbDevice d : deviceList.values()) {
            if (isGryphonScanner(d)) {
                usbManager.requestPermission(device, permissionIntent);
                device = d;
                break;
            }
        }
    }

    private boolean isGryphonScanner(UsbDevice device) {
        return device.getVendorId() == vendorId && device.getProductId() == productId;
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (device != null) {
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            setupDeviceCommunication(device);
                        } else {
                            Toast.makeText(context, "Permission denied for device", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    usbManager.requestPermission(device, permissionIntent);
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && connection != null) {
                    connection.close();
                    connection = null; // clear the connection reference
                    Toast.makeText(context, "Device detached", Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

    private void setupDeviceCommunication(UsbDevice device) {
        UsbInterface intf = device.getInterface(0);
        endpoint = intf.getEndpoint(0); // Assuming the first endpoint

        connection = usbManager.openDevice(device);
        if (connection == null || !connection.claimInterface(intf, true)) {
            Toast.makeText(this, "Failed to open device", Toast.LENGTH_SHORT).show();
            return;
        }

    }

    private void parseDeviceFilter() {
        XmlResourceParser parser = getResources().getXml(R.xml.device_filter);
        try {
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.getName().equals("usb-device")) {
                    vendorId = Integer.parseInt(parser.getAttributeValue(null, "vendor-id"));
                    productId = Integer.parseInt(parser.getAttributeValue(null, "product-id"));
                }
                eventType = parser.next();
            }
        } catch (XmlPullParserException | IOException e) {
            e.printStackTrace();
        } finally {
            parser.close();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(usbReceiver);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}