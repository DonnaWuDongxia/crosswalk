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
    private Map<String, ReflectionHelper> constructorReflections = new HashMap<String, ReflectionHelper>();
    private String[] eventList = null;
    private MemberInfo entryPoint = null;
    static Set<Class<?>> primitives = new HashSet<>();

    public enum MemberType {
        JS_METHOD,
        JS_PROPERTY,
        JS_CONSTRUCTOR
    }

    public class MemberInfo {
        MemberType type;
        boolean isWritable;
        AccessibleObject accesser;
        boolean isEntryPoint;
        String jsName;
        String javaName;
        //Only for JS_CONSTRUCTOR.
        Class<?> mainClass;
    }

    public ReflectionHelper(Class<?> clazz) {
        myClass = clazz;
        init();
    }

    void getMemberInfo(AccessibleObject[] accessers, MemberType type) {
        for (AccessibleObject a : accessers) {

            MemberInfo mInfo = new MemberInfo();
            String name = ((Member) a).getName();
            String jsName = "";
            mInfo.javaName = name;
            mInfo.accesser = a;
            if (a.isAnnotationPresent(JsApi.class)) {
                JsApi mAnno = a.getAnnotation(JsApi.class);

                // Get eventList from properties.
                if (type == MemberType.JS_PROPERTY && mAnno.isEventList()) {
                    if (!((Field)a).getType().equals(String[].class)) {
                        Log.w(TAG, "Invalid type for Supported JS event list" + name);
                        continue;
                    }
                    try {
                        // Event List should be a class property with "static".
                        eventList = (String[])(((Field)a).get(null));
                    } catch (IllegalArgumentException | IllegalAccessException e ) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    continue;
                }

                mInfo.type = type;
                mInfo.isWritable = mAnno.isWritable();
                mInfo.isEntryPoint = mAnno.isEntryPoint();
                jsName = mAnno.exportedJsName();
                mInfo.jsName = (jsName.length == 0) ? name : jsName; 
            } else if (a.isAnnotationPresent(JsConstructor.class)) {
                if (type != MemberType.JS_METHOD) {
                    Log.w(TAG, "Invalid @JsConstructor on non-function member:" + name);
                    continue;
                }
                JsConstructor cAnno = a.getAnnotation(JsConstructor.class);
                mInfo.type = MemberType.JS_CONSTRUCTOR;
                mInfo.isEntryPoint = cAnno.isEntryPoint();
                mInfo.mainClass = cAnno.getMainClass();
                // TODO: more detail checking for main class.
                // Is there a way to throw compile error if main class missing?
                if (mInfo.mainClass == null) continue;

                jsName = cAnno.exportedJsName();
                mInfo.jsName = (jsName.length == 0) ? mInfo.mainClass.getName() : jsName; 
                // Create relections for constructor main classes.
                constructorReflections.push(jsName, new ReflectionHelper(mInfo.mainClass));
            }

            jsName = mInfo.jsName; 
            if (mInfo.isEntryPoint) {
                // Always get the first entry point setting.
                if (entryPoint != null) {
                    Log.w(TAG, "Entry point already exist, try to set another:" + jsName);
                    continue;
                }
                // Flag isEntryPoint only meanful for methods.
                if (type != MemberType.JS_METHOD) {
                    Log.w(TAG, "Invalid entry point setting on property:" + name);
                    continue;
                }
                // The first entry point will be used.
                entryPoint = mInfo;
                continue;
            }
            if (members.containsKey(jsName) && (members.get(jsName).type == mInfo.type)) {
                Log.w(TAG, "Conflict namespace - " + jsName);
                continue;
            }  
            members.put(jsName, mInfo);
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

    ReflectionHelper getConstructorReflection(String cName) {
        if (!constructorReflections.containsKey(cName)) return null;

        return constructorReflections.get(cName);
    }

    Boolean hasMethod(String name) {
        if (!members.containsKey(name)) return false;

        MemberInfo m = members.get(name);
        return ((m.type == MemberType.JS_METHOD) || (m.type == MemberType.JS_CONSTRUCTOR));
    }

    Boolean hasProperty(String name) {
        if (!members.containsKey(name)) return false;

        return members.get(name).type == MemberType.JS_PROPERTY;
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
            try {
                Class<?> p = pTypes[i];
                // Identify the callback parameters, JSONObject type with @JsCallback annotation.
                if (p.equals(JSONObject.class)) {
                    if (anns[i].length > 0 && anns[i][0] instanceof JsCallback) {
                        JSONObject callbackInfo = (JSONObject)(args.get(i));

                        // Add extension instance ID to the callbackInfo for message back.
                        callbackInfo.put("instanceID", instanceID);
                        oArgs[i] = callbackInfo;
                    } else {
                        // For other JSONObject arguments.
                        oArgs[i] = (JSONObject)(args.get(i));
                    }
                } else {
                    // TODO: check if this is enough for other types.
                    oArgs[i] = args.get(i);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return oArgs;
    }

    public static boolean isSerializable(Object obj) {
        Class<?> clz = obj.getClass();

        return clz.isPrimitive() ||
                primitives.contains(clz) ||
                obj instanceof String ||
                obj instanceof Map ||
                obj instanceof JSONArray ||
                obj instanceof JSONObject;
    }

    public static Object toSerializableObject(Object obj) {
        if (isSerializable(obj)) return obj;

        if (obj.getClass().isArray()) {
            JSONArray result = new JSONArray();
            Object [] arr = (Object[]) obj;
            for (int i = 0; i < arr.length; ++i) {
                try {
                    result.put(i, toSerializableObject(arr[i]));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return result;
        }
        /*
         * For ordinary objects, we will just serialize the accessible fields.
         */
        try {
            Class<?> c = obj.getClass();
            JSONObject json = new JSONObject();
            Field[] fields = c.getFields();
            for (Field f : fields) {
                json.put(f.getName(), f.get(obj));
            }
            return json;
        } catch (Exception e) {
            Log.e(TAG, "Field to serialize object to JSON.");
            e.printStackTrace();
            return null;
        }
    }

    /*
     * Use case: return the Java object back to JS after invokeNativeMethod
     * 1. quote string in proper way
     * 2. if there is a "toJSONString" method, it will be used to get JSON string
     * 3. serialize the normal Java object
     * 4. serialize array [Object... args]
     */
    public static String objToJSON(Object obj) {
        // We expect the object is JSONObject or primive type.
        if (obj == null) return "null";

        // If the object is not directly serializable, we check if it is customised
        // serializable. That means the developer implemented the public serialization
        // method "toJSONObject".
        if (!isSerializable(obj)) {
            try {
                Method m = obj.getClass().getMethod("toJSONString", new Class<?>[0]);
                return (String)(m.invoke(obj, new Object[0]));
            } catch (Exception e) {
                Log.w(TAG, "No serialization method: \"toJSONString\", or errors happened.");
            }
        }

        Object sObj = toSerializableObject(obj);
        return (sObj instanceof String) ?
                JSONObject.quote(sObj.toString()) : sObj.toString();
    }

    Object invokeMethod(int instanceID, Object obj, String mName, JSONArray args)
            throws ReflectiveOperationException {
        if ((obj !== null) && !(myClass.isInstance(obj) && hasMethod(mName))) {
            throw new UnsupportedOperationException("Not support:" + mName);
        }
        Method m = (Method)members.get(mName).accesser;
        if (!m.isAccessible()) {
            m.setAccessible(true);
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
        if (!f.isAccessible()) {
            f.setAccessible(true);
        }
        return f.get(obj);
    }

    void setProperty(Object obj, String pName, Object value)
            throws ReflectiveOperationException {
        if (!(myClass.isInstance(obj) && hasProperty(pName))) {
            throw new UnsupportedOperationException("Not support:" + pName);
        }

        Field f = (Field)members.get(pName).accesser;
        if (!f.isAccessible())
            f.setAccessible(true);
        f.set(obj, value);
    }

    String[] getEventList() {
        return eventList;
    }

    boolean isEventSupported(String event) {
        if (eventList == null) return false;
        for (int i = 0; i < eventList.length; ++i) {
            if (eventList[i].equals(event)) return true;
        }
        return false;
    }

    boolean isInstance(Object obj) {
        return myClass.isInstance(obj);
    }
}
