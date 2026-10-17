package org.xhy.application.tool.service;

import java.net.URI;
import java.util.Objects;

import org.xhy.domain.tool.constant.McpTransportType;

/** Trusted endpoint information resolved for one server and user. The URI can contain gateway credentials. */
public final class McpEndpoint {
    private final McpTransportType transportType;
    private final URI uri;

    public McpEndpoint(McpTransportType transportType, URI uri) {
        this.transportType = Objects.requireNonNull(transportType, "transportType");
        this.uri = Objects.requireNonNull(uri, "uri");
    }

    public McpTransportType transportType() {
        return transportType;
    }

    public URI uri() {
        return uri;
    }

    @Override
    public String toString() {
        return "McpEndpoint[transportType=" + transportType + ", uri=" + uri.getScheme() + "://" + uri.getHost()
                + (uri.getPort() < 0 ? "" : ":" + uri.getPort()) + uri.getRawPath() + "]";
    }
}
