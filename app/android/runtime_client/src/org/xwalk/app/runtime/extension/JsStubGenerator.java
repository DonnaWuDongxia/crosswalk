package org.xwalk.app.runtime.extension;

import android.util.Log;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;

import org.xwalk.app.runtime.extension.ReflectionHelper.MemberInfo;

public class JsStubGenerator {
    static public String TAG = "JsStubGenerator";
    ReflectionHelper reflection;
    String jsHeader =
        "var jsStub = requireNative(\"jsStub\").jsStub;\n" +
        "var helper = jsStub.create(exports, extension);\n";

    JsStubGenerator (ReflectionHelper mirror) {
        reflection = mirror;
    }

    String generate() {
        String result = "";
        if(reflection.getEventList() != null) {
            result += generateEventTarget();
        }

        Map<String, MemberInfo> members = reflection.getMembers();
        for (String key : members.keySet()) {
            MemberInfo m = members.get(key);
            switch (m.type) {
                case JS_PROPERTY:
                    result += generateProperty(key, m);
                    break;
                case JS_METHOD:
                    result += generateMethod(key, m);
                    break;
                default:
                    break;
            }
        }
        return jsHeader + result + "\n";
    }

    String generateEventTarget() {
        String[] eventList = reflection.getEventList();
        if(eventList == null || eventList.length < 1) {
            return "";
        }

        String gen = "jsStub.makeEventTarget(exports);\n";
        for(String e : eventList) {
            gen += "helper.addEvent(\"" + e + "\");\n";
        }
        return gen;
    }

    String generateProperty(String name, MemberInfo m) {
        if(m.isWritable) {
            return "jsStub.defineProperty(exports, \"" + name + "\", true);\n";
        } else {
            return "jsStub.defineProperty(exports, \"" + name + "\");\n";
        }
    }

    String generatePromiseMethod(String name, String jsArgs) {
        String argStr = "{\"resolve\": resolve, \"reject\":reject}";
        if(jsArgs.length() > 0) {
            argStr = jsArgs + ", " + argStr;
        }
        return String.format(
                "exports.%s = function(%s) {\n" +
                "  return new Promise(function(resolve, reject){\n" +
                "     helper.invokeNative(\"%s\", [%s]);\n" + 
                "  })\n" +
                "};\n",
                name, jsArgs, name, argStr);
    }

    String generateMethod(String name, MemberInfo mInfo) {
        Method m = (Method)mInfo.accesser;
        Class<?>[] pTypes = m.getParameterTypes();
        Annotation[][] anns = m.getParameterAnnotations();
        String jsArgs = "";
        for (int i = 0; i < pTypes.length; i++) {
            Class<?> p = pTypes[i];
            String pStr;
            if(anns[i].length > 0 && anns[i][0] instanceof JsCallback) {
                if(((JsCallback)anns[i][0]).isPromise()) {
                    if(i != pTypes.length - 1) {
                        Log.e(TAG, "@JsCallback(isPromise = true) must annotated on "
                                + "last parameter, the tailing ones will be ignored.");
                    }
                    //generate promise
                    return generatePromiseMethod(name, jsArgs);
                }
                //callback
                pStr = "callback" + i + "_function";
            } else {
                pStr = "arg" + i + "_" + p.getSimpleName();
            }

            if(jsArgs.length() > 0)
                jsArgs += ", ";
            jsArgs += pStr;
        }

        boolean isSync = !(m.getReturnType().equals(Void.TYPE));
        return String.format(
                "exports.%s = function(%s) {\n" +
                ((isSync) ? "  return " : "  ") +
                "helper.invokeNative(\"%s\", [%s], %b);\n" +
                "};\n",
                name, jsArgs, name, jsArgs, isSync);

    }
}
