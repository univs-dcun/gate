package ai.univs.auth.shared.swagger;

import ai.univs.auth.shared.web.enums.ErrorType;

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
