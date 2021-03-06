define(["dojo/_base/declare",
        "../layout/ContentPane",
        "dijit/Dialog",
        "dojo/has"
        ], function(declare,ContentPane,Dialog,has) {
        
	return declare("dojoui.widget.Dialog",[ContentPane, Dialog._DialogBase],{

	  onDownloadEnd:function(){
	    this.inherited(arguments);
	    this.layout();
	  },
	
	  postCreate:function(){
	    this.inherited(arguments);
	    // Can't close the dialog if the user is using an iPad.
	    
	    if(this.draggable){
	      if(!has("agent-ios")){
	        this.draggable = false;
	      }
	    }
	  }
	  
	});
});