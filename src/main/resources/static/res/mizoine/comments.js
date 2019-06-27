$(document).ready(function () {

	var csrfToken = $("meta[name='_csrf']").attr("content");
	var csrfHeader = $("meta[name='_csrf_header']").attr("content");

//	$("#uploadFilesInput").fileinput({
//		theme: "fa",
//		uploadUrl: "upload",
//		hideThumbnailContent: false // hide image, pdf, text or other content in the thumbnail preview
//	});
//
//	$('#uploadFilesInput').on('filepreupload', function(event, data, previewId, index) {
//		var xhr = data.jqXHR;
//		xhr.setRequestHeader(csrfHeader, csrfToken);
//		console.log('File pre upload triggered');
//	});
	$('#newCommentArea').on('shown.bs.collapse', function () {
		$('#newCommentMarkdownTextarea').focus();
		console.debug('Focused');
	});
});
