$(document).ready(function () {
	var csrfToken = $("meta[name='_csrf']").attr("content");
	var csrfHeader = $("meta[name='_csrf_header']").attr("content");

	$( "span.unstaged-file" )
	.addClass('btn btn-outline-primary btn-sm mr-2')
	.html(
	function () {
		return "<i class='fas fa-plus-square' title='Stage " + $(this).attr("git-path") + "'> </i> ";
	})
	.click( function () {
		console.log("Stage " + $(this).attr("git-path"));
		
		$.ajax({
			type: "POST",
			url: "stage",
			dataType: "text",
			data: {
				filePath: $(this).attr("git-path")
			},
			beforeSend: function(xhr) {
				xhr.setRequestHeader(csrfHeader, csrfToken);
			},
			success: 
				function( descriptionResponse ) {
				location.reload();
			},
			error: function( jqXHR, textStatus, errorThrown) {
				mizAlert("Stage failed: " + textStatus + " - " + errorThrown, "danger");
			}
		});

	});
});