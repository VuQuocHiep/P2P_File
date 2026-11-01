package vn.edu.p2p.server.entity.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.*;

@Converter
public class StringSetConverter implements AttributeConverter<Set<String>, String> {
    private static final ObjectMapper JSON = new ObjectMapper();
    public String convertToDatabaseColumn(Set<String> value) {
        try { return JSON.writeValueAsString(value == null ? Set.of() : value); }
        catch (Exception e) { throw new IllegalArgumentException("Không lưu được quyền quét file.", e); }
    }
    public Set<String> convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) return new HashSet<>();
        try { return JSON.readValue(value, new TypeReference<HashSet<String>>() {}); }
        catch (Exception e) { throw new IllegalArgumentException("Không đọc được quyền quét file.", e); }
    }
}
