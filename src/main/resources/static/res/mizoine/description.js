$(document).ready(function () {

	$("#descriptionEdit").hide();

	var editor = ace.edit("descriptionMarkdownEditor");
	editor.setShowPrintMargin(false);
	editor.setTheme("ace/theme/clouds");
	//editor.renderer.setShowGutter(false);
	editor.getSession().setMode("ace/mode/markdown")
	editor.getSession().setUseWrapMode(true);
	editor.resize();
	
	function updateDescription(descriptionResponse) {
		console.log(descriptionResponse);

		$('#descriptionHtml').html(descriptionResponse.html);
		editor.setValue(descriptionResponse.markdown);

		$('img.miz-md-thumbnail').each(function () {
			var currentImage = $(this);
			currentImage.wrap(
			"<a href='" + currentImage.attr("src") 
				+ "' data-toggle='lightbox' data-title='" 
				+ currentImage.attr("title") + "'> </a>");
		});
		
		$('pre code').each(function(i, block) {
			hljs.highlightBlock(block);
		});
	}
	
	function closeEditor() {
		$("#descriptionEdit").hide();
		$("#descriptionView").show();
	}

	function refreshDescription() {
		$.ajax({
			type: "GET",
			url: "description",
			dataType: "json",
			success: 
				function( descriptionResponse ) {
				updateDescription(descriptionResponse);
				//editor.resize();
			}
		});
	}

	var csrfToken = $("meta[name='_csrf']").attr("content");
	var csrfHeader = $("meta[name='_csrf_header']").attr("content");

	$("#descriptionForm").submit(function(event){
		// cancels the form submission
		event.preventDefault();

		$.ajax({
			type: "POST",
			url: "description",
			dataType: "json",
			data: {
				description: editor.getValue()
			},
			beforeSend: function(xhr) {
				xhr.setRequestHeader(csrfHeader, csrfToken);
			},
			success: 
				function( descriptionResponse ) {
				updateDescription(descriptionResponse);
				closeEditor();
				mizAlert("Description updated", "success");
			},
			error: function( jqXHR, textStatus, errorThrown) {
				mizAlert("Update failed: " + textStatus + " - " + errorThrown, "danger");
			}
		});
	});

	$("#descToolUndo").click(function () {
		editor.undo();
		editor.focus();
	});

	$("#descToolRedo").click(function () {
		editor.redo();
		editor.focus();
	});

	$("#descToolReload").click(refreshDescription);

	function indentSelectionWith(indentText) {
		var r1 = editor.selection.getSelectionAnchor().row;
		var r2 = editor.selection.getSelectionLead().row;
		if (r1 > r2) {
			var flip = r2;
			r2 = r1;
			r1 = flip;
		}
		editor.session.indentRows(r1, r2, indentText);
		editor.clearSelection();
		editor.focus();
	}
	
	$("#descToolHr").click(function () {
		editor.insert("\n\n---\n")
		editor.focus();
	});

	$("#descToolH1").click(function () {
		indentSelectionWith("# ");
	});

	$("#descToolH2").click(function () {
		indentSelectionWith("## ");
	});

	$("#descToolH3").click(function () {
		indentSelectionWith("### ");
	});

	$("#descToolH4").click(function () {
		indentSelectionWith("#### ");
	});

	$("#descToolH5").click(function () {
		indentSelectionWith("##### ");
	});
	
	function navLeftSelect(leftCount, selectCount) {
		editor.navigateLeft(leftCount);
		for (var i = 0; i < selectCount; i++) {
			editor.selection.selectRight();
		}
		editor.focus();
	}
	
	function insertTable(columns) {
		editor.insert("\n");
		for (var i = 0; i < columns; i++) {
			editor.insert("| Col" + (i + 1) + " ");
		}
		editor.insert("|\n");
		for (var i = 0; i < columns; i++) {
			editor.insert("| ---- ");
		}
		editor.insert("|\n");
		for (var i = 0; i < columns; i++) {
			editor.insert("| text ");
		}
		editor.insert("|\n");
		navLeftSelect(7, 4);
	}

	$("#descToolTable2").click(function () {
		insertTable(2);
	});

	$("#descToolTable3").click(function () {
		insertTable(3);
	});

	$("#descToolTable4").click(function () {
		insertTable(4);
	});

	$("#descToolTable5").click(function () {
		insertTable(5);
	});
	
	function putAroundSelection(prefix, suffix) {
		if (editor.selection.isMultiLine()) {
			editor.clearSelection();
		}
		var txt = editor.getCopyText();
		if (txt == "") {
			editor.insert(prefix + "text" + suffix);
			navLeftSelect(4 + suffix.length, 4);
		} else {
			editor.insert(prefix + txt+ suffix);
		}
		editor.focus();
	}

	$("#descToolBold").click(function () {
		putAroundSelection("**", "**");
	});

	$("#descToolItalic").click(function () {
		putAroundSelection("*", "*");
	});
	
	$("#descToolStrike").click(function () {
		putAroundSelection("~~", "~~");
	});
	
	$("#descToolUnderline").click(function () {
		putAroundSelection("<u>", "</u>");
	});
	
	$("#descToolKbd").click(function () {
		putAroundSelection("<kbd>", "</kbd>");
	});

	$("#descToolLinkSample").click(function () {
		if (editor.selection.isMultiLine()) {
			editor.clearSelection();
		}
		var txt = editor.getCopyText();
		if (txt == "") {
			editor.insert("[Link text](http://url)");
			navLeftSelect(11, 10);
		} else if (txt.startsWith("http") || txt.startsWith("/") ) { 
			editor.insert("[Link text](" + txt + ")");
			navLeftSelect(txt.length + 12, 9);
		} else {
			editor.insert("[" + txt + "](http://url)");
			navLeftSelect(11, 10);
		}
	});

	$("#descToolImageSample").click(function () {
		if (editor.selection.isMultiLine()) {
			editor.clearSelection();
		}
		var txt = editor.getCopyText();
		if (txt == "") {
			editor.insert("![Image title](http://url)");
			navLeftSelect(11, 10);
		} else if (txt.startsWith("http") || txt.startsWith("/") ) {
			editor.insert("[Image title](" + txt + ")");
			navLeftSelect(txt.length + 14, 11);
		} else {
			editor.insert("![" + txt + "](http://url)");
			navLeftSelect(11, 10);
		}
	});
	
	
	$("#descToolUList").click(function () {
		indentSelectionWith("- ");
	});

	$("#descToolOList").click(function () {
		indentSelectionWith("1. ");
	});

	$("#descToolQuote").click(function () {
		indentSelectionWith("> ");
	});

	$("#descToolCode").click(function () {
		var txt = editor.getCopyText();
		if (txt == "") {
			editor.insert("`code`");
			navLeftSelect(5, 4);
		} else if (editor.selection.isMultiLine()) {
			editor.insert("\n```\n" + txt + "\n```\n");
		} else {
			editor.insert("`" + txt+ "`");
		}
		editor.focus();
	});
	
	$('#descriptionEditButton').click( function () {
		var h = $("#descriptionHtml").height();
		
		$("#descriptionEdit").show();
		$("#descriptionView").hide();

		$("#descriptionMarkdown").height(h);
		
		editor.resize();
		editor.focus();
	});

	$('#descriptionEditCancelButton').click( closeEditor );
	
	
	$('input[type=radio][name=descPasteType]').change( function () {
		editor.focus();
	});
	
	// https://stackoverflow.com/questions/2176861/javascript-get-clipboard-data-on-paste-event-cross-browser
	editor.on('paste', function (o) {
		console.log("Editor paste");
		//console.log(o);
		
		if($('#descPasteTypeHtml').prop('checked')) {
			var e = o.event;

			// Get pasted data via clipboard API
			clipboardData = e.clipboardData || window.clipboardData;
			console.log(clipboardData);
			pastedData = clipboardData.getData('text/html');

			// Do whatever with pasteddata
			console.log(pastedData);
			
			if (pastedData == null || pastedData == "") {
				pastedData = clipboardData.getData('text/plain');
				console.log("getting plain text from clipboard")
				console.log(pastedData);
			}
			
			o.text = "";

			$.ajax({
				type: "POST",
				url: "/html2md",
				dataType: "json",
				data: {
					html: pastedData
				},
				beforeSend: function(xhr) {
					xhr.setRequestHeader(csrfHeader, csrfToken);
				},
				success: 
					function( descriptionResponse ) {
					console.log(descriptionResponse);
					editor.insert(descriptionResponse.markdown);
				},
				error: function( jqXHR, textStatus, errorThrown) {
					mizAlert("Conversion failed: " + textStatus + " - " + errorThrown, "danger");
				}
			});
			
			$('#descPasteTypeText').click();
		}
	});
	
	editor.commands.addCommand({
		name: 'close',
		bindKey: {win: 'Escape',  mac: 'Escape'},
		exec: closeEditor,
		readOnly: true // false if this command should not apply in readOnly mode
	});
	
	$('[data-toggle="tooltip"]').tooltip();
	$('#descriptionMarkdownToolbar .btn').tooltip();
	
	refreshDescription();
});

