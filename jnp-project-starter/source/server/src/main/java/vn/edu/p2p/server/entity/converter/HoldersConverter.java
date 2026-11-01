package vn.edu.p2p.server.entity.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.*;
import vn.edu.p2p.common.model.Manifest;

@Converter
public class HoldersConverter implements AttributeConverter<Map<String, List<Integer>>, String> {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, List<Integer>> value) {
        if (value == null) return null;
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot serialize holders.", e);
        }
    }

    @Override
    public Map<String, List<Integer>> convertToEntityAttribute(String value) {
        if (value == null) return null;
        try {
            return JSON.readValue(
                    value, new TypeReference<LinkedHashMap<String, List<Integer>>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot deserialize holders.", e);
        }
    }
}
