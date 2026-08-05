package ai.univs.gate.shared.utils;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * base64 로 인코딩된 특징점(descriptor) 문자열 제약 (UG-279).
 *
 * @see DescriptorValidator
 */
@Target({ ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DescriptorValidator.class)
public @interface ValidDescriptor {

    String message() default "Invalid descriptor";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
