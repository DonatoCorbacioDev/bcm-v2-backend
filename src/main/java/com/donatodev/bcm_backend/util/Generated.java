package com.donatodev.bcm_backend.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// JaCoCo's built-in "generated code" filter excludes any method annotated with an
// annotation named exactly "Generated" (any package), but only if the annotation
// survives into bytecode — CLASS retention, not the JDK's SOURCE-retention
// javax.annotation.processing.Generated. Use this to exclude a specific method from
// coverage when the uncovered branch is a compiler/library artifact, not untested logic.
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Generated {
}
