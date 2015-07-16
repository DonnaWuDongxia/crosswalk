package org.xwalk.app.runtime.extension;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotated fields and method can be exposed to JavaScript.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface  JsConstructor{

    // The main Java class binding to the JS constructor.
    public Class<?> mainClass();

    // Name for the constructor will be exposed in JS context.
    // If not set, or set to empty string, Java main class name will be used.
    public String exportedJsName() default "";

    // If the constructor is the entry point of this extension.
    // If true, jsName property will be ignored.
    public boolean isEntryPoint() default false;
}
