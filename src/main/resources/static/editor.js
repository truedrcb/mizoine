Vue.component('ace-editor', {
	props: ['text', 'html'],
	template:
`
<div>{{text}}</div>
`
})

Vue.component('tool-button', {
	props: ['title', 'icon'],
	mounted: function() {
		// tooltip activation goes here.
		var el = $(this.$el);
		if (el && el.tooltip) {
			//console.log('tool-button::mounted hook', el);
			el.tooltip();
		}
	},  
	methods: {
		click: function() {
			this.$emit("click");
		}
	},
	template:
`
<button v-on:click='click' :title="title" type='button' 
		class='nav-item btn btn-light' data-toggle="tooltip" data-placement="top">
	<icon v-if="icon" :name="icon" />
	<span-lg v-if="!icon">{{title}}</span-lg>
</button>
`
})


Vue.component('tool-dd-button', {
	props: ['title', 'icon'],
	methods: {
		click: function() {
			this.$emit("click");
		}
	},
	template:
`
<button v-on:click='click' :title="title" type='button'	class='dropdown-item'>
	<icon v-if="icon" :name="icon" />
	<span v-if="!icon">{{title}}</span>
</button>
`
})


const editorRoute = {
	path: '/edit/:filePath',
	component: {
		template: 
`
<div>
	<div class="mizoine-title">
		<h1><i v-if="text == null" class="fas fa-circle-notch fa-spin"> </i>
		<project-link-badge v-if="projectInfo" :info="projectInfo.project" />
		<i-link-badge v-if="issueNumber && projectInfo" :to="'/issue/' + project + '-' + issueNumber" 
			:meta="projectInfo.project.meta" :text="project + '-' + issueNumber" />
		{{filePath}}
		</h1>
	</div>
	<div v-if="error" role='alert' class='alert alert-danger'>{{error}}</div>
	<div>
		<nav class='navbar navbar-expand-lg navbar-light bg-light sticky-top'>
			<tool-button @click='undo()' title="Undo" icon='undo' />
			<tool-button @click='redo()' title="Redo" icon='redo' />
			<template v-if="mode == 'markdown'">
				<div class="dropdown">
					<button title="" type="button"
						class="nav-item btn btn-light dropdown-toggle" data-toggle="dropdown" aria-haspopup="true" 
						data-original-title="# Headers">
						<icon name="heading"/>
					</button>
					<div class="dropdown-menu">
						<tool-dd-button @click='indentSelectionWith("# ")' title="# Heading 1" />
						<tool-dd-button @click='indentSelectionWith("## ")' title="## Heading 2" />
						<tool-dd-button @click='indentSelectionWith("### ")' title="### Heading 3" />
					</div>
				</div>
				<tool-button @click='putAroundSelection("*","*")' title="*Emphasis*" icon='italic' />
				<tool-button @click='putAroundSelection("**","**")' title="**Strong Emphasis**" icon='bold' />
				<tool-button @click='putAroundSelection("~~","~~")' title="~~Strikethrough~~" icon='strikethrough' />
				<tool-button @click='indentSelectionWith("- ")' title="- List" icon='list-ul' />
				<tool-button @click='indentSelectionWith("1. ")' title="1. Numbered List" icon='list-ol' />
				<tool-button @click='indentSelectionWith("> ")' title="> Blockquote" icon='quote-right' />
				<tool-button @click='insertLink()' title="[Link sample](url/sample)" icon='link' />
				<tool-button @click='insertImage()' title="![Image sample](url/image)" icon='image' />
				<div class="dropdown">
					<button title="" type="button"
						class="nav-item btn btn-light dropdown-toggle" data-toggle="dropdown" aria-haspopup="true" 
						data-original-title="| Table |">
						<icon name="table"/>
					</button>
					<div class="dropdown-menu">
						<tool-dd-button v-for="n in 5" @click='insertTable(n)' :title="n + ' columns'" :key="n" />
					</div>
				</div>
				<tool-button @click='insert("\\n\\n---\\n")' title="Horisontal ruler ---" icon='window-minimize' />
				<tool-button @click='putAroundSelection("<kbd>","</kbd>")' title="<kbd>Keyboard key</kbd>" icon='keyboard' />
				<tool-button @click='insertCode()' title="Code" icon='code' />
				<div class="btn-group mx-2">
					<div class="input-group-prepend">
						<div class="input-group-text"><icon name="paste"/></div>
					</div>
					<button type='button' :class="'btn btn-outline-secondary ' + (mdPasteMode === 'text' ? 'active' : '')"
						data-original-title="Paste normal text"
						@click="setPasteMode('text')">text</button>
					<button type='button'title="" :class="'btn btn-outline-secondary ' + (mdPasteMode === 'html' ? 'active' : '')" 
						data-original-title="Paste HTML if available"
						@click="setPasteMode('html')">html</button>
				</div>
				<a href="https://github.com/adam-p/markdown-here/wiki/Markdown-Cheatsheet" target="_blank" 
					class="nav-item btn btn-info" title="Markdown Cheatsheet"><icon name="question" /></a>
			</template>
			<button type="button" @click="save()" class="nav-item btn btn-success mr-1 ml-auto" data-original-title="" title="">
				<icon name="save" /> <span>Save</span>
			</button>
			<tool-button @click='quit()' title="Cancel" icon='times' />
		</nav>
		<div id="aceEditor" style="font-size: 1rem; width: 100%; height: 40em;">HERE SHOULD BE AN EDITOR</div>
	</div>
</div>
`,
		data () {
			return {
				editor: null,
				mode: null,
				filePath: null,
				servicePath: null,
				text: null,
				info: null,
				error: null,
				project: null,
				projectInfo: null,
				issueNumber: null,
				issueInfo: null,
				appInfo: null,
				mdPasteMode: 'text'
			}
		},
		methods: {
			save: function () {
				var t = this;
				var formData = new FormData();
				formData.append('fileContent', this.editor.getValue());
				
				axios
				.put(t.servicePath, formData)
				.then(response => {
					console.log(response);
					t.error = null;
					store.commit('updateGitStatus');
					
					// Overwrite references to improve navigation
					if (response.data && response.data.info) {
						t.info = response.data.info;
						t.project = this.info.project;
						t.projectInfo = null;
						t.issueNumber = this.info.issueNumber;
						t.attachmentId = this.info.attachmentId;
						t.commentId = this.info.commentId;
						t.issueInfo = null;
						if (t.project && t.issueNumber) {
							issuesInfo.clear(t.project, t.issueNumber);
						}
					}
					store.commit('message', 'Saved file: ' + t.filePath);
					t.quit();
				})
				.catch(error => {
					t.error = error;
					store.commit('message', 'Save failed: ' + error);
				});
			},
			quit: function () {
				if (this.project) {
					if (this.issueNumber) {
						if(this.attachmentId) {
							this.$router.push({ path: `/issue/${this.project}-${this.issueNumber}/attachment/${this.attachmentId}` });
						} else if(this.commentId) {
							this.$router.push({ path: `/issue/${this.project}-${this.issueNumber}/comment/${this.commentId}` });
						} else {
							this.$router.push({ path: `/issue/${this.project}-${this.issueNumber}` });
						}
					} else {
						this.$router.push({ path: `/issues/${this.project}` });
					}
				} else {
					this.$router.push("/");
				}
			},
			undo: function () {
				this.editor.undo();
				this.editor.focus();
			},
			redo: function () {
				this.editor.redo();
				this.editor.focus();
			},
			setPasteMode: function (pasteMode) {
				console.log('new past mode: ' + pasteMode);
				this.mdPasteMode = pasteMode;
				this.editor.focus();
			},
			insert: function (toInsert) {
				this.editor.insert(toInsert)
				this.editor.focus();
			},
			indentSelectionWith: function (indentText) {
				var r1 = this.editor.selection.getSelectionAnchor().row;
				var r2 = this.editor.selection.getSelectionLead().row;
				if (r1 > r2) {
					var flip = r2;
					r2 = r1;
					r1 = flip;
				}
				this.editor.session.indentRows(r1, r2, indentText);
				this.editor.clearSelection();
				this.editor.focus();
			},
			putAroundSelection: function (prefix, suffix) {
				var editor = this.editor;
				if (editor.selection.isMultiLine()) {
					editor.clearSelection();
				}
				var txt = editor.getCopyText();
				if (txt == "") {
					editor.insert(prefix + "text" + suffix);
					this.navLeftSelect(4 + suffix.length, 4);
				} else {
					editor.insert(prefix + txt + suffix);
				}
				editor.focus();
			},
			insertLink: function () {
				var editor = this.editor;
				if (editor.selection.isMultiLine()) {
					editor.clearSelection();
				}
				var txt = editor.getCopyText();
				if (txt == "") {
					editor.insert("[Link text](http://url)");
					this.navLeftSelect(11, 10);
				} else if (txt.startsWith("http") || txt.startsWith("/") ) { 
					editor.insert("[Link text](" + txt + ")");
					this.navLeftSelect(txt.length + 12, 9);
				} else {
					editor.insert("[" + txt + "](http://url)");
					this.navLeftSelect(11, 10);
				}
			},
			insertImage: function () {
				var editor = this.editor;
				if (editor.selection.isMultiLine()) {
					editor.clearSelection();
				}
				var txt = editor.getCopyText();
				if (txt == "") {
					editor.insert("![Image title](http://url)");
					this.navLeftSelect(11, 10);
				} else if (txt.startsWith("http") || txt.startsWith("/") ) {
					editor.insert("[Image title](" + txt + ")");
					this.navLeftSelect(txt.length + 14, 11);
				} else {
					editor.insert("![" + txt + "](http://url)");
					this.navLeftSelect(11, 10);
				}
			},
			navLeftSelect: function (leftCount, selectCount) {
				var editor = this.editor;
				editor.navigateLeft(leftCount);
				for (var i = 0; i < selectCount; i++) {
					editor.selection.selectRight();
				}
				editor.focus();
			},
			insertTable: function (columns) {
				var editor = this.editor;
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
				editor.insert("|\n\n");
				this.navLeftSelect(8, 4);
			},
			insertCode: function() {
				var editor = this.editor;
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
			}
		},
		mounted () {
			this.filePath = this.$route.params.filePath;
			this.servicePath = "/api/textfile/" + this.filePath;

			this.editor = ace.edit("aceEditor");
			this.editor.setTheme("ace/theme/clouds");
			this.editor.setShowPrintMargin(false);
			this.type = null;
			if (this.filePath.endsWith(".md")) {
				this.editor.session.setMode("ace/mode/markdown");
				this.mode = "markdown";
			} else if (this.filePath.endsWith(".json")) {
				this.editor.session.setMode("ace/mode/json")
				this.mode = "json";
			} else {
				this.editor.session.setMode("ace/mode/text")
				this.mode = "text";
			}
			this.editor.session.setUseWrapMode(true);
			this.editor.resize();
			this.editor.focus();
			var t = this;

			// https://stackoverflow.com/questions/2176861/javascript-get-clipboard-data-on-paste-event-cross-browser
			this.editor.on('paste', function (o) {
				console.log("Editor paste: " + t.mdPasteMode);
				//console.log(o);
				
				if(t.mdPasteMode === "html") {
					var e = o.event;

					// Get pasted data via clipboard API
					var clipboardData = e.clipboardData || window.clipboardData;
					console.log(clipboardData);
					var pastedData = clipboardData.getData('text/html');

					// Do whatever with pasteddata
					console.log(pastedData);
					
					if (pastedData === null || pastedData === "") {
						pastedData = clipboardData.getData('text/plain');
						console.log("getting plain text from clipboard")
						console.log(pastedData);
					}
					
					o.text = "";
					var formData = new FormData();
					formData.append('html', pastedData);
					
					axios
					.post("/api/html2md", formData)
					.then(descriptionResponse => {
						console.log(descriptionResponse);
						t.editor.insert(descriptionResponse.data.markdown);
						t.mdPasteMode = 'text';
						t.error = null;
					})
					.catch(displayError);
				}
			});

			console.log(this.filePath);
			axios
				.get(this.servicePath)
				.then(response => {
					t.text = response.data.text;
					t.editor.session.setValue(this.text);

					t.info = response.data.info;
					t.project = this.info.project;
					t.projectInfo = null;
					t.issueNumber = this.info.issueNumber;
					t.attachmentId = this.info.attachmentId;
					t.commentId = this.info.commentId;
					t.issueInfo = null;
					
					if (t.project) {
						projectsInfo.get(t.project, data => {
							t.projectInfo = data;
						});
						if (t.issueNumber) {
							issuesInfo.get(t.project, t.issueNumber, data => {
								t.issueInfo = data;
							});
						}
					}
				})
				.catch(error => {
					if (error.response && error.response.status == 404) {
						console.log("File missing yet. Set content to empty.");
						t.text = "";
						t.editor.session.setValue(this.text);
					} else {
						t.error = error;
						console.log("Loading failed");
						console.log(error);
					}
				});
			appInfo.get(info => (this.appInfo = info));
		}
	}
}