/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2010  Mickael Guessant
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package davmail.http.request;

import davmail.exchange.dav.PropertyValue;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Custom Exchange PROPPATCH method.
 * Supports extended property update with type.
 */

public class ExchangePropPatchRequest extends ExchangeDavRequest {
    protected static final Logger LOGGER = Logger.getLogger(ExchangePropPatchRequest.class);
    static final String TYPE_NAMESPACE = "urn:schemas-microsoft-com:datatypes";
    final Set<PropertyValue> propertyValues;
    private StatusLine statusLine;

    /**
     * Create PROPPATCH method.
     *
     * @param path           path
     * @param propertyValues property values
     */
    public ExchangePropPatchRequest(String path, Set<PropertyValue> propertyValues) {
        super(path);
        this.propertyValues = propertyValues;
    }

    @Override
    protected byte[] generateRequestContent() {
        try {
            // build namespace map
            int currentChar = 'e';
            final Map<String, Integer> nameSpaceMap = new HashMap<>();
            final Set<PropertyValue> setPropertyValues = new HashSet<>();
            final Set<PropertyValue> deletePropertyValues = new HashSet<>();
            for (PropertyValue propertyValue : propertyValues) {
                // data type namespace
                if (!nameSpaceMap.containsKey(TYPE_NAMESPACE) && propertyValue.getTypeString() != null) {
                    nameSpaceMap.put(TYPE_NAMESPACE, currentChar++);
                }
                // property namespace
                String namespaceUri = propertyValue.getNamespaceUri();
                if (!nameSpaceMap.containsKey(namespaceUri)) {
                    nameSpaceMap.put(namespaceUri, currentChar++);
                }
                if (propertyValue.getXmlEncodedValue() == null) {
                    deletePropertyValues.add(propertyValue);
                } else {
                    setPropertyValues.add(propertyValue);
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (
                    OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8)
            ) {
                writer.write("<D:propertyupdate xmlns:D=\"DAV:\"");
                for (Map.Entry<String, Integer> mapEntry : nameSpaceMap.entrySet()) {
                    writer.write(" xmlns:");
                    writer.write((char) mapEntry.getValue().intValue());
                    writer.write("=\"");
                    writer.write(mapEntry.getKey());
                    writer.write("\"");
                }
                writer.write(">");
                if (!setPropertyValues.isEmpty()) {
                    writer.write("<D:set><D:prop>");
                    for (PropertyValue propertyValue : setPropertyValues) {
                        String typeString = propertyValue.getTypeString();
                        char nameSpaceChar = (char) nameSpaceMap.get(propertyValue.getNamespaceUri()).intValue();
                        writer.write('<');
                        writer.write(nameSpaceChar);
                        writer.write(':');
                        writer.write(propertyValue.getName());
                        if (typeString != null) {
                            writer.write(' ');
                            writer.write(nameSpaceMap.get(TYPE_NAMESPACE));
                            writer.write(":dt=\"");
                            writer.write(typeString);
                            writer.write("\"");
                        }
                        writer.write('>');
                        writer.write(propertyValue.getXmlEncodedValue());
                        writer.write("</");
                        writer.write(nameSpaceChar);
                        writer.write(':');
                        writer.write(propertyValue.getName());
                        writer.write('>');
                    }
                    writer.write("</D:prop></D:set>");
                }
                if (!deletePropertyValues.isEmpty()) {
                    writer.write("<D:remove><D:prop>");
                    for (PropertyValue propertyValue : deletePropertyValues) {
                        char nameSpaceChar = (char) nameSpaceMap.get(propertyValue.getNamespaceUri()).intValue();
                        writer.write('<');
                        writer.write(nameSpaceChar);
                        writer.write(':');
                        writer.write(propertyValue.getName());
                        writer.write("/>");
                    }
                    writer.write("</D:prop></D:remove>");
                }
                writer.write("</D:propertyupdate>");
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getMethod() {
        return "PROPPATCH";
    }

    @Override
    public List<MultiStatusResponse> handleResponse(HttpResponse response) {
        this.statusLine = response.getStatusLine();
        return super.handleResponse(response);
    }


    public StatusLine getStatusLine() {
        return statusLine;
    }
}
