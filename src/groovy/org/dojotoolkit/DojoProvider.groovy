package org.dojotoolkit
import org.codehaus.groovy.grails.plugins.web.taglib.JavascriptProvider

/**
 * Dojo implmentation of JavascriptProvider.
 *
 */
class DojoProvider implements JavascriptProvider {


  
  /**
   * This will convert remoteXXX tag params to a dojo friendly format. Most params should be passed as a regular map but
   * there are some exceptions.
   *
   * The remoteField tag passes the params as a GString. So we deal with that differently.
   *
   * If the user wishes to pass in javascript values as part of the params attribute, the best way is to pass a string
   * that is structured like this: params="'myVar1':myJsValue, 'myVar2':myJsValue2". 
   *
   * @param params
   * @return String
   */
  private def convertParamsToDojoJson(params) {
    def paramString = ""
    if(params instanceof Map){
      def paramList = []      
      params.each{k, v ->
        if(v instanceof String){
          v = "'${v}'"
        }
        paramList.add("'${k}':${v}")
      }
      paramString = "{${paramList.join(",")} }"
    }
    // Used ONLY for <g:remoteField>
    else if(params instanceof GString){
      //<g:remoteField> passes in the param like this: 'color='+this.value. So lets convert to 'color':this.value
      paramString = params.replaceAll(/\+/,":").replaceAll(/\=/,"")
      paramString = "{${paramString}}"
    }
    else if(params instanceof String){
      paramString = "{${params}}"      
    }
    return paramString
  }




   /**
    * Will generate the Ajax javascript to be applied to the html dom node.
    *
    * @param props
    * @return
    */
  def private getDojoXhrString(Map props){
    def method              = props?.method ?: "Get"
    def url                 = props?.url ?: ""
    def parameters          = props?.parameters ?: ""
    def updateDomElem       = props?.updateDomElem ?: ""
    def errorDomElem        = props?.errorDomElem ?: ""
    def async               = props?.async ?: true
    def onSuccess           = (props?.onSuccess) ? "${props?.onSuccess};" : ""
    def onFailure           = (props?.onFailure) ? "${props?.onFailure};" : ""
    def onLoading           = (props?.onLoading) ? "${props?.onLoading};" : ""
    def onLoaded            = (props?.onLoaded) ? "${props?.onLoaded};" : ""
    def onComplete          = (props?.onComplete) ? "${props?.onComplete};" : ""
    def statusCodeHandlers  = props?.statusCodeHandlers ?: ""
    def formName            = props?.formName ?: ""
    def preventCache        = props?.preventCache ?: false

    def dojoString =
    "${onLoading}" +
    "dojo.xhr('${method}',{" +
        (!async ? "sync:${async}, ": "") +
        (parameters?.length() ? "content:${parameters}, " : "") +
        (formName?.length() ? "form:${formName}, " : "") +
        "preventCache:${preventCache}, " +
        "url:'${url}', " +
        "load:function(response){" +
            "dojo.attr(dojo.byId('${updateDomElem}'),'innerHTML',response); " +
            "if(dojo.parser){dojo.parser.parse(dojo.byId('${updateDomElem}'))} " +
            "${onLoaded} " +
            "${onSuccess} " +
        "}, " +
        "handle:function(response,ioargs){" +
          "${statusCodeHandlers}" +
          "${onComplete} " +          
        "}, " +
        "error:function(error,ioargs){" +
          "dojo.attr(dojo.byId('${errorDomElem}'),'innerHTML',ioargs.xhr.responseText); " +
          "${onLoaded} " +
          "${onFailure} " +
        "} " +
    "});"
    return dojoString
  }



  def doRemoteFunction(taglib, attrs, out) {
    def allowedMethods = ["Get", "Post", "Put", "Delete"]
    def method = "Get"
    if (attrs.method) {
      def tmpMethod = attrs.method[0].toUpperCase() + attrs.method.substring(1).toLowerCase()
      if (allowedMethods.contains(tmpMethod)) {
        method = tmpMethod
      }
    }
    def parameters    = convertParamsToDojoJson(attrs?.params)
    attrs.remove('params') // to not duplicate these params on the url.

    def url           = (attrs?.url instanceof String) ? attrs?.url : taglib.createLink(attrs)    
    def updateDomElem = (attrs?.update instanceof Map ? attrs.update.success : attrs.update)
    def errorDomElem  = (attrs?.update instanceof Map ? attrs.update.failure : attrs.update)
    def sync          = attrs.sync && attrs.sync == "true" ?: "false"
    def onSuccess     = attrs?.onSuccess
    def onFailure     = attrs?.onFailure
    def onLoading     = attrs?.onLoading
    def onLoaded      = attrs?.onLoaded
    def onComplete    = attrs?.onComplete
    def formName      = attrs?.formName
    def preventCache  = attrs?.preventCache
    ['method','sync','params','options','onSuccess','onFailure','onLoading','onLoaded','onComplete','preventCache'].each{attrs.remove(it)}

    // Http Status Code Handlers
    def statusCodes = attrs.findAll { k,v ->
      k ==~ /on(\p{Upper}|\d){1}\w+/
    }
    def statusCodeHandlers = ""
    statusCodes.each{k,v ->
      statusCodeHandlers+="if(ioargs.xhr.status===${k.replaceAll(/on/,'')}){${v}}; "
      attrs.remove(k)
    }

    // Generate XHR Output
    out << getDojoXhrString([
            method: method,
            url: url,
            parameters: parameters,
            updateDomElem: updateDomElem,
            errorDomElem: errorDomElem,
            async: sync,
            onSuccess: onSuccess,
            onFailure: onFailure,
            onLoading: onLoading,
            onLoaded: onLoaded,
            onComplete: onComplete,
            statusCodeHandlers: statusCodeHandlers,
            formName: formName,
            preventCache: preventCache])
  }




  def prepareAjaxForm(attrs) {
    if(!attrs.method){
      attrs.method = "Post"
    }

    if(attrs?.forSubmitTag){
      // This is for <g:submitToRemote>
      attrs.formName = "this.form"
    }
    else{
      // This is for <g:remoteForm>
      attrs.formName = "'${attrs.name}'"
    }
  }
}