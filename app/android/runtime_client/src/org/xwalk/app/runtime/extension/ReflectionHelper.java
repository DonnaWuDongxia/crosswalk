package org.xwalk.app.runtime.extension;

import android.util.Log;

import java.lang.reflect.*;
import java.util.HashMap;
import java.util.Map;
import org.json.*;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;

class ReflectionHelper {
    private static final String TAG = "JsStubReflectHelper";
    private Class myClass;
    private Map<String, MemberInfo> members = new HashMap<String, MemberInfo>();
    public Field eventList = null;
    static Set<Class> primitives = new HashSet<>();

    public enum MemberType {
        JS_METHOD,
        JS_PROPERTY,
    }

    public class MemberInfo {
        Class<?> clazz;
        MemberType type;
        boolean isWritable;
        AccessibleObject accesser;

        MemberInfo(Class<?> classObject) {
            clazz = classObject;
        }   
    }

    public ReflectionHelper(Class<?> clazz) {
        myClass = clazz;
        primitives.add(Byte.class);
        primitives.add(Integer.class);
        primitives.add(Long.class);
        primitives.add(Double.class);
        primitives.add(Character.class);
        primitives.add(Float.class);
        primitives.add(Boolean.class);
        primitives.add(Short.class);

        init();
    }

    void getMemberInfo(AccessibleObject[] accessers, MemberType type) {
        for (AccessibleObject a : accessers) {

            if (a.isAnnotationPresent(JsAPI.class)){
                JsAPI mAnno = a.getAnnotation(JsAPI.class);
                String name = ((Member) a).getName();

                //Get eventList from propeties
                if (type == MemberType.JS_PROPERTY && mAnno.isEventList()) {
                    if (!((Field)a).getType().equals(String[].class)) {
                        Log.w(TAG, "Invalid type for Supported JS event list" + name);
                        continue;
                    }
                    eventList = (Field)a;
                    continue;
                }

                MemberInfo mInfo = new MemberInfo(myClass);
                mInfo.type = type;
                mInfo.isWritable = mAnno.isWritable();
                mInfo.accesser = a;

                if (members.containsKey(name)) {
                    //TODO: LOG out the confliction
                    Log.w(TAG, "Conflict namespace - " + name);
                    continue;
                }  
                members.put(name, mInfo);
            }
        }

    }

    void init() {
        // Find all functions.
        getMemberInfo(myClass.getDeclaredMethods(), MemberType.JS_METHOD);

        // Find all properties
        getMemberInfo(myClass.getDeclaredFields(), MemberType.JS_PROPERTY);
    }

    Map<String, MemberInfo> getMembers() {
        return members;
    }

    Boolean hasMethod(String name) {
        if (!members.containsKey(name)) return false;
        MemberInfo mInfo = members.get(name);
        if (mInfo.type == MemberType.JS_METHOD)
            return true;
        else
            return false;
    }

    Boolean hasProperty(String name) {
        if (!members.containsKey(name)) return false;
        MemberInfo mInfo = members.get(name);
        if (mInfo.type == MemberType.JS_PROPERTY)
            return true;
        else
            return false;
    }

    /*
     * Use case: construct Java object array from JSON array which is passed by JS
     * 1. restore original Java object in the array
     * 2. if the parameter is a callbackID, then combine the instanceID with it
     */
    public static Object[] getArgsFromJson(int instanceID, Method m, JSONArray args) {
        //TODO: convert JSON args to Java object[]
        Log.e("getArgsFromJson", "***args:" + args);
        Log.e("getArgsFromJson", "***instanceID:" + instanceID);
        Class[] pTypes = m.getParameterTypes();
        Object[] oArgs = new Object[pTypes.length];
        Annotation[][] anns = m.getParameterAnnotations();
        for (int i = 0; i < pTypes.length; i++) {
            try{
                Class p = pTypes[i];
                if(p.equals(int.class) || p.equals(Integer.class)) {
                    if(anns[i].length > 0 && anns[i][0] instanceof JsCallback) {
                        //TODO: Should we get the info in mInfo?
                        int callback = args.getInt(i);
                        callback = instanceID << 24 | callback;
                        oArgs[i] = callback;
                    } else {
                        oArgs[i] = args.getInt(i);
                    }
                    Log.e("getArgsFromJson", "***oArgs[i]:" + oArgs[i]);
                } else if (p.equals(String.class)) {
                    oArgs[i] = args.getString(i);
                }
                //TODO: parse other types
            } catch (Exception e) {
                Log.e("getArgsFromJson", "exception");
                e.printStackTrace();
            }
        }
        return oArgs;
    }
    public static boolean isPrimitive(Object obj) {
        Class clz = obj.getClass();
        return clz.isPrimitive() || primitives.contains(clz);
    }

    /*
     * Use case: return the Java object back to JS after invokeNativeMethod
     * 1. quote string in proper way
     * 2. serialize the normal Java object
     */
    public static String objToJSON(Object obj) {
        //We expect the object is JSONObject or primive type.
        if (obj instanceof String) {
            return JSONObject.quote(obj.toString());
        } else if(isPrimitive(obj) || obj instanceof JSONObject) {
            return obj.toString();
        } else {
            /*
             * For objects, we will just serialize the accessible fields.
             */
            try{
                Class c = obj.getClass();
                JSONObject json = new JSONObject();
                Field[] fields = c.getFields();
                for(Field f : fields) {
                    json.put(f.getName(), f.get(obj));
                }
                return json.toString();
            } catch(Exception e) {
                Log.e(TAG, "Field to serialize object to JSON.");
                e.printStackTrace();
            }
        }
        return "";
    }

    Object invokeMethod(int instanceID, Object obj, String mName, JSONArray args) {
        if (!(myClass.isInstance(obj) && hasMethod(mName))) {
            throw new UnsupportedOperationException("Not support:" + mName);
        }
        try {
            Method m = (Method)members.get(mName).accesser;
            if((obj == null) || m ==null) {
                Log.e(TAG, "obj is null");
            }
            Object[] oArgs = getArgsFromJson(instanceID, m, args);
            return m.invoke(obj, oArgs);
        } catch (Exception e) {
            throw e;
        }
        return null;
    }

    Object getProperty(Object obj, String pName) {
        if (!(myClass.isInstance(obj) && hasProperty(pName))) {
            throw new UnsupportedOperationException("Not support:" + pName);
        }
        try {
            Field f = (Field)members.get(pName).accesser;
            if(!f.isAccessible())
                f.setAccessible(true);
            if((obj == null) || f ==null) {
                Log.e(TAG, "obj is null");
            }
            return f.get(obj);
        } catch (Exception e) {
            throw e;
        }
        return null;
    }

    void setProperty(Object obj, String pName, Object value) {
        if (!(myClass.isInstance(obj) && hasProperty(pName))) {
            throw new UnsupportedOperationException("Not support:" + pName);
        }
        try {
            Field f = (Field)members.get(pName).accesser;
            if(!f.isAccessible())
                f.setAccessible(true);
            if((obj == null) || f ==null) {
                Log.e(TAG, "obj is null");
            }
            f.set(obj, value);
        } catch (Exception e) {
            throw e;
        }
    }

    String[] getEventList(Object obj, String event) {
        try {
            return (String[])eventList.get(obj);
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    boolean isEventSupported(Object obj, String event) {
        String[] events = getEventList(obj, event);
        Log.e(TAG, "eventList.length:" + events.length);
        if(events == null) return false;
        for(int i = 0; i < events.length; i++) {
            Log.e("eventList[i]", events[i]);
            if(events[i] == event) return true;
        }
        return false;
    }
}
