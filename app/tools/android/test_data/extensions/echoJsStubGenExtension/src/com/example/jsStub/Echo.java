package com.example.jsStub;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xwalk.app.runtime.extension.*;

/*
 * Example for JavaScript stub auto-generation feature on Android.
 */
public class Echo extends XWalkExtensionClient {

  // Expose a read only JS property.
  @JsApi
  public String prefix = "From java:";

  // The event list must be "static" field.
  @JsApi(isEventList = true)
  public static String[] events = {"updatePrefix", "click"};

  public Echo(String extensionName, String jsApi, XWalkExtensionContextClient context) {
    super(extensionName, jsApi, context);
  }
  
  public class Event {
      public String type;
      public int dataInt;
      public String dataStr;
      public Event(String t, int dInt, String dStr) {
          type = t;
          dataInt = dInt;
          dataStr = dStr;
      }
  }

  // This method will be invoked to trigger events.
  @JsApi
  public void testEvent() {
      prefix = "a new prefix";
      updateProperty("prefix");
      try{
          JSONObject event = new JSONObject();
          event.put("prefix", prefix);
          dispatchEvent("updatePrefix", event);
          
          // Trigger another event with object data.
          Event e1 = new Event("click", 99, "helloWorld!");
          dispatchEvent(e1.type, e1);
      }catch(Exception e) {
          e.printStackTrace();
      }
  }

  // Expose a sync JS method.
  @JsApi
  public String echo(String msg) {
    return prefix + msg;
  }

  // Expose an async JS method with callback.
  @JsApi
  public void getPrefix(@JsCallback int callback) {
    invokeJsCallback(callback, null, prefix);
    return;
  }

  // Expose a JS method returns promise.
  @JsApi
  public void getPrefixPromise(@JsCallback(isPromise = true) int promise) {
    Boolean flag = true;
    if (flag)
      invokeJsCallback(promise, "resolve", prefix);
    else
      invokeJsCallback(promise, "reject", prefix);
    return;
  }
}
