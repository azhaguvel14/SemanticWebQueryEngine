/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openjena.riot.web;

import java.io.IOException ;
import java.io.InputStream ;
import java.util.HashMap ;
import java.util.Map ;

import org.apache.http.HttpEntity ;
import org.apache.http.HttpResponse ;
import org.openjena.atlas.io.IO ;
import org.openjena.atlas.lib.Sink ;
import org.openjena.atlas.web.MediaType ;
import org.openjena.riot.RiotReader ;
import org.openjena.riot.WebContent ;
import org.openjena.riot.lang.LangRIOT ;
import org.openjena.riot.lang.SinkTriplesToGraph ;

import com.hp.hpl.jena.graph.Graph ;
import com.hp.hpl.jena.graph.Triple ;
import com.hp.hpl.jena.query.ResultSet ;
import com.hp.hpl.jena.query.ResultSetFactory ;
import com.hp.hpl.jena.sparql.graph.GraphFactory ;
import com.hp.hpl.jena.sparql.resultset.ResultsFormat ;

/** A collection of handlers for response handling.
 * @see HttpOp#execHttpGet(String, String, java.util.Map)
 * @see HttpOp#execHttpPost(String, String, ContentProducer, String, java.util.Map)
 * 
 */
public class HttpResponseLib
{
    static abstract class AbstractGraphReader implements HttpCaptureResponse<Graph>
    {
        private Graph graph = null ;
        @Override
        final public void handle(String contentType, String baseIRI, HttpResponse response)
        {
            try {
                Graph g = GraphFactory.createDefaultGraph() ;
                HttpEntity entity = response.getEntity() ;
                MediaType mt = new MediaType(response.getFirstHeader(HttpNames.hContentType).getValue()) ;
                mt.getCharset() ;
                Sink<Triple> sink = new SinkTriplesToGraph(g) ; 
                InputStream in = entity.getContent() ;
                LangRIOT parser = createParser(in, baseIRI, sink) ;
                parser.parse() ;
                in.close() ;
                this.graph = g ; 
            } catch (IOException ex) { IO.exception(ex) ; }
        }
    
        @Override
        public Graph get() { return graph ; }
        
        abstract protected LangRIOT createParser(InputStream in, String baseIRI, Sink<Triple> sink) ;
    }

    public static HttpResponseHandler httpDumpResponse = new HttpResponseHandler()
    {
        @Override
        public void handle(String contentType, String baseIRI, HttpResponse response)
        {
            try {
                HttpEntity entity = response.getEntity() ;
                InputStream in = entity.getContent() ;
                int l ;
                byte buffer[] = new byte[1024] ;
                while( (l=in.read(buffer)) != -1 )
                {
                    System.out.print(new String(buffer, 0, l, "UTF-8")) ;
                }
                    
    
                in.close() ;
            } catch (IOException ex)
            {
                ex.printStackTrace(System.err) ;
            }
        }
    } ;
    public static HttpCaptureResponse<Graph> graphReaderTurtle = new AbstractGraphReader()
    {
        @Override
        protected LangRIOT createParser(InputStream in, String baseIRI, Sink<Triple> sink)
        {
            return RiotReader.createParserTurtle(in, baseIRI, sink) ;
        }
    } ;
    public static HttpCaptureResponse<Graph> graphReaderNTriples = new AbstractGraphReader()
    {
        @Override
        protected LangRIOT createParser(InputStream in, String baseIRI, Sink<Triple> sink)
        {
            return RiotReader.createParserNTriples(in, sink) ;
        }
    } ;
    public static HttpCaptureResponse<Graph> graphReaderRDFXML = new AbstractGraphReader()
    {
        @Override
        protected LangRIOT createParser(InputStream in, String baseIRI, Sink<Triple> sink)
        {
            return RiotReader.createParserRDFXML(in, baseIRI, sink) ;
        }
    } ;
    
    public static ResultsFormat contentTypeToResultSet(String contentType) { return mapContentTypeToResultSet.get(contentType) ; }
    private static final Map<String, ResultsFormat> mapContentTypeToResultSet = new HashMap<String, ResultsFormat>() ;
    static
    {
        mapContentTypeToResultSet.put(WebContent.contentTypeResultsXML, ResultsFormat.FMT_RS_XML) ;
        mapContentTypeToResultSet.put(WebContent.contentTypeResultsJSON, ResultsFormat.FMT_RS_JSON) ;
        mapContentTypeToResultSet.put(WebContent.contentTypeTextTSV, ResultsFormat.FMT_RS_TSV) ;
    }

    /** Response handling for SPARQL result sets. */
    public static class HttpCaptureResponseResultSet implements HttpCaptureResponse<ResultSet>
    {    
        ResultSet rs = null ;
        @Override
        public void handle(String contentType, String baseIRI, HttpResponse response) throws IOException
        {
            MediaType mt = new MediaType(contentType) ;
            ResultsFormat fmt = mapContentTypeToResultSet.get(contentType) ; // contentTypeToResultSet(contentType) ;
            InputStream in = response.getEntity().getContent() ;
            rs = ResultSetFactory.load(in, fmt) ;
            // Force reading
            rs = ResultSetFactory.copyResults(rs) ;
        }

        @Override
        public ResultSet get()
        {
            return rs ;
        }
    }
}
