package org.xwalk.app.runtime.extension;

import android.util.Log;

import java.lang.reflect.*;
import java.util.HashMap;
import java.util.Map;
import org.json.*;

class ReflectionHelper {
    private static final String TAG = "JsStubReflectHelper";
    private Class myClass;
    public MemberInfo entryPoint = null;
    private Map<String, MemberInfo> members = new HashMap<String, MemberInfo>();

    /*  
     * 4 types: method, setter, getter, constructor
     */
    public enum MemberType {
        JS_METHOD,
        JS_PROPERTY,
        JS_CONSTRUCTOR
    }

    public class MemberInfo {
        Class<?> clazz;
        MemberType type;
        boolean isEntryPoint;
        boolean isWritable;
        AccessibleObject accesser;

        MemberInfo(Class<?> classObject) {
            clazz = classObject;
        }   
    }

    public ReflectionHelper(Class<?> clazz) {
        myClass = clazz;

        init();
    }

    void getMemberInfo(AccessibleObject[] accessers, MemberType type) {
        for (AccessibleObject a : accessers) {

            if (a.isAnnotationPresent(JsAPI.class)){
                MemberInfo mInfo = new MemberInfo(myClass);
                mInfo.accesser = a;
                JsAPI mAnno = a.getAnnotation(JsAPI.class);
                mInfo.isEntryPoint = mAnno.isEntryPoint();
                mInfo.isWritable = mAnno.isWritable();
                String name = ((Member) a).getName();
                mInfo.type = type;

                if (type == MemberType.JS_METHOD && mAnno.isConstructor())
                    mInfo.type = MemberType.JS_CONSTRUCTOR;

                if (mInfo.isEntryPoint){
                    if (entryPoint == null) {
                        entryPoint = mInfo;
                    } else {
                        Log.w(TAG, "Entry point already exists, conflict - " + name);
                    }
                    continue;
                }

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
        if (mInfo.type == MemberType.JS_METHOD || mInfo.type == MemberType.JS_CONSTRUCTOR)
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

    MemberInfo getEntryPoint() {
        return entryPoint;
    }

    Object invokeMethod(Object obj, String mName, JSONArray args) {
        if (!(myClass.isInstance(obj) && hasMethod(mName))) {
            throw new UnsupportedOperationException("Not support");
        }
        try {
            Method m = (Method)members.get(mName).accesser;
            if((obj == null) || m ==null) {
                Log.e(TAG, "obj is null");
            }
            //convert JSON args TO Object...
            Class[] pTypes = m.getParameterTypes();
            Object[] oArgs = new Object[pTypes.length];
            for (int i = 0; i < pTypes.length; i++) {
                Class p = pTypes[i];
                if(p.equals(int.class) || p.equals(Integer.class)) {
                    oArgs[i] = args.getInt(i);
                } else if (p.equals(String.class)) {
                    oArgs[i] = args.getString(i);
                }
            }
            return m.invoke(obj, oArgs);
        } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    Object getProperty(Object obj, String pName) {
        if (!(myClass.isInstance(obj) && hasProperty(pName))) {
            throw new UnsupportedOperationException("Not support");
        }
        try {
            Field f = (Field)members.get(pName).accesser;
            if(!f.isAccessible())
                f.setAccessible(true);
            if((obj == null) || f ==null) {
                Log.e(TAG, "obj is null");
            }
            return f.get(obj);
        } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    void setProperty(Object obj, String pName, Object value) {
        if (!(myClass.isInstance(obj) && hasProperty(pName))) {
            throw new UnsupportedOperationException("Not support");
        }
        try {
            Field f = (Field)members.get(pName).accesser;
            if(!f.isAccessible())
                f.setAccessible(true);
            if((obj == null) || f ==null) {
                Log.e(TAG, "obj is null");
            }
            f.set(obj, value);
        } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
