// Copyright (c) 2013 Intel Corporation. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.xwalk.app.runtime.extension;

import android.content.Intent;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This class is to encapsulate the reflection detail of
 * invoking XWalkExtension class in the shared library APK.
 *
 * Each external extension should inherit this class and implements
 * below methods. It's created and registered by runtime side via the
 * configuration information in extensions-config.json.
 */
public class XWalkExtensionClient {
    // The unique name for this extension.
    protected String mName;

    // The JavaScript code stub. Will be injected to JS engine.
    protected String mJsApi;

    // The Entry points that will trigger the extension loading
    protected String[] mEntryPoints;

    // The context used by extensions.
    protected XWalkExtensionContextClient mExtensionContext;

    // Reflection for JS stub generation
    protected ReflectionHelper mirror;

    /**
     * Constructor for extensions need to auto generate jsApi.
     * @param name the extension name.
     * @param apiVersion the version of API.
     * @param context the extension context.
     */
    public XWalkExtensionClient(String name, XWalkExtensionContextClient context) {
        this(name, null, null, context);
    }

    /**
     * Constructor with the information of an extension.
     * @param name the extension name.
     * @param apiVersion the version of API.
     * @param jsApi the code stub of JavaScript for this extension.
     * @param context the extension context.
     */
    public XWalkExtensionClient(String name, String jsApi, XWalkExtensionContextClient context) {
        this(name, jsApi, null, context);
    }

    /**
     * Constructor with the information of an extension.
     * @param name the extension name.
     * @param apiVersion the version of API.
     * @param jsApi the code stub of JavaScript for this extension.
     * @param entryPoints Entry points are used when the extension needs to
     *                    have objects outside the namespace that is
     *                    implicitly created using its name.
     * @param context the extension context.
     */
    public XWalkExtensionClient(String name, String jsApi, String[] entryPoints, XWalkExtensionContextClient context) {
        assert (context != null);
        Log.e(name, "[Extension JSapi] " + jsApi);
        mName = name;
        mJsApi = jsApi;
        mEntryPoints = entryPoints;
        mExtensionContext = context;
        mExtensionContext.registerExtension(this);
        mirror = new ReflectionHelper(this.getClass());

        if (mJsApi == null || mJsApi.length() == 0) {
            mJsApi = new JsStubGenerator(mirror).generate();
        }
        Log.e(name, "[Extension JSapi] " + mJsApi);
    }

    /**
     * Get the unique name of extension.
     * @return the name of extension set from constructor.
     */
    public final String getExtensionName() {
        return mName;
    }

    /**
     * Get the JavaScript code stub.
     * @return the JavaScript code stub.
     */
    public final String getJsApi() {
        return mJsApi;
    }

    /**
     * Get the entry points list.
     * @return the JavaScript code stub.
     */
    public final String[] getEntryPoints() {
        return mEntryPoints;
    }

    /**
     * Called when this app is onStart.
     */
    public void onStart() {
    }

    /**
     * Called when this app is onResume.
     */
    public void onResume() {
    }

    /**
     * Called when this app is onPause.
     */
    public void onPause() {
    }

    /**
     * Called when this app is onStop.
     */
    public void onStop() {
    }

    /**
     * Called when this app is onDestroy.
     */
    public void onDestroy() {
    }

    /**
     * Called when this app is onNewIntent.
     */
    public void onNewIntent(Intent intent) {
    }

