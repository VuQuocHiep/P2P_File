package vn.edu.p2p.client.dto.response;

import vn.edu.p2p.common.model.Manifest;

public record UploadResponse(String message, Manifest manifest) {}
