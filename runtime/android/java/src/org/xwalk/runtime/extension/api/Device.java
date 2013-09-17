package org.xwalk.runtime.extension.api;

import org.xwalk.runtime.extension.XWalkExtension;
import org.xwalk.runtime.extension.XWalkExtensionContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.provider.Settings;
import android.util.Log;

/**
 * The extension to get device information, similar with "Device" interface
 * in Cordova. Interfaces listed below:
 *   device.model - the product name of the device
 *   device.crosswalk - the crosswalk verion running on the device
 *   device.platform - device's operating system name
 *   device.uuid - device's Universally Unique Identifier
 *   device.version - the version of operating system
 */
public class Device extends XWalkExtension {
    final private static String TAG = "DeviceExtension";
    final public static String NAME = "device";
    final public static String PLATFORM = "Android";
    // TODO(Donna): find way to get crosswalk version
    final public static String CROSSWALK_VERSION = "1.29.3.0";
    final public static String JS_API_PATH = "js_api/device.js";

    public Device(String JsApiContent, XWalkExtensionContext context) {
        super(NAME, JsApiContent, context);
    }
    private JSONObject getDeviceInfo() throws JSONException {
        JSONObject infoMap = new JSONObject();
        infoMap.put("model", this.getModel());
        infoMap.put("crosswalk", Device.CROSSWALK_VERSION);
        infoMap.put("platform", Device.PLATFORM);
        infoMap.put("uuid", this.getUuid());
        infoMap.put("version", this.getOSVersion());
        return infoMap;
    }
    public String getModel() {
        String model = android.os.Build.MODEL;
        return model;
    }
    public String getUuid() {
        String uuid = Settings.Secure.getString(
                mExtensionContext.getActivity().getContentResolver(),
                android.provider.Settings.Secure.ANDROID_ID);
        return uuid;
    }
    public String getOSVersion() {
        String osversion = android.os.Build.VERSION.RELEASE;
        return osversion;
    }

    public JSONObject handleMessage(String message) throws JSONException {
        JSONObject msgObject = new JSONObject(message);
        JSONObject result = new JSONObject();
        if (msgObject.has("cmd") && msgObject.get("cmd") instanceof String) {
            result.put("cmd", msgObject.getString("cmd"));
            result.put("error", false);
        } else {
            result.put("error", true);
            result.put("errorMessage", "invalid command");
            return result;
        }
        // response to commands
        if (msgObject.getString("cmd").equals("getDeviceInfo")) {
            result.put("reply", getDeviceInfo());
        } else {
            result.put("error", true);
            result.put("errorMessage", "no such command");
        }
        return result;
    }

    @Override
    public void onMessage(String message) {
        try {
            postMessage(handleMessage(message).toString());
        } catch(JSONException e) {
            Log.e(TAG, "Failed to handle the received message: " + message);
        }
    }

    @Override
    public String onSyncMessage(String message) {
        try{
            return handleMessage(message).toString();
        } catch(JSONException e) {
            Log.e(TAG, "Failed to handle the received message: " + message);
            return null;
        }
    }
}
