package org.xwalk.app.runtime.extension;

import android.util.Log;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

class ReflectionHelper {
    private static final String TAG = "JsStubReflectHelper";
    private Class<?> myClass;
    private Map<String, MemberInfo> members = new HashMap<String, MemberInfo>();
    private String[] eventList = null;
    static Set<Class<?>> primitives = new HashSet<>();

    public enum MemberType {
        JS_METHOD,
        JS_PROPERTY,
    }

    public class MemberInfo {
        MemberType type;
        boolean isWritable;
        AccessibleObject accesser;
    }

    public ReflectionHelper(Class<?> clazz) {
        myClass = clazz;
        init();
    }

    void getMemberInfo(AccessibleObject[] accessers, MemberType type) {
        for (AccessibleObject a : accessers) {

            if (a.isAnnotationPresent(JsApi.class)){
                JsApi mAnno = a.getAnnotation(JsApi.class);
                String name = ((Member) a).getName();

                //Get eventList from propeties
                if (type == MemberType.JS_PROPERTY && mAnno.isEventList()) {
                    if (!((Field)a).getType().equals(String[].class)) {
                        Log.w(TAG, "Invalid type for Supported JS event list" + name);
                        continue;
                    }
                    try {
                        // Event List should be a class property with "static"
                        eventList = (String[])(((Field)a).get(null));
                    } catch (IllegalArgumentException | IllegalAccessException e ) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    continue;
                }

                MemberInfo mInfo = new MemberInfo();
                mInfo.type = type;
                mInfo.isWritable = mAnno.isWritable();
                mInfo.accesser = a;

                if (members.containsKey(name)) {
                    Log.w(TAG, "Conflict namespace - " + name);
                    continue;
                }  
                members.put(name, mInfo);
            }
        }
    }

    void init() {
        primitives.add(Byte.class);
        primitives.add(Integer.class);
        primitives.add(Long.class);
        primitives.add(Double.class);
        primitives.add(Character.class);
        primitives.add(Float.class);
        primitives.add(Boolean.class);
        primitives.add(Short.class);

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
        return mInfo.type == MemberType.JS_METHOD;
    }

    Boolean hasProperty(String name) {
        if (!members.containsKey(name)) return false;
        MemberInfo mInfo = members.get(name);
        return mInfo.type == MemberType.JS_PROPERTY;
    }

    /*
     * Use case: construct Java object array from JSON array which is passed by JS
     * 1. restore original Java object in the array
     * 2. if the parameter is a callbackID, then combine the instanceID with it
     */
    public static Object[] getArgsFromJson(int instanceID, Method m, JSONArray args) {
        Class<?>[] pTypes = m.getParameterTypes();
        Object[] oArgs = new Object[pTypes.length];
        Annotation[][] anns = m.getParameterAnnotations();
        for (int i = 0; i < pTypes.length; ++i) {
            try{
                Class<?> p = pTypes[i];
                if(p.equals(int.class) || p.equals(Integer.class)) {
                    if(anns[i].length > 0 && anns[i][0] instanceof JsCallback) {
                        //TODO: Should we get the info in mInfo?
                        int callback = args.getInt(i);
                        callback = instanceID << 24 | callback;
                        oArgs[i] = callback;
                    } else {
                        oArgs[i] = args.getInt(i);
                    }
                } else {
                    //TODO: is this enough for other types
                    oArgs[i] = args.get(i);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return oArgs;
    }
    public static boolean isPrimitive(Object obj) {
        Class<?> clz = obj.getClass();
        return clz.isPrimitive() || primitives.contains(clz);
    }

    public static Object toSerializableObject(Object obj) {
        if(isPrimitive(obj) ||
           obj instanceof String ||
           obj instanceof Map ||
           obj instanceof JSONArray ||
           obj instanceof JSONObject) {
            return obj;
        } else if (obj.getClass().isArray()) {
            JSONArray result = new JSONArray();
            Object [] arr = (Object[]) obj;
            for(int i = 0; i < arr.length; ++i) {
                try{
                    result.put(i, toSerializableObject(arr[i]));
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
            return result;
        } else {
            /*
             * For ordinary objects, we will just serialize the accessible fields.
             */
            try{
                Class<?> c = obj.getClass();
                JSONObject json = new JSONObject();
                Field[] fields = c.getFields();
                for(Field f : fields) {
                    json.put(f.getName(), f.get(obj));
                }
                return json;
            } catch(Exception e) {
                Log.e(TAG, "Field to serialize object to JSON.");
                e.printStackTrace();
                return null;
            }
        }
    }

    /*
     * Use case: return the Java object back to JS after invokeNativeMethod
     * 1. quote string in proper way
     * 2. serialize the normal Java object
     * 3. serialize [Object... args]
     */
    public static String objToJSON(Object obj) {
        //We expect the object is JSONObject or primive type.
        if (obj == null) return "null";

        Object sObj = toSerializableObject(obj);
        if (sObj instanceof String) {
            return JSONObject.quote(sObj.toString());
        } else {
            return sObj.toString();
        }
    }

    Object invokeMethod(int instanceID, Object obj, String mName,
                        JSONArray args) throws ReflectiveOperationException {
        if (!(myClass.isInstance(obj) && hasMethod(mName))) {
            throw new UnsupportedOperationException("Not support:" + mName);
        }
        Method m = (Method)members.get(mName).accesser;
        if((obj == null) || m ==null) {
            Log.e(TAG, "obj is null");
        }
        Object[] oArgs = getArgsFromJson(instanceID, m, args);
        return m.invoke(obj, oArgs);
    }

    Object getProperty(Object obj, String pName)
                       throws ReflectiveOperationException {
        if (!(myClass.isInstance(obj) && hasProperty(pName))) {
            throw new UnsupportedOperationException("Not support:" + pName);
        }
        Field f = (Field)members.get(pName).accesser;
        if(!f.isAccessible())
            f.setAccessible(true);
        if((obj == null) || f ==null) {
            Log.e(TAG, "obj is null");
        }
        return f.get(obj);
    }

    void setProperty(Object obj, String pName, Object value)
                     throws ReflectiveOperationException {
        if (!(myClass.isInstance(obj) && hasProperty(pName))) {
            throw new UnsupportedOperationException("Not support:" + pName);
        }
        Field f = (Field)members.get(pName).accesser;
        if(!f.isAccessible())
            f.setAccessible(true);
        if((obj == null) || f ==null) {
            Log.e(TAG, "obj is null");
        }
        f.set(obj, value);
    }

    String[] getEventList() {
        return eventList;
    }

    boolean isEventSupported(String event) {
        if(eventList == null) return false;
        for(int i = 0; i < eventList.length; ++i) {
            if(eventList[i].equals(event)) return true;
        }
        return false;
    }

    boolean isInstance(Object obj) {
        return myClass.isInstance(obj);
    }
}
