// Copyright (c) 2013 Intel Corporation. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.xwalk.app.runtime.extension;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class XWalkExtensionBindingObject {
    int objectId;
    XWalkExtensionClient extension_client;
    protected String TAG;
    ReflectionHelper reflection;

    public XWalkExtensionBindingObject(int objId, XWalkExtensionClient ext) {
        objectId = objId;
        extension_client = ext;
        TAG = "Extension-" + ext.getExtensionName();
        reflection = ReflectionHelper(this.getClass());
    }

    private postMessage () {
    }
    /* Helper method to invoke JavaScript callback.
     *
     * Following message will be sent to JavaScript side:
     * {
     *  cmd:"invokeCallback"
     *  // need to combine the cid and instanceId in the same feild
     *  callInfo: an object contains the callback information(cid, vid)
     *  key: String
     *  args: args
     * }
     */
    public void invokeJsCallback(JSONObject callInfo, String key, Object... args) {
        try {
            int instanceID = callInfo.getInt("instanceID");
            JSONObject jsCallInfo = new JSONObject();
            jsCallInfo.put("cid", callInfo.getInt("cid"));
            jsCallInfo.put("vid", callInfo.getInt("vid"));

            JSONObject msgOut = new JSONObject();
            msgOut.put("cmd", "invokeCallback");
            msgOut.put("callInfo", jsCallInfo);
            msgOut.put("key", key);
            msgOut.put("args", ReflectionHelper.objToJSON(args));
            postMessage(instanceID, msgOut.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* Helper method to print information in JavaScript console,
     * mostly for debug purpose.
     *
     * Following message will be sent to JavaScript side:
     * { cmd:"error"
     *   level: "log", "info", "warn", "error", default is "error"
     *   msg: String
     * }
     */
    public void logJs(int instanceId, String msg, String level) {
        try {
            JSONObject msgOut = new JSONObject(); 
            msgOut.put("cmd", "error");
            msgOut.put("level", level);
            msgOut.put("msg", msg);
            postMessage(instanceId, msgOut.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* Trigger JavaScript handlers in Java side.
     *
     * Following message will be sent to JavaScript side:
     * { cmd:"dispatchEvent"
     *   type: pointed in "supportedEvents" string array
     *   data: a JSON data will passed to js
     * }
     */
    public void dispatchEvent(String type, Object event) {
        if (!reflection.isEventSupported(type)) {
            Log.w(TAG, "Unsupport event in extension: " + type);
            return;
        }
        try {
            JSONObject msgOut = new JSONObject(); 
            msgOut.put("cmd", "dispatchEvent");
            msgOut.put("type", type);
            msgOut.put("event", ReflectionHelper.objToJSON(event));
            // The event will be broadcasted to all extension instances.
            broadcastMessage(msgOut.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* Notify the JavaScript side that some property is updated by Java side.
     *
     * Following message will be sent to JavaScript side:
     * { cmd:"updateProperty"
     *   name: the name of property need to be updated
     * }
     */
    public void updateProperty(String pName) {
        if (!reflection.hasProperty(pName)) {
            Log.w(TAG, "Unexposed property in extension: " + pName);
            return;
        }
        try {
            JSONObject msgOut = new JSONObject();
            msgOut.put("cmd", "updateProperty");
            msgOut.put("name", pName);
            // This message will be broadcasted to all extension instances.
            broadcastMessage(msgOut.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /**
     * Handle message
     * @param extensionInstanceID the ID of extension instance where the message came from.
     * @param message the message from JavaScript code.
     */
    public String handleMessage(int extensionInstanceID, String message) {
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
        }
        return (result != null) ? ReflectionHelper.objToJSON(result): "";
    }
}

