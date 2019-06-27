$(document).ready(function () {

	var csrfToken = $("meta[name='_csrf']").attr("content");
	var csrfHeader = $("meta[name='_csrf_header']").attr("content");
	
	var fileUploadError = false;

	$("#uploadFilesInput").fileinput({
		theme: "fa",
		uploadUrl: "upload",
		hideThumbnailContent: true // hide image, pdf, text or other content in the thumbnail preview
	});

	$('#uploadFilesInput').on('filepreupload', function(event, data, previewId, index) {
		var xhr = data.jqXHR;
		xhr.setRequestHeader(csrfHeader, csrfToken);
		console.log('File pre upload triggered');
	});

	$('#uploadFilesInput').on('filebatchuploadcomplete', function(event, data, previewId, index) {
		console.log('File batch upload complete');
		if (!fileUploadError) {
			location.reload();
		}
	});
	$('#uploadFilesInput').on('fileuploaderror', function(event, data, msg) {
		fileUploadError = true;
		console.log('File upload error');
		console.log(data);
		console.log(msg);
		
		mizAlert("Upload failed: " + msg, "danger");
	});
});
