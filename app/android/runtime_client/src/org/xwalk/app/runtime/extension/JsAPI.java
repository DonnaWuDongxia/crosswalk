package org.xwalk.app.runtime.extension;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as being able to be exposed to JavaScript.  This is used for
 * safety purposes so that only explicitly marked methods get exposed instead
 * of every method in a class.
 * See the explanation for {@link XWalkView#addJavascriptInterface(Object, String)}
 * about the usage.
 */
@SuppressWarnings("javadoc")
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface JsAPI {
	public boolean isEntryPoint() default false;
	public boolean isWritable() default false;
	public boolean isConstructor() default false;
	public boolean isEventList() default false;
}                                                                   
