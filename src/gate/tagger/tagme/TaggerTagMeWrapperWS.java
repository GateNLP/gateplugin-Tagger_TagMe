/*
 *  TaggerTagMeWS.java
 *
 * Copyright (c) 2000-2012, The University of Sheffield.
 *
 * This file is part of GATE (see http://gate.ac.uk/), and is free
 * software, licenced under the GNU Library General Public License,
 * Version 3, 29 June 2007.
 *
 * A copy of this licence is included in the distribution in the file
 * licence.html, and is also available at http://gate.ac.uk/gate/licence.html.
 *
 *  johann, 7/5/2014
 *
 * For details on the configuration options, see the user guide:
 * http://gate.ac.uk/cgi-bin/userguide/sec:creole-model:config
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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.http.Consts;
import org.apache.http.client.fluent.Content;
import org.apache.http.client.fluent.Form;

import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.log4j.Logger;

/** 
 * 
 */
@CreoleResource(name = "Tagger_TagMeWrapper", 
        comment = "Annotate documents using a custom endpoint wrapping TagMe web service"
    // icon="taggerIcon.gif",
    )
public class TaggerTagMeWrapperWS extends AbstractLanguageAnalyser {

  public static final long serialVersionUID = 1L;

  protected String inputASName = "";

  @RunTime
  @Optional
  @CreoleParameter(comment = "Input annotation set for containing annotations, default is the default set", defaultValue = "")
  public void setInputAnnotationSet(String ias) {
    inputASName = ias;
  }

  public String getInputAnnotationSet() {
    return inputASName;
  }

  protected String inputType = "";

  @RunTime
  @Optional
  @CreoleParameter(comment = "Only text covered by each containing annotation is annotated, default: annotate whole document", defaultValue = "")
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
  @CreoleParameter(comment = "Output annotation set, default is default annotation set", defaultValue = "")
  public void setOutputAnnotationSet(String ias) {
    outputASName = ias;
  }

  public String getOutputAnnotationSet() {
    return outputASName;
  }

  protected String outputType = "";

  @RunTime
  @Optional
  @CreoleParameter(comment = "The output annotation type, default is 'Lookup'", defaultValue = "Lookup")
  public void setOutputAnnotationType(String val) {
    this.outputType = val;
  }

  public String getOutputAnnotationType() {
    return outputType;
  }

  protected URL tagMeServiceUrl = null;

  @RunTime
  @CreoleParameter(comment = "The URL of the web service to use", defaultValue = "http://ferrax2.itc.unipi.it:9002/about")

  public void setTagMeServiceUrl(URL url) {
    tagMeServiceUrl = url;
  }

  public URL getTagMeServiceUrl() {
    return tagMeServiceUrl;
  }

  // @RunTime
  // @CreoleParameter(
  // comment = "Should be true if the text is a tweet or very short",
  // defaultValue = "false"
  // )
  // public void setIsTweet(Boolean flag) {
  // isTweet = flag;
  // }
  // public Boolean getIsTweet() {
  // return isTweet;
  // }
  // protected Boolean isTweet = false;

  // @RunTime
  // @CreoleParameter(
  // comment = "Language code, currently supported: en,it,de",
  // defaultValue = "en"
  // )
  // public void setLanguageCode(String code) {
  // languageCode = code;
  // }
  // public String getLanguageCode() {
  // return languageCode;
  // }

  // protected String languageCode = "en";
  protected Integer max_num_entities = 30;

  @RunTime
  @CreoleParameter(comment = "Max num entities: Maximum number of different annotations in the result", defaultValue = "30")
  public void setMaxNumEntities(Integer value) {
    max_num_entities = value;
  }

  public Integer getMaxNumEntities() {
    return max_num_entities;
  }

  protected Integer minCount = 1;

  @RunTime
  @CreoleParameter(comment = "Minimum number of times an entity should appear in text to be considered valid", defaultValue = "1")
  public void setMinCount(Integer value) {
    minCount = value;
  }

  public Integer getMinCount() {
    return minCount;
  }

  static final Logger logger = Logger.getLogger(TaggerTagMeWrapperWS.class);

