package com.microsoft.azure.kusto.data;

import org.apache.http.client.utils.URIBuilder;

import java.net.URISyntaxException;

public class UriUtils {
    public static String concatPathToUri(String uri, String path, boolean ensureTrailingSlash) throws URISyntaxException {
        if (ensureTrailingSlash && !path.endsWith("/")) {
            path += "/";
        }
        return new URIBuilder(uri).setPath(path).build().toString();
    }

    public static String concatPathToUri(String uri, String path) throws URISyntaxException {
        return concatPathToUri(uri, path, false);
    }

}