    /**
     * Tell extension that one activity exists so that it can know the result
     * of the exit code.
     */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    }


    /**
     * JavaScript calls into Java code. The message is handled by
     * the extension implementation. The inherited classes should
     * override and add its implementation.
     * @param extensionInstanceID the ID of extension instance where the message came from.
     * @param message the message from JavaScript code.
     */
    public void onMessage(int extensionInstanceID, String message) {
        String TAG = mName;
        try {
            JSONObject m = new JSONObject(message);
            Log.e(TAG, "async Message:" + message);
            String cmd = m.getString("cmd");
            switch (cmd) {
                case "invokeNative":
                    String mName = m.getString("name");
                    //Object[] args = getArgsFromJson(m.getJSONArray("args"));
                    mirror.invokeMethod(extensionInstanceID, this, mName, m.getJSONArray("args"));
                    break;
                case "newInstance":
                    //TODO: support constructor
                    break;
                default:
                    Log.w(TAG, "Unsupported cmd: " + cmd);
                    break;
            }
        } catch(Exception e) {
            Log.e(TAG, "[Invalid message] " + e.toString());
            e.printStackTrace();
            return;
        }
    }

    /**
     * Synchronized JavaScript calls into Java code. Similar to
     * onMessage. The only difference is it's a synchronized
     * message.
     * @param extensionInstanceID the ID of extension instance where the message came from.
     * @param message the message from JavaScript code.
     */
    public String onSyncMessage(int extensionInstanceID, String message) {
        //TODO: logic to parse sync message
        String TAG = mName;
        Object result = null;
        Log.e(TAG, "sync Message:" + message);
        try {
            JSONObject m = new JSONObject(message);
            String cmd = m.getString("cmd");
            switch (cmd) {
                case "invokeNative":
                    // invoke the method
                    //TODO: combine instancID args(callback/promise) cid/, args
                    result = mirror.invokeMethod(extensionInstanceID, this, m.getString("name"), m.getJSONArray("args"));
                    break;

                case "getProperty":
                    result = mirror.getProperty(this, m.getString("name"));
                    break;
                case "setProperty":
                    //TODO: Do type check here for the set value or type exception handler
                    // new ReflectField(null, this, m.getString("name")).set(m.get("value"));
                    mirror.setProperty(this, m.getString("name"), m.get("value"));
                    break;
                default:
                    Log.w(TAG, "Unsupported cmd: " + cmd);
                    break;
            }
        } catch(Exception e) {
            Log.e(TAG, "[Invalid message] " + e.toString());
            e.printStackTrace();
        }
        Log.e(TAG, "THE RESULT:::::" + ((result != null) ? ReflectionHelper.objToJSON(result): ""));
        return (result != null) ? ReflectionHelper.objToJSON(result): "";
    }

    public void invokeJsCallback(int cid, String key, Object... args) {
        //{
        //  cmd:"invokeCallback"
        //  // need to combine the cid and instanceId in the same feild
        //  cid: unit32
        //  key: String
        //  args: args
        //}
        try {
            int instanceID = cid >> 24;
            int callbackID  = cid & 0xFFFFFF;
            JSONObject msgOut = new JSONObject();
            msgOut.put("cmd", "invokeCallback");
            msgOut.put("cid", callbackID);
            msgOut.put("key", key);
            msgOut.put("args", ReflectionHelper.objToJSON(args));
            postMessage(instanceID, msgOut.toString());
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void logJs(int instanceId, String msg) {
        logJs(instanceId, msg, "error");
    }
    public void logJs(int instanceId, String msg, String level) {
        /*
         * { cmd:"error"
         *   level: "log", "info", "warn", "error", default is "error"
         *   msg: String
         * }
         */
        try {
            JSONObject msgOut = new JSONObject(); 
            msgOut.put("cmd", "error");
            msgOut.put("level", level);
            msgOut.put("msg", msg);
            postMessage(instanceId, msgOut.toString());
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Post messages to JavaScript via extension's context.
     * It's used by child classes to post message from Java side
     * to JavaScript side.
     * @param instanceID the ID of target extension instance.
     * @param message the message to be passed to Javascript.
     */
    public final void postMessage(int instanceID, String message) {
        mExtensionContext.postMessage(this, instanceID, message);
    }

    /**
     * Broadcast messages to JavaScript via extension's context.
     * It's used by child classes to broadcast message from Java side
     * to all JavaScript side instances of the extension.
     * @param message the message to be passed to Javascript.
     */
    public final void broadcastMessage(String message) {
        mExtensionContext.broadcastMessage(this, message);
    }
}
