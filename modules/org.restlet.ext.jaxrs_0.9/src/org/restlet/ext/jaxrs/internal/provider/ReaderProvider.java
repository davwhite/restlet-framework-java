/*
 * Copyright 2005-2008 Noelios Consulting.
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License (the "License"). You may not use this file except in
 * compliance with the License.
 * 
 * You can obtain a copy of the license at
 * http://www.opensource.org/licenses/cddl1.txt See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL HEADER in each file and
 * include the License file at http://www.opensource.org/licenses/cddl1.txt If
 * applicable, add the following below this CDDL HEADER, with the fields
 * enclosed by brackets "[]" replaced with your own identifying information:
 * Portions Copyright [yyyy] [name of copyright owner]
 */
package org.restlet.ext.jaxrs.internal.provider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.logging.Logger;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import org.restlet.data.CharacterSet;
import org.restlet.data.Request;
import org.restlet.ext.jaxrs.internal.util.Util;
import org.restlet.resource.Representation;
import org.restlet.util.ByteUtils;

/**
 * This Provider is used to read directly from a {@link Reader}.
 * 
 * @author Stephan Koops
 * @see BufferedReaderProvider
 */
@Provider
public class ReaderProvider extends AbstractProvider<Reader> {

    private static final Logger logger = Logger.getAnonymousLogger();

    /**
     * Returns a Reader wrapping the given entity stream, with respect to the
     * {@link CharacterSet} of the entity of the current {@link Request}, or
     * UTF-8 if no character set was given or if it is not available
     */
    static Reader getReader(InputStream entityStream) {
        Representation entity = Request.getCurrent().getEntity();
        CharacterSet cs;
        if (entity != null) {
            cs = entity.getCharacterSet();
            if (cs == null)
                cs = CharacterSet.UTF_8;
        } else {
            cs = CharacterSet.UTF_8;
        }
        try {
            try {
                return ByteUtils.getReader(entityStream, cs);
            } catch (UnsupportedEncodingException e) {
                try {
                    Reader r;
                    r = ByteUtils.getReader(entityStream, CharacterSet.UTF_8);
                    logger.warning("The character set " + cs
                            + " is not available. Will use "
                            + CharacterSet.UTF_8);
                    return r;
                } catch (UnsupportedEncodingException e1) {
                    try {
                        return ByteUtils.getReader(entityStream, null);
                    } catch (UnsupportedEncodingException e2) {
                        logger.warning("Neither the character set " + cs
                                + " nor the character set "
                                + CharacterSet.UTF_8
                                + " (default) is available");
                        throw new WebApplicationException(500);
                    }
                }
            }
        } catch (IOException ioe) {
            // this catch block could be removed if it is not reachable. 
            throw new WebApplicationException(500);
        }
    }

    /**
     * @see javax.ws.rs.ext.MessageBodyWriter#getSize(java.lang.Object)
     */
    @Override
    public long getSize(Reader t) {
        return -1;
    }

    /**
     * @see MessageBodyReader#readFrom(Class, Type, MediaType, Annotation[],
     *      MultivaluedMap, InputStream)
     */
    @Override
    public Reader readFrom(Class<Reader> type, Type genericType,
            Annotation[] annotations, MediaType mediaType,
            MultivaluedMap<String, String> httpHeaders, InputStream entityStream)
            throws IOException {
        return getReader(entityStream);
    }

    @Override
    protected Class<?> supportedClass() {
        return Reader.class;
    }

    /**
     * @see MessageBodyWriter#writeTo(Object, Type, Annotation[], MediaType,
     *      MultivaluedMap, OutputStream)
     */
    @Override
    public void writeTo(Reader reader, Class<?> type, Type genericType,
            Annotation[] annotations, MediaType mediaType,
            MultivaluedMap<String, Object> httpHeaders,
            OutputStream entityStream) throws IOException {
        CharacterSet charSet = Util.getCharacterSet(httpHeaders);
        if (charSet == null)
            charSet = CharacterSet.UTF_8;
        Util.copyStream(ByteUtils.getStream(reader, charSet), entityStream);
        // NICE testen charset for ReaderProvider.writeTo(..) ?
    }
}