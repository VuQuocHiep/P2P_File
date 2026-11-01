package vn.edu.p2p.client.mapper;

import java.util.ArrayList;
import org.springframework.stereotype.Component;
import vn.edu.p2p.client.dto.response.PeerStatusResponse.LibraryResponse;
import vn.edu.p2p.client.model.Entry;

@Component
public class LibraryMapper {
    public LibraryResponse toResponse(Entry entry) {
        return new LibraryResponse(
                entry.manifest,
                entry.complete,
                entry.sharing,
                entry.catalogStatus,
                new ArrayList<>(entry.chunks), entry.scanConsent);
    }
}
