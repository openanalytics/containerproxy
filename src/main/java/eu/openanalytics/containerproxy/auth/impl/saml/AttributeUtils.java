/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2020 Open Analytics
 *
 * ===========================================================================
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
package eu.openanalytics.containerproxy.auth.impl.saml;

import org.apache.logging.log4j.Logger;
import org.opensaml.saml2.core.Attribute;
import org.opensaml.xml.XMLObject;
import org.opensaml.xml.schema.XSAny;
import org.opensaml.xml.schema.XSString;
import org.springframework.security.saml.SAMLCredential;

import java.util.List;

public class AttributeUtils {

    public static String getAttributeValue(Attribute attribute) {
        // copied from Attribute class ...
        List<XMLObject> attributeValues = attribute.getAttributeValues();
        if (attributeValues == null || attributeValues.size() == 0) {
            return null;
        }
        XMLObject xmlValue = attributeValues.iterator().next();
        return getString(xmlValue);
    }

    public static String[] getAttributeAsStringArray(Attribute attribute) {
        if (attribute == null) {
            return null;
        }
        List<XMLObject> attributeValues = attribute.getAttributeValues();
        if (attributeValues == null || attributeValues.size() == 0) {
            return new String[0];
        }
        String[] result = new String[attributeValues.size()];
        int i = 0;
        for (XMLObject attributeValue : attributeValues) {
            result[i++] = getString(attributeValue);
        }
        return result;
    }

    private static String getString(XMLObject xmlValue) {
        if (xmlValue instanceof XSString) {
            return ((XSString) xmlValue).getValue();
        } else if (xmlValue instanceof XSAny) {
            return ((XSAny) xmlValue).getTextContent();
        } else {
            return null;
        }
    }

    public static void logAttributes(Logger logger, SAMLCredential credential) {
        String userID = credential.getNameID().getValue();
        List<Attribute> attributes = credential.getAttributes();
        attributes.forEach((attribute) -> {
            logger.info(String.format("[SAML] User: \"%s\" => attribute => name=\"%s\"(\"%s\") => value \"%s\" - \"%s\"",
                    userID,
                    attribute.getName(),
                    attribute.getFriendlyName(),
                    getAttributeValue(attribute),
                    String.join(", ", getAttributeAsStringArray(attribute))));
        });

    }
}
