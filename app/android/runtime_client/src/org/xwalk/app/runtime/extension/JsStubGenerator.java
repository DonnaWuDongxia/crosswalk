package org.xwalk.app.runtime.extension;

import java.util.Map;

import org.xwalk.app.runtime.extension.ReflectionHelper.MemberInfo;

public class JsStubGenerator {
	ReflectionHelper reflection;
	String jsHeader = "";
	String jsEnder = "";
	
	JsStubGenerator (String ns, Class<?> clz) {
		reflection = new ReflectionHelper(clz);
	}
	
	JsStubGenerator (ReflectionHelper mirror) {
		reflection = mirror;
	}

	String generate() {
		String result = "";
		Map<String, MemberInfo> members = reflection.getMembers();
		for (String key : members.keySet()) {
			MemberInfo m = members.get(key);
			switch (m.type) {
			case JS_METHOD:
				result += generateMethod();
			case JS_PROPERTY:
				result += generateProperty();
			}
		}
		
		return jsHeader + result + jsEnder;
	}

	String generateMethod() {
        return "";
	}
	
	String generateProperty() {
        return "";
	}
}
