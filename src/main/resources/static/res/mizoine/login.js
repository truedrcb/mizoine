$(document).ready(function () {
	$('#inputUsername').focus();
	
	var sPageURL = window.location.search.substring(1);
	if (sPageURL == 'error') {
		$('#header').text('Login error');
		$('input').addClass('is-invalid');
	} else if (sPageURL == 'logout') {
		$('#header').text('Logout successful');
	}
});
