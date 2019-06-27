function getCookie(cname) {
    var name = cname + "=";
    var decodedCookie = decodeURIComponent(document.cookie);
    var ca = decodedCookie.split(';');
    for(var i = 0; i <ca.length; i++) {
        var c = ca[i];
        while (c.charAt(0) == ' ') {
            c = c.substring(1);
        }
        if (c.indexOf(name) == 0) {
            return c.substring(name.length, c.length);
        }
    }
    return "";
}

// Required for login form
$(document).ready(function () {
	$('#inputUsername').focus();
	$('input[name="_csrf"]').val(getCookie("XSRF-TOKEN"));
});


function setXSRF(xhr) {
	xhr.setRequestHeader("X-XSRF-TOKEN", getCookie("XSRF-TOKEN"));
}