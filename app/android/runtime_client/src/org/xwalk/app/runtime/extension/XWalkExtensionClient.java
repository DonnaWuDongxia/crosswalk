// Copyright (c) 2013 Intel Corporation. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.xwalk.app.runtime.extension;

import android.content.Intent;
import android.util.Log;

import java.util.Map;
import java.util.HashMap;

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

    // Store of all binding objects
    private Map<int, XWalkExtensionBindingObject> bindingObjectStore;
    // Reflection for JS stub generation
    protected ReflectionHelper reflection;

    // Binding Object client
    protected XWalkExtensionBindingObject bindingObjectClient;

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
        mName = name;
        mJsApi = jsApi;
        mEntryPoints = entryPoints;
        mExtensionContext = context;
        reflection = new ReflectionHelper(this.getClass());
        bindingObjectClient = new XWalkExtensionBindingObject(0, this);
        bindingObjectStore = new HashMap<int, XWalkExtensionBindingObject>();

        if (mJsApi == null || mJsApi.length() == 0) {
            mJsApi = new JsStubGenerator(reflection).generate();
            if (mJsApi == null || mJsApi.length() == 0) {
                Log.e("Extension-" + mName, "Can't generate JavaScript stub for this extension.");
                return;
            }
        }
        mExtensionContext.registerExtension(this);
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
     * Tell extension that a binding object is added to store.
     */
    public void onAddBindingObject(Object obj) {
    }

    /**
     * Tell extension that a binding object is destoried and removed from store.
     */
    public void onDestoryBindingObject(Object obj) {
    }

    /**
     * JavaScript calls into Java code. The message is handled by
     * the extension implementation. The inherited classes should
     * override and add its implementation.
     * @param extensionInstanceID the ID of extension instance where the message came from.
     * @param message the message from JavaScript code.
     */
    public void onMessage(int extensionInstanceID, String message) {
        String TAG = "Extension-" + mName;
        try {
            JSONObject m = new JSONObject(message);
            String cmd = m.getString("cmd");
            try {
                switch (cmd) {
                    case "invokeNative":
                        String memberName = m.getString("name");
                        int objectId = m.getInt("objectId");
                        Object targetObj = (objectId < 0) ? this : findBindingObject(objectId);
                        //For static properties.
                        if (objectId == 0) targetObj = null;

                        reflection.invokeMethod(extensionInstanceID,
                               targetObj, memberName, m.getJSONArray("args"));
                        break;
                    case "JsObjectCollected":
                        break;
                    default:
                        Log.w(TAG, "Unsupported cmd: " + cmd);
                        break;
                }
            } catch (Exception e) {
                // Currently, we only notice the user when the argument is not matching.
                // The error message will passed to JavaScript console.
                if (e instanceof IllegalArgumentException) {
                    logJs(extensionInstanceID, e.toString(), "warn");
                } else {
                    Log.w(TAG, "Failed to access member, error msg:\n" + e.toString());
                }
                e.printStackTrace();
            }
        } catch (Exception e) {
            Log.w(TAG, "Invalid message, error msg:\n" + e.toString());
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
        String TAG = "Extension-" + mName;
        Object result = null;
        try {
            JSONObject m = new JSONObject(message);
            String cmd = m.getString("cmd");
            int objectId = m.getInt("objectId");
            String cName = m.getString("__constructor");
            /*
             * 1. message to the extension itself,  objectId:0,    cName:""
             * 2. message to constructor,           objectId:0,    cName:[Its exported JS name]
             * 3, message to object,                objectId:[>1], cName:[Its constructor's JS name]
             */
            Object targetObj = (objectId == 0) ? ((cName.length == 0) ? this : null) : findBindingObject(objectId);
            ReflectionHelper targetReflect = reflection.getConstructorReflection(cName);
            if (targetReflect == null) targetReflect = reflection;

            String memberName = m.getString("name");
            try {
                switch (cmd) {
                    case "invokeNative":
                        result = targetReflect.invokeMethod(extensionInstanceID,
                                targetObj, memberName, m.getJSONArray("args"));
                        break;
                    case "newInstance":
                        Object instance = result = targetReflect.invokeMethod(extensionInstanceID,
                                targetObj, memberName, m.getJSONArray("args"));
                        if (instance != null) {
                            bindingObjectId = m.getInt("bindingObjectId");
                            result = addBindingObject(bindingObject, instance)；
                        } else {
                            result = false;
                        }
                        break;
                    case "getProperty":
                        result = reflection.getProperty(targetObject, memberName);
                        break;
                    case "setProperty":
                        reflection.setProperty(targetObject, memberName, m.get("value"));
                        break;
                    default:
                        Log.w(TAG, "Unsupported cmd: " + cmd);
                        break;
                }
            } catch (Exception e) {
                if (e instanceof IllegalArgumentException) {
                    logJs(extensionInstanceID, e.toString(), "warn");
                } else {
                    Log.w(TAG, "Failed to access member, error msg:\n" + e.toString());
                }

                e.printStackTrace();
            }
        } catch (Exception e) {
            Log.w(TAG, "Invalid message, error msg:\n" + e.toString());
            e.printStackTrace();
        }
        return (result != null) ? ReflectionHelper.objToJSON(result): "";
    }

    protected boolean addBindingObject(int objectId, XWalkExtensionBindingObject obj) {
        if (bindingObjectStore.containsKey(objectId)) {
            Log.w("Extension-" + mName, "Existing binding object:\n" + obj_id);
            return false;
        }

        bindingObjectStore.push(obj_id, obj);
        return true;
    }

    protected Object findBindingObject(int obj_id) {
       return bindingObjectStore.getKey(obj_id);
    }

    public void invokeJsCallback(JSONObject callInfo, String key, Object... args) {
        bindingObjectClient.invokeJsCallback(callInfo, key, args);
    }

    public void logJs(int instanceId, String msg, String level) {
        bindingObjectClient.logJs(instanceId, msg, level);
    }

    public void dispatchEvent(String type, Object event) {
        bindingObjectClient.dispatch(type, event);
    }

    public void updateProperty(String pName) {
        bindingObjectClient.updateProperty(pName);
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
