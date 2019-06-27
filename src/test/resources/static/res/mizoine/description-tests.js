QUnit.test( "hello test", function( assert ) {
	assert.ok( 1 == "1", "Passed!" );
});


//QUnit.test( "Convert b within div", function( assert ) {
//	var md = htmlToMarkdown("<div>Hello <b>markdown</b></div>")
//	assert.equal( md, "Hello **markdown**");
//});
//
//QUnit.test( "Convert img", function( assert ) {
//	var md;
//	md = htmlToMarkdown("<img src=\"https://bitbucket.org/truedrcb/mizoine/avatar/32/\" style=\"display: block;\" alt=\"Mizoine alt\" title=\"Mizoine\" border=\"0\" height=\"28\" width=\"28\">")
//	assert.equal( md, "![Mizoine](https://bitbucket.org/truedrcb/mizoine/avatar/32/)");
//	md = htmlToMarkdown("<img src=\"https://bitbucket.org/truedrcb/mizoine/avatar/32/\" style=\"display: block;\" alt=\"Mizoine alt\" border=\"0\" height=\"28\" width=\"28\">")
//	assert.equal( md, "![Mizoine alt](https://bitbucket.org/truedrcb/mizoine/avatar/32/)");
//	md = htmlToMarkdown("<img src=\"https://bitbucket.org/truedrcb/mizoine/avatar/32/\" style=\"display: block;\" title=\"Mizoine\" border=\"0\" height=\"28\" width=\"28\">")
//	assert.equal( md, "![Mizoine](https://bitbucket.org/truedrcb/mizoine/avatar/32/)");
//});
//
//QUnit.test( "Convert skipped img", function( assert ) {
//	var md;
//	md = htmlToMarkdown("<img src=\"https://bitbucket.org/truedrcb/mizoine/spacer.gif\" style=\"display: block;\" border=\"0\" height=\"28\" width=\"28\">")
//	assert.equal( md, "");
//	md = htmlToMarkdown("<img src=\"https://bitbucket.org/truedrcb/mizoine/something.gif\" style=\"display: block;\" border=\"0\" height=\"28\" width=\"28\">")
//	assert.equal( md, "![](https://bitbucket.org/truedrcb/mizoine/something.gif)");
//	md = htmlToMarkdown("<img src=\"https://bitbucket.org/truedrcb/mizoine/tracking/test/\" style=\"display: block;\">")
//	assert.equal( md, "");
//	md = htmlToMarkdown("<img src=\"https://bitbucket.org/truedrcb/mizoine/racking/test/\" style=\"display: block;\">")
//	assert.equal( md, "![](https://bitbucket.org/truedrcb/mizoine/racking/test/)");
//});
//
//
//QUnit.test( "Convert simple a", function( assert ) {
//	var md = htmlToMarkdown("<a href=\"https://bitbucket.org/truedrcb/mizoine\" target=\"_blank\">Mizoine</a>")
//	assert.equal( md, "[Mizoine](https://bitbucket.org/truedrcb/mizoine)");
//});
//
//QUnit.test( "Convert simple a within form", function( assert ) {
//	var md = htmlToMarkdown("<form method='post' action='/something/'><a href=\"https://bitbucket.org/truedrcb/mizoine\" target=\"_blank\">Mizoine</a></form>")
//	assert.equal( md, "[Mizoine](https://bitbucket.org/truedrcb/mizoine)");
//});
//
//
//QUnit.test( "Convert img within a", function( assert ) {
//	var md;
//	md = htmlToMarkdown("<a href=\"https://bitbucket.org/truedrcb/mizoine\" target=\"_blank\"> <img src=\"https://bitbucket.org/truedrcb/mizoine/avatar/32/\" style=\"display: block;\" alt=\"Mizoine alt\" title=\"Mizoine\" border=\"0\" height=\"28\" width=\"28\"> </a>")
//	assert.equal( md, "[Mizoine](https://bitbucket.org/truedrcb/mizoine)");
//	md = htmlToMarkdown("<a href=\"https://bitbucket.org/truedrcb/mizoine\" target=\"_blank\"> <img src=\"https://bitbucket.org/truedrcb/mizoine/avatar/32/\" style=\"display: block;\" alt=\"Mizoine alt\" border=\"0\" height=\"28\" width=\"28\"> </a>")
//	assert.equal( md, "[Mizoine alt](https://bitbucket.org/truedrcb/mizoine)");
//	md = htmlToMarkdown("<a href=\"https://bitbucket.org/truedrcb/mizoine\" target=\"_blank\"> <img src=\"https://bitbucket.org/truedrcb/mizoine/avatar/32/\" style=\"display: block;\" title=\"Mizoine\" border=\"0\" height=\"28\" width=\"28\"> </a>")
//	assert.equal( md, "[Mizoine](https://bitbucket.org/truedrcb/mizoine)");
//});
//
