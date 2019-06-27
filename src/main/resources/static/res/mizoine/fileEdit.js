$(document).ready(function () {

	var csrfToken = $("meta[name='_csrf']").attr("content");
	var csrfHeader = $("meta[name='_csrf_header']").attr("content");

	var editor = ace.edit("fileTextEditor");
	editor.setShowPrintMargin(false);
	editor.setTheme("ace/theme/clouds");
	//editor.renderer.setShowGutter(false);
	editor.getSession().setUseWrapMode(true);
	
	var fileType = $("#fileEditTextarea").attr("mizfiletype");

	if (fileType != null) {
		editor.getSession().setMode("ace/mode/" + fileType)
	}
	editor.setValue($('#fileEditTextarea').val())
	editor.resize();
	editor.focus();

	$("#contentEditForm").submit(function(event){
		$('#fileEditTextarea').val(editor.getValue())
	});
	
	//$('#fileEditTextarea').focus();
});
