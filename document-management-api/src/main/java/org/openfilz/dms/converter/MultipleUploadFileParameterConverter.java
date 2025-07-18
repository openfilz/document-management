package org.openfilz.dms.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.openfilz.dms.dto.MultipleUploadFileParameter;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;

@Configuration
@RequiredArgsConstructor
@ConfigurationPropertiesBinding
public class MultipleUploadFileParameterConverter implements Converter<String, MultipleUploadFileParameter> {

    private final ObjectMapper objectMapper;

    @Override
    public MultipleUploadFileParameter convert(String json) {
        try {
            return objectMapper.readValue(json, MultipleUploadFileParameter.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
