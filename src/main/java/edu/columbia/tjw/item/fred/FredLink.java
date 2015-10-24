/*
 * Copyright 2014 Tyler Ward.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * This code is part of the reference implementation of http://arxiv.org/abs/1409.6075
 * 
 * This is provided as an example to help in the understanding of the ITEM model system.
 */
package edu.columbia.tjw.item.fred;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URL;
import java.net.URLConnection;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author tyler
 */
public final class FredLink
{
    private static final String PROTOCOL = "https";
    private static final String HOST = "api.stlouisfed.org";

    private final String _queryBase;
    private final String _apiKey;
    private final DocumentBuilder _builder;

    public FredLink(final String apiKey_)
    {
        _apiKey = apiKey_;
        _queryBase = "api_key=" + _apiKey + "&";

        final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

        dbf.setValidating(false);
        dbf.setIgnoringComments(false);
        dbf.setIgnoringElementContentWhitespace(true);
        dbf.setNamespaceAware(true);
        // dbf.setCoalescing(true);
        // dbf.setExpandEntityReferences(true);

        try
        {
            _builder = dbf.newDocumentBuilder();
            _builder.setEntityResolver(new NullResolver());
        }
        catch (final ParserConfigurationException e)
        {
            //Realistically, there's nothing the user could do about this anyway.
            throw new RuntimeException(e);
        }
    }

    private Document fetchData(final String pathName_, final String query_) throws IOException
    {
        final String fullQuery = pathName_ + "?" + _queryBase + query_;
        final URL thisUrl = new URL(PROTOCOL, HOST, fullQuery);

        final URLConnection conn = thisUrl.openConnection();

        try (final InputStream stream = conn.getInputStream())
        {
            final Document output = readXml(stream);
            return output;
        }
        catch (final SAXException e)
        {
            throw new IOException("XML exception.", e);
        }

    }

    public Document readXml(InputStream is) throws SAXException, IOException
    {
        return _builder.parse(is);
    }

    private static final class NullResolver implements EntityResolver
    {
        @Override
        public InputSource resolveEntity(String publicId, String systemId) throws SAXException,
                IOException
        {
            return new InputSource(new StringReader(""));
        }

    }

}
