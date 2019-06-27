$(document).ready(function () {

	var csrfToken = $("meta[name='_csrf']").attr("content");
	var csrfHeader = $("meta[name='_csrf_header']").attr("content");

	$('#newIssueArea').on('shown.bs.collapse', function () {
		$('#newIssueTitleInput').focus();
		console.debug('Focused');
	});
});
