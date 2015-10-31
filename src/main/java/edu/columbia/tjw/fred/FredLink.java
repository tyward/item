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
package edu.columbia.tjw.fred;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * This class is designed to fetch data from the FRED XML API. 
 * 
 * You can find the API docs here: https://api.stlouisfed.org/docs/fred/
 * 
 * FRED itself is here: https://research.stlouisfed.org/fred2/
 * 
 * @author tyler
 */
public final class FredLink
{
    private static final String PROTOCOL = "https";
    private static final String HOST = "api.stlouisfed.org";
    private static final String SERIES_PATH = "/fred/series";
    private static final String OBSERVATION_PATH = "/fred/series/observations";

    private final Proxy _proxy;
    private final String _queryBase;
    private final String _apiKey;
    private final DocumentBuilder _builder;
    private final Map<String, FredSeries> _seriesMap;
    private final Map<String, FredException> _exceptionMap;

    public FredLink(final String apiKey_)
    {
        this(apiKey_, null);
    }

    public FredLink(final String apiKey_, final Proxy proxy_)
    {
        _proxy = proxy_;
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

        _seriesMap = new HashMap<>();
        _exceptionMap = new HashMap<>();
    }

    public synchronized FredSeries fetchSeries(final String seriesName_) throws IOException, FredException
    {
        if (_seriesMap.containsKey(seriesName_))
        {
            final FredSeries series = _seriesMap.get(seriesName_);

            if (null == series)
            {
                final FredException e = _exceptionMap.get(seriesName_);
                throw new FredException(e);
            }

            return series;
        }

        final String seriesQuery = "series_id=" + seriesName_;

        try
        {
            final Element seriesRoot = fetchData(SERIES_PATH, seriesQuery);
            final Element obsRoot = fetchData(OBSERVATION_PATH, seriesQuery);
            final FredSeriesData obs = new FredSeriesData(obsRoot);
            final Element seriesElem = (Element) seriesRoot.getElementsByTagName("series").item(0);
            final FredSeries output = new FredSeries(seriesElem, obs);

            _seriesMap.put(seriesName_, output);

            return output;
        }
        catch (final FredException e)
        {
            _seriesMap.put(seriesName_, null);
            _exceptionMap.put(seriesName_, e);
            throw new FredException(e);
        }
    }

    private void checkError(final Element elem_) throws FredException
    {
        final String tagName = elem_.getTagName();

        if (!tagName.equals("error"))
        {
            return;
        }

        final String code = elem_.getAttribute("code");
        final String error = elem_.getAttribute("message");

        final int codeInt;

        if (null == code)
        {
            codeInt = -1;
        }
        else
        {
            codeInt = Integer.parseInt(code);
        }

        final FredException exc = new FredException(error, codeInt);
        throw exc;
    }

    private Element fetchData(final String pathName_, final String query_) throws IOException, FredException
    {
        final String fullQuery = pathName_ + "?" + _queryBase + query_;
        final URL thisUrl = new URL(PROTOCOL, HOST, fullQuery);

        final HttpURLConnection conn;

        if (null != _proxy)
        {
            conn = (HttpURLConnection) thisUrl.openConnection(_proxy);
        }
        else
        {
            conn = (HttpURLConnection) thisUrl.openConnection();
        }

        final int responseCode = conn.getResponseCode();

        if (400 == responseCode)
        {
            throw new FredException("Element does not exist: " + query_, responseCode);
        }

        try (final InputStream stream = conn.getInputStream())
        {
            final Document output = readXml(stream);
            final Element outputElem = output.getDocumentElement();

            this.checkError(outputElem);

            return outputElem;
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
