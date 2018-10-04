/*
 * Copyright (c) 2014-2018 The University Of Sheffield.
 *
 * This file is part of gateplugin-Tagger_TagMe 
 * (see https://github.com/GateNLP/gateplugin-Tagger_TagMe).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software. If not, see <http://www.gnu.org/licenses/>.
 */

package gate.tagger.tagme;

import com.fasterxml.jackson.databind.ObjectMapper;
import gate.Annotation;
import gate.AnnotationSet;
import gate.Document;
import gate.Factory;
import gate.FeatureMap;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ExecutionException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.util.GateRuntimeException;
import gate.util.InvalidOffsetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.http.client.fluent.Content;

import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.client.utils.URIBuilder;
import org.apache.log4j.Logger;

/** 
 *  PR for using the WAT service api for entity linking.
 */
@CreoleResource(name = "Tagger_WAT",
        comment = "Annotate documents using a WAT web service",
        // icon="taggerIcon.gif",
        helpURL="https://github.com/GateNLP/gateplugin-Tagger_TagMe/wiki/Tagger_WAT"
)
public class TaggerWatWS  
  extends AbstractLanguageAnalyser  {

  private static final long serialVersionUID = 5322455999996492868L;

  protected String inputASName = "";
  @RunTime
  @Optional
  @CreoleParameter(
          comment = "Input annotation set for containing annotations, default is the default set",
          defaultValue = "")
  public void setInputAnnotationSet(String ias) {
    inputASName = ias;
  }

  public String getInputAnnotationSet() {
    return inputASName;
  }
  protected String inputType = "";

  
  
  @RunTime
  @Optional
  @CreoleParameter(
          comment = "Only text covered by each containing annotation is annotated, default: annotate whole document",
          defaultValue = "")
  public void setContainingAnnotationType(String val) {
    this.containingType = val;
  }

  public String getContainingAnnotationType() {
    return containingType;
  }
  protected String containingType = "";

  protected String outputASName = "";

  @RunTime
  @Optional
  @CreoleParameter(
          comment = "Output annotation set, default is default annotation set",
          defaultValue = "")
  public void setOutputAnnotationSet(String ias) {
    outputASName = ias;
  }

  public String getOutputAnnotationSet() {
    return outputASName;
  }
  protected String outputType = "";

  @RunTime
  @Optional
  @CreoleParameter(
          comment = "The output annotation type, default is 'Lookup'",
          defaultValue = "Lookup")
  public void setOutputAnnotationType(String val) {
    this.outputType = val;
  }

  public String getOutputAnnotationType() {
    return outputType;
  }
  
  protected URL tagMeServiceUrl = null;
  
  @RunTime
  @CreoleParameter( 
          comment = "The URL of the web service to use",
          defaultValue = "https://wat.d4science.org/wat/tag/tag")
  public void setTagMeServiceUrl(URL url) {
    tagMeServiceUrl = url;
  }
  
  public URL getTagMeServiceUrl() {
    return tagMeServiceUrl;
  }
  
  @RunTime
  @CreoleParameter(
          comment = "The service auth token to use, required, no default",
          defaultValue = ""
          )
  public void setApiKey(String key) {
    apiKey = key;
  }
  public String getApiKey() {
    return apiKey;
  }
  protected String apiKey = "";
  
  
  
  @RunTime
  @CreoleParameter(
          comment = "Language code, currently supported: en,it,de",
          defaultValue = "en"
          )
  public void setLanguageCode(String code) {
    languageCode = code;
  }
  public String getLanguageCode() {
    return languageCode;
  }

  protected String languageCode = "en";
  
  @RunTime
  @CreoleParameter(
          comment = "Minimum value of rho: all annotations with a rho less than this will be ignored",
          defaultValue = "0.2"
  )
  public void setMinRho(Double value) {
    minrho = value;
  }
  public Double getMinRho() { return minrho; }
  protected double minrho = 0.2;
    
  static final Logger logger = Logger.getLogger(TaggerWatWS.class);
  
  private static final Pattern patternUrl = 
          Pattern.compile("(?iu:www\\.[\\s]+)|(?iu:https?://[^\\s]+)");
  private static final Pattern patternUser = 
          Pattern.compile("@[^\\s]+");
  private static final String patternHashTag = 
          "#([^\\s]+)";
  private static final String patternStringRT3 = "^(?iu:RT:) ";
  private static final String patternStringRT2 = "^(?iu:RT) ";
  
  
  // helper method to produce a String of n spaces
  private String nSpaces(int n) {
    char[] chars = new char[n];
    java.util.Arrays.fill(chars, ' ');
    return new String(chars);
  }
  
  @Override
  public void execute() throws ExecutionException {
    doExecute(document); 
  }

  protected void doExecute(Document theDocument) throws ExecutionException {
    interrupted = false;
    if (theDocument == null) {
      throw new ExecutionException("No document to process!");
    }
    AnnotationSet outputAS = theDocument.getAnnotations(getOutputAnnotationSet());
    if (containingType == null || containingType.isEmpty()) {
      annotateText(document,outputAS,0,document.getContent().size());
    } else {
      AnnotationSet inputAS;
      if (inputASName == null
              || inputASName.isEmpty()) {
        inputAS = theDocument.getAnnotations();
      } else {
        inputAS = theDocument.getAnnotations(inputASName);
      }      
      AnnotationSet containingAnns = inputAS.get(containingType);
      for(Annotation containingAnn : containingAnns) {
        annotateText(document,outputAS,gate.Utils.start(containingAnn),gate.Utils.end(containingAnn));
      }
    }
  }
  
  // carry out the actual annotations on the given span of text in the 
  // document.
  protected void annotateText(Document doc, AnnotationSet outputAS, long from, long to) {
    String text = "";
    try {
      text = doc.getContent().getContent(from, to).toString();
    } catch (InvalidOffsetException ex) {
      throw new GateRuntimeException("Unexpected offset exception, offsets are "+from+"/"+to);
    }
    // send the text to the service and get back the response
    // System.out.println("DEBUG: Annotating text from="+from+", to="+to+", text="+text);
    //System.out.println("Starting offset is "+from);
    
    WatAnnotation[] tagmeAnnotations = getTagMeAnnotations(text);
    for(WatAnnotation tagmeAnn : tagmeAnnotations) {
        if(tagmeAnn.rho < minrho) {
          continue;
        }
        FeatureMap fm = Factory.newFeatureMap();
        fm.put("tagMeId", tagmeAnn.id);
        fm.put("title", tagmeAnn.title);
        fm.put("rho", tagmeAnn.rho);
        fm.put("spot", tagmeAnn.spot);
        if (tagmeAnn.title == null) {
          throw new GateRuntimeException("Odd: got a null title from the TagMe service" + tagmeAnn);
        } else {
          fm.put("inst", "http://dbpedia.org/resource/" + recodeForDbp38(tagmeAnn.title));
        }
        try {
          gate.Utils.addAnn(outputAS, from + tagmeAnn.start, from + tagmeAnn.end, getOutputAnnotationType(), fm);
        } catch (Exception ex) {
          System.err.println("Got an exception in document " + doc.getName() + ": " + ex.getLocalizedMessage());
          ex.printStackTrace(System.err);
          System.err.println("from=" + from + ", to=" + to + " TagMeAnn=" + tagmeAnn);
        }
      
    }
  }
    
  protected WatAnnotation[] getTagMeAnnotations(String text) {
    String str = retrieveServerResponse(text);
    return convertStringToTagMeAnnotations02(str);
  }
  
  protected String retrieveServerResponse(String text) {
    URI uri;
    try {
      uri = new URIBuilder(getTagMeServiceUrl().toURI())
              .setParameter("text", text)
              .setParameter("gcube-token",getApiKey())
              .setParameter("lang",getLanguageCode())
              .build();
    } catch (URISyntaxException ex) {
      throw new GateRuntimeException("Could not create URI for the request",ex);
    }
        
    //System.err.println("DEBUG: WAT URL="+uri);
    Request req = Request.Get(uri);
    
    Response res = null;
    try {
      res = req.execute();
    } catch (Exception ex) {
      throw new GateRuntimeException("Problem executing HTTP request: "+req,ex);
    } 
    Content cont = null;
    try {
      cont = res.returnContent();
    } catch (Exception ex) {
      throw new GateRuntimeException("Problem getting HTTP response content: "+res,ex);
    } 
    String ret = cont.asString();
    logger.debug("WAT server response "+ret);
    return ret;
  }
  
  
  // second version of the conversion code: this uses classes to represent
  // the format of the JSON we expect and should be less clumsy, but may 
  // be slower
  protected WatAnnotation[] convertStringToTagMeAnnotations02(String str) {
    // parse the String as JSON
    ObjectMapper mapper = new ObjectMapper();
    WatJsonData data = null;
    try {
      data = mapper.readValue(str, WatJsonData.class);
    } catch (Exception ex) {
      throw new GateRuntimeException("Problem parsing the returned JSON as TagMeJsonData "+str,ex);
    }
    return data.annotations;
  }
  
  
  protected static class WatAnnotation {
    public int id = 0;    
    public String title = "";
    public int start = 0;
    public int end = 0;    
    public double rho = 0.0;
    public String spot = "";
    @Override 
    public String toString() {
      return "WatAnnotation(id="+id+",rho="+rho+",title="+title+",offset="+start+", end="+end+")";
    }
  }
  
  protected static class WatJsonData {
    public Object metrics = ""; // we do not care about this one
    public WatAnnotation[] annotations = null;
  }
  
  // UTILITY methods
  
  public static String recodeForDbp38(String uriString) {
    String ret;
    URI uri = null;
    if(uriString.startsWith("http://") || uriString.startsWith("https://")) {
      // First try to parse the string as an URI so that any superfluous 
      // percent-encodings can get decoded later
      try {
        uri = new URI(uriString);
      } catch(Exception ex) {
        throw new GateRuntimeException("Could not parse URI "+uriString,ex);
      }
      // now use this constructor to-recode only the necessary parts
      try {
        String path = uri.getPath();
        path = path.trim();
        path = path.replaceAll(" +","_");
        uri = new URI(uri.getScheme(),null,uri.getHost(),-1,path,uri.getQuery(),uri.getFragment());
      } catch(Exception ex) {
        throw new GateRuntimeException("Could not re-construct URI: "+uri);
      }
      ret = uri.toString();
    } else {
      if(uriString.contains("\\u")) {
        uriString = StringEscapeUtils.unescapeJava(uriString);
      }
      uriString = uriString.trim();
      uriString = uriString.replaceAll(" +", "_");
      // We need to %-encode colons, otherwise the getPath() method will return
      // null ...
      uriString = uriString.replaceAll(":","%3A");
      try {
        uri = new URI(uriString);
        // decode and prepare for minimal percent encoding
        uriString = uri.getPath();
      } catch (URISyntaxException ex) {
        // do nothing: the uriString must already be ready for percent-encoding
      }
      uriString = uriString.replaceAll(" +", "_");
      try {        
        uri = new URI(null,null,null,-1,"/"+uriString,null,null);
      } catch(Exception ex) {
        throw new GateRuntimeException("Could not re-construct URI part: "+uriString);
      }
      ret = uri.toString().substring(1);
    }
    return ret;
  }
  
  
  

} // class TaggerTagMeWS
