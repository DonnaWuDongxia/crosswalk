package org.xwalk.app.runtime.extension;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotated fields and method can be exposed to JavaScript.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface JsApi {
    /* Property "isWritable" is only meanful for fields. */
    public boolean isWritable() default false;

    /* Property "isEventList" is only meanful for fields,
     * methods will ignore this value.
     */
    public boolean isEventList() default false;

    // The exposed name in JS context.
    // If not set, or set to empty string, name will be the same in Java.
    public String exportedJsName() default "";

    /*
     * This property is only meanful for functions/constructors.
     */
    public boolean isEntryPoint() default false;
}
