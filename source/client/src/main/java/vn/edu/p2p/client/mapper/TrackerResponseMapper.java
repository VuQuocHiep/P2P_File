package vn.edu.p2p.client.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Component;
import vn.edu.p2p.client.dto.response.FileResponse;
import vn.edu.p2p.client.dto.response.RegistrationResponse;
import vn.edu.p2p.client.dto.response.SourceResponse;

@Component
public class TrackerResponseMapper {
    private final ObjectMapper json;

    public TrackerResponseMapper(ObjectMapper json) {
        this.json = json;
    }

    public RegistrationResponse toRegistrationResponse(JsonNode data)
            throws JsonProcessingException {
        return json.treeToValue(data, RegistrationResponse.class);
    }

    public List<FileResponse> toFileResponses(JsonNode data) throws IOException {
        return json.readerForListOf(FileResponse.class).readValue(data);
    }

    public List<SourceResponse> toSourceResponses(JsonNode data) throws IOException {
        return json.readerForListOf(SourceResponse.class).readValue(data);
    }
}
