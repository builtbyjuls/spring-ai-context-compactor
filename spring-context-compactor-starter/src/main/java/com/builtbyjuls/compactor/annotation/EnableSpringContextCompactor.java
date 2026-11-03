package com.builtbyjuls.compactor.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;
import com.builtbyjuls.compactor.config.SpringContextCompactorAutoConfiguration;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(SpringContextCompactorAutoConfiguration.class)
public @interface EnableSpringContextCompactor {
}
