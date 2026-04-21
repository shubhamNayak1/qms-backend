package com.qms.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverterFactory(caseInsensitiveEnumConverterFactory());
    }

    /**
     * Converts any query/form string to an enum case-insensitively.
     * Allows the frontend to send "Auth", "auth", or "AUTH" for AuditModule.AUTH.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private ConverterFactory<String, Enum> caseInsensitiveEnumConverterFactory() {
        return new ConverterFactory<>() {
            @Override
            public <T extends Enum> org.springframework.core.convert.converter.Converter<String, T>
                    getConverter(Class<T> targetType) {
                return source -> {
                    String value = source.trim();
                    if (value.isEmpty()) return null;
                    try {
                        return (T) Enum.valueOf(targetType, value.toUpperCase());
                    } catch (IllegalArgumentException ex) {
                        throw new IllegalArgumentException(
                                "Invalid value '" + value + "' for " + targetType.getSimpleName()
                                + ". Valid values: " + java.util.Arrays.toString(targetType.getEnumConstants()));
                    }
                };
            }
        };
    }
}