  private static final Pattern patternUrl = Pattern.compile("(?iu:www\\.[\\s]+)|(?iu:https?://[^\\s]+)");
  private static final Pattern patternUser = Pattern.compile("@[^\\s]+");
  private static final String patternHashTag = "#([^\\s]+)";
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
      annotateText(document, outputAS, 0, document.getContent().size());
    } else {
      AnnotationSet inputAS = null;
      if (inputASName == null || inputASName.isEmpty()) {
        inputAS = theDocument.getAnnotations();
      } else {
        inputAS = theDocument.getAnnotations(inputASName);
      }
      AnnotationSet containingAnns = inputAS.get(containingType);
      for (Annotation containingAnn : containingAnns) {
        annotateText(document, outputAS, gate.Utils.start(containingAnn), gate.Utils.end(containingAnn));
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
      throw new GateRuntimeException("Unexpected offset exception, offsets are " + from + "/" + to);
    }
    // send the text to the service and get back the response
    // System.out.println("Annotating text: "+text);
    // System.out.println("Starting offset is "+from);

    TagMeAnnotation[] tagmeAnnotations = getTagMeAnnotations(text);
    for (TagMeAnnotation tagmeAnn : tagmeAnnotations) {
      for (TagMeAnnotationOccurrence tagmeAnnOcc : tagmeAnn.occurrences) {
        FeatureMap fm = Factory.newFeatureMap();
        // per ogni count devo creare e aggiungere una mappa
        fm.put("tagMeId", tagmeAnn.entity_id);
        fm.put("title", tagmeAnn.entity_name);
        fm.put("rho", tagmeAnn.score);
        fm.put("link_probability", tagmeAnn.link_probability);
        fm.put("pr_score", tagmeAnn.pr_score);

        if (tagmeAnn.entity_name == null) {
          throw new GateRuntimeException("Odd: got a null title from the TagMe service" + tagmeAnn);
        } else {
          fm.put("inst", "http://dbpedia.org/resource/" + recodeForDbp38(tagmeAnn.entity_name));
        }
        try {
          gate.Utils.addAnn(outputAS, from + tagmeAnnOcc.begin, from + tagmeAnnOcc.end, getOutputAnnotationType(), fm);
        } catch (Exception ex) {
          System.err.println("Got an exception in document " + doc.getName() + ": " + ex.getLocalizedMessage());
          ex.printStackTrace(System.err);
          System.err.println("from=" + from + ", to=" + to + " TagMeAnn=" + tagmeAnn);
        }
      }
    }
  }

  protected TagMeAnnotation[] getTagMeAnnotations(String text) {
    String str = retrieveServerResponse(text);
    return convertStringToTagMeAnnotations02(str);
  }

  protected String retrieveServerResponse(String text) {
    Request req = Request.Post(getTagMeServiceUrl().toString());

    req.addHeader("Content-Type", "application/x-www-form-urlencoded");
    req.bodyForm(
        Form.form().add("text", text).add("min_count", getMinCount().toString()).add("max_num_entities", getMaxNumEntities().toString()).build(),
        Consts.UTF_8);
    logger.debug("Request is " + req);
    Response res = null;
    try {
      res = req.execute();
    } catch (Exception ex) {
      throw new GateRuntimeException("Problem executing HTTP request: " + req, ex);
    }
    Content cont = null;
    try {
      cont = res.returnContent();
    } catch (Exception ex) {
      throw new GateRuntimeException("Problem getting HTTP response content: " + res, ex);
    }
    String ret = cont.asString();
    logger.debug("TagMe server response " + ret);
    return ret;
  }

  // second version of the conversion code: this uses classes to represent
  // the format of the JSON we expect and should be less clumsy, but may
  // be slower
  protected TagMeAnnotation[] convertStringToTagMeAnnotations02(String str) {
    List<TagMeAnnotation> tagmeAnnotations = new ArrayList<TagMeAnnotation>();
    // parse the String as JSON
    ObjectMapper mapper = new ObjectMapper();
    TagMeJsonData data = null;
    try {
      data = mapper.readValue(str, TagMeJsonData.class);
    } catch (Exception ex) {
      throw new GateRuntimeException("Problem parsing the returned JSON as TagMeJsonData " + str, ex);
    }
    return data.result.topics;
  }

  protected static class TagMeAnnotationOccurrence {
    public int begin = 0;
    public int end = 0;
  } 
  
  protected static class TagMeAnnotation {
    public int entity_id = 0;
    public int count = 0;
    public String entity_name = "";
    public double link_probability = 0.0;
    public double pr_score = 0.0;
    public double score = 0.0;
    public TagMeAnnotationOccurrence[] occurrences = null;

    @Override
    public String toString() {
      return "TagMeAnnotation(id=" + entity_id + ",rho=" + score + ",title=" + entity_name + ")";
    }
  }

  protected static class TagMeResult {
    public TagMeAnnotation[] topics = null;
  }

  protected static class TagMeJsonData {
    public double time = 0.0;
    public boolean success = false;
    public TagMeResult result = null;
  }

  // UTILITY methods

  public static String recodeForDbp38(String uriString) {
    String ret;
    URI uri = null;
    if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
      // First try to parse the string as an URI so that any superfluous
      // percent-encodings can get decoded later
      try {
        uri = new URI(uriString);
      } catch (Exception ex) {
        throw new GateRuntimeException("Could not parse URI " + uriString, ex);
      }
      // now use this constructor to-recode only the necessary parts
      try {
        String path = uri.getPath();
        path = path.trim();
        path = path.replaceAll(" +", "_");
        uri = new URI(uri.getScheme(), null, uri.getHost(), -1, path, uri.getQuery(), uri.getFragment());
      } catch (Exception ex) {
        throw new GateRuntimeException("Could not re-construct URI: " + uri);
      }
      ret = uri.toString();
    } else {
      if (uriString.contains("\\u")) {
        uriString = StringEscapeUtils.unescapeJava(uriString);
      }
      uriString = uriString.trim();
      uriString = uriString.replaceAll(" +", "_");
      // We need to %-encode colons, otherwise the getPath() method will return
      // null ...
      uriString = uriString.replaceAll(":", "%3A");
      try {
        uri = new URI(uriString);
        // decode and prepare for minimal percent encoding
        uriString = uri.getPath();
      } catch (URISyntaxException ex) {
        // do nothing: the uriString must already be ready for percent-encoding
      }
      uriString = uriString.replaceAll(" +", "_");
      try {
        uri = new URI(null, null, null, -1, "/" + uriString, null, null);
      } catch (Exception ex) {
        throw new GateRuntimeException("Could not re-construct URI part: " + uriString);
      }
      ret = uri.toString().substring(1);
    }
    return ret;
  }

} // class TaggerTagMeWS
