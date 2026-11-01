package vn.edu.p2p.server.entity.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.*;
import vn.edu.p2p.common.model.Manifest;

@Converter
public class ManifestConverter implements AttributeConverter<Manifest, String> {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Manifest value) {
        if (value == null) return null;
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot serialize manifest.", e);
        }
    }

    @Override
    public Manifest convertToEntityAttribute(String value) {
        if (value == null) return null;
        try {
            return JSON.readValue(value, Manifest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot deserialize manifest.", e);
        }
    }
}
