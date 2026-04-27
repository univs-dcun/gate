package ai.univs.gate.shared.swagger;

import ai.univs.gate.shared.web.enums.ErrorType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SwaggerError {

    ErrorType errorType();
    int status();
}