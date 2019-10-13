Vue.component('upload-files', {
	props: ['uploadUrl'],
	data: function() {
		return {
			el: null
		}
	}, 
	template:
`
<input type="file" multiple="" />
`,
	watch: {
		'uploadUrl' (to, from) {
			var t = this;
//			console.log("Updating upload-files");
//			console.log(t.el);
//			console.log(t.uploadUrl);
			t.el.fileinput('destroy').fileinput(
					{
						theme: "fa",
						uploadUrl: t.uploadUrl,
						showClose: false,
						hideThumbnailContent: true // hide image, pdf, text or other content in the thumbnail preview
					}
			);
		} 
	},
	mounted: function() {
		var t = this;
		var el = $(t.$el);
		t.el = el;

//		console.log("Creating upload-files");
//		console.log(el);

		el.fileinput({
			theme: "fa",
			uploadUrl: t.uploadUrl,
			showClose: false,
			hideThumbnailContent: true // hide image, pdf, text or other content in the thumbnail preview
		});

		el.on('filepreupload', function(event, data, previewId, index) {
			console.log('File pre upload triggered');
			console.log(data);
			var xhr = data.jqXHR;
			setXSRF(xhr);
		});

		el.on('filebatchuploadcomplete', function(event, data, previewId, index) {
			console.log('File batch upload complete');
			t.$emit('filebatchuploadcomplete');
		});

		el.on('fileuploaderror', function(event, data, msg) {
			console.log('File upload error');
			console.log(data);
			console.log(msg);
			t.$emit('fileuploaderror');
		});
	},
	beforeDestroy: function() {
		var t = this;
		var el = $(t.$el);

		console.log("Destroying upload-files");
		console.log(el);
		
		el.fileinput('destroy');
	}
});


Vue.component('md-html', {
	props: ['html', 'imgInfos'],
	template:
`
<div>
<div class="limit-images container-fluid mb-0 p-1" 
	style="min-height: 10em; width: 100%; " 
	v-html="html"> </div>
</div>
`,
	methods: {
		update: function() {
			var t = this;
			var el = $(this.$el);
			el.find('pre code').each(function(i, block) {
				hljs.highlightBlock(block);
			});
			
			el.find('img.miz-md-thumbnail, img.miz-md-img').each(function () {
				var image = $(this);
				var ref = image.attr("miz-ref");
				var attachment = null;
				if (t.imgInfos && ref) {
					attachment = t.imgInfos[ref]; 
				}
				var fileUri = null;
				if (attachment && attachment.files && attachment.files.length > 0) {
					fileUri = attachment.files[0].fullFileUri;
				}
				var filePreviewUri = fileUri;
				if (attachment && attachment.preview) {
					filePreviewUri = attachment.preview.fullFileUri || filePreviewUri;
				}
				if (attachment && filePreviewUri) {
					image.wrap(
							"<a href='" + filePreviewUri 
								+ "' data-toggle='lightbox' data-title='" 
								+ attachment.title + "'> </a>");
				} else {
					image.wrap(
						"<a href='" + image.attr("src") 
							+ "' data-toggle='lightbox' data-title='" 
							+ image.attr("title") + "'> </a>");
				}
			});
		}
	},
	mounted: function() {
		this.update();
	},
	updated: function() {
		this.update();
	}
});


const unreadMailList = {
	template: `
<div>
	<div v-if="mails == null">Loading...</div>
	<div class="list-group">
		<router-link :to="'preview-mail/' + mail.uri" 
			class="list-group-item" 
			v-for="mail in mails"
			:key="mail.uri">
			<icon :name="importedMessageIds[mail.id] ? 'envelope-open' : 'envelope'"/>
			<div>From: <span class="mr-2" v-for="from in mail.from">"{{from.personal}}" {{from.address}}</span></div>
			<strong>{{mail.subject}}</strong>
		</router-link>
	</div>
</div>
`,
	data () {
		return {
			project: this.$route.params.project,
			issueNumber: this.$route.params.issueNumber,
			mails: null,
			importedMessageIds: {}
		}
	},
	mounted() {
		var t = this;
		axios.get("/api/mail/list/unread")
		.then(response => {
			this.mails = response.data;
		})
		.catch(displayError);
		issuesInfo.get(t.project, t.issueNumber, data => {
			var mentslen = data.ments.length;
			for (var mentindex = 0; mentindex < mentslen; mentindex++) {
				var ment = data.ments[mentindex];
				if (ment.comment && ment.comment.meta && ment.comment.meta.messageId) {
					t.importedMessageIds[ment.comment.meta.messageId] = ment;
				}
			}
		});
	}
};

const contentIcons = {
	'text/plain': 'file-alt',
	'text/html': 'file-code',
	'application/pdf': 'file-pdf',
	'image': 'file-image'
}

const previewMail = {
	template: `
<div>
	<div v-if="mail == null" class="py-4 jumbotron" style="line-height: 1; min-height: 20em;">
		<div class="d-flex justify-content-center">
			<i class="fas fa-circle-notch fa-spin"></i>
			<span class="ml-1">Loading...</span>
		</div>
	</div>
	<div v-if="mail">
		<div>From: <span class="mr-2" v-for="from in mail.from">"{{from.personal}}" {{from.address}}</span></div>
		<h3>{{mail.subject}}</h3>
		<div class="row">
			<div class="col-2">
				<div class="nav flex-column nav-pills">
					<template v-for="block in mail.blocks">
						<a href="#" v-on:click.prevent.stop="showBlock(block)" 
							:class="'nav-link' + (isBlockActive(block) ? ' active' : '')""
							:title="block.contentType">
							<icon :name="blockIcon(block)"/> {{block.contentSubType.toLowerCase().substring(0, 20) + (block.contentSubType.length > 20 ? '…' : '')}}
						</a>
					</template>
					<a href="#" v-on:click.prevent.stop="activeBlock = 'header'" 
						:class="'nav-link' + (activeBlock == 'header' ? ' active' : '')"><icon name="at"/> Headers</a>
				</div>
			</div>
			<div class="col-10">
				<div class="tab-content">
					<div :class="'container-fluid tab-pane ' + (activeBlock == 'header' ? 'active' : '')" id="mail-preview-header">
						<div v-for="header in mail.headers" class="row form-group">
							<div class="col-2 col-form-label">{{header.name}}:</div>
							<div class="col-10">{{header.value}}</div>
						</div>
					</div>
					<template v-for="block in mail.blocks">
						<div :class="'tab-pane ' + (isBlockActive(block) ? 'active' : '')">
							<div v-html="showOriginalHtml ? block.content : block.html"></div>
							<hr/>
							<button :class="'btn btn-primary' + (importingMail ? ' disabled' : ' active')" 
								:disabled="importingMail" @click="importMail(block)">
								<waiting-icon name="upload" :waiting="importingMail"/> Import
							</button>
							<template v-if="block.contentSubType.toLowerCase() == 'text/html'">
								<button class="btn btn-secondary" @click="toggleOriginalHtml()">
									<icon :name="showOriginalHtml ? 'eye' : 'eye-slash'"/> Original HTML
								</button>
							</template>
							<div v-if="block.markdown">
								<hr/>
								<code><pre>{{block.markdown}}</pre></code>
							</div>
							<div v-if="block.fileName">
							<h3>{{block.fileName}}
								<span v-if="block.size">{{block.size}} bytes</span>
							</h3></div>
						</div>
					</template>
				</div>
			</div>
		</div>
	</div>
</div>
`,
	data () {
		return {
			mailUri: this.$route.params.mailUri,
			project: this.$route.params.project,
			issueNumber: this.$route.params.issueNumber,
			mail: null,
			activeBlock: 'header',
			showOriginalHtml: false,
			importingMail: false
		}
	},
	methods: {
		blockIcon: function(block) {
			var contentType = block.contentType.toLowerCase();
			for (var typePrefix in contentIcons) {
				if (contentType.startsWith(typePrefix)) return contentIcons[typePrefix];
			}
			return 'file-archive';
		},
		showBlock: function(block) {
			this.activeBlock = block.id;
			this.showOriginalHTML = false;
		},
		isBlockActive: function(block) {
			return this.activeBlock == block.id;
		},
		toggleOriginalHtml: function() {
			this.showOriginalHtml = !this.showOriginalHtml;
		},
		importMail: function(block) {
			var t = this;
			t.importingMail = true;
			console.log('Importing mail:  ' + t.mailUri + ' (' + block.id + ') to issue ' 
					+ t.project + '-' + t.issueNumber);
			var formData = new FormData();
			formData.append('project', t.project);
			formData.append('issueNumber', t.issueNumber);
			formData.append('uri', t.mailUri);
			formData.append('blockId', block.id);

			axios.post("/api/mail/import-to-issue", formData)
			.then(response => {
				store.commit('updateGitStatus');
				t.$router.push({ path: `/issue/${this.project}-${this.issueNumber}` });
				issuesInfo.reload(t.project, t.issueNumber, (data)=>{});
			})
			.catch(error => {
				displayError(error);
				t.importingMail = false;
			});
			
		}
	},
	mounted() {
		var t = this;
		axios.get("/api/mail/preview/" + this.mailUri)
		.then(response => {
			t.mail = response.data;
			["text/html", "text/plain", "text"].some((contentType, index, array) => {
				//console.log("checking: " + contentType);
				return t.mail.blocks.some((block, index, array) => {
					//console.log("block: " + block.contentType.toLowerCase());
					if (block.contentType.toLowerCase().startsWith(contentType)) {
						t.showBlock(block);
						return true;
					}
					return false;
				});
			});
		})
		.catch(displayError);
	}
};

const issueMents = {
	template: `
<div v-if="info">
	<div class="no-print p-2">
		<div v-for="(ment, mentindex) in info.ments" class="media mb-5" :key="ment.descriptionPath">
			<a :id="'ment-' + ment.descriptionPath"></a>
			<h6>
				<router-link v-if="ment.attachment" :to="$route.path + '/attachment/' + ment.attachment.id">
					<div class="comment-badge mr-2 badge badge-light text-secondary">
						<img v-if="ment.thumbnail" class="thumbnail" :src="ment.thumbnail" />
						<i v-if="ment.icon" :class="ment.icon"></i>
					</div>
				</router-link>
				<router-link v-if="ment.comment" :to="$route.path + '/comment/' + ment.comment.id">
					<div class="comment-badge mr-2 badge badge-light text-secondary">
						<i :class="ment.icon"></i>
					</div>
				</router-link>
			</h6>
			<div class="media-body">
				<div>
					<span v-if="ment.attachment" class="attachment-id">[{{ment.attachment.id}}]</span>
					<span v-if="ment.meta && ment.meta.creationDate">{{moment(ment.meta.creationDate).format("DD-MM-YYYY (ddd) HH:mm")}}</span>
				</div>
				<h4 class="comment-title">
					<button class="btn btn-light btn-sm float-right" 
						type="button" @click="ment.collapsed = !ment.collapsed">
						<icon :name="ment.collapsed ? 'angle-right' : 'angle-down'"/>
					</button>
					<template v-if="ment.meta">
						<span class="comment-creator" v-if="ment.meta.creator">{{ment.meta.creator}}:</span>
						<span class="comment-title" v-if="ment.meta.title">{{ment.meta.title}}</span>
					</template>
				</h4>
				<div :class="'limit-images ' + (ment.collapsed ? 'collapse' : '')"> 
					<md-html :html="ment.descriptionHtml" :imgInfos="imgInfos" />
				</div>
				<div :class="'text-center border-top border-bottom ' + (ment.collapsed ? '' : 'collapse')"> 
					<icon name="ellipsis-h"/>
				</div>
				<div class="limit-images" v-if="ment.attachment && ment.attachment.previews">
					<a v-for="(preview, index) in ment.attachment.previews" :href="preview.fullFileUri" data-toggle='lightbox' 
						:data-title="index + 1">
						<img class="miz-md-thumbnail" :key="preview.fullFileUri" :src="preview.thumbnailUri"/>
					</a>
				</div>
			</div>
		</div>
	</div>
</div>
`, 
	data () {
		return {
			info: null,
			project: this.$route.params.project,
			projectInfo: null,
			issueNumber: this.$route.params.issueNumber,
			uri: null,
			appInfo: null,
			imgInfos: {}
		}
	},
	methods: {
		update: function() {
			var t = this;
			t.uri = "/api/issue/" + t.project + '-' + t.issueNumber
			issuesInfo.get(t.project, t.issueNumber, t.reloadInfo);
			projectsInfo.get(t.project, data => {
				t.projectInfo = data;
			});
		},
		reloadInfo: function(data) {
			var t = this;
			var mentslen = data.ments.length;
			for (var mentindex = 0; mentindex < mentslen; mentindex++) {
				var ment = data.ments[mentindex];
				ment.meta = ment.comment ? ment.comment.meta : (ment.attachment ? ment.attachment.meta : null);
				ment.collapsed = !ment.comment;
				ment.icon = 'far fa-file-alt';
				if (ment.attachment) {
					var a = ment.attachment;
					t.imgInfos[a.id] = a;
					if (a.thumbnail) {
						ment.thumbnail = a.thumbnail.fullFileUri;
					}
					if (jQuery.isEmptyObject(a.thumbnails) && jQuery.isEmptyObject(a.previews)
							&& a.thumbnail && a.preview) {
						a.thumbnails = [a.thumbnail];
						a.previews = [a.preview];
					}
						
					if (a.thumbnails && a.previews 
							&& a.thumbnails.length == a.previews.length) {
						a.previews.forEach((preview, index) =>{
							preview.thumbnailUri = a.thumbnails[index].fullFileUri;
						})
					}
					if (!ment.thumbnail) {
						ment.icon = 'far fa-file-image';
					} else {
						ment.icon = null;
					}
					
					ment.viewPath = 'projects/' + t.project + '/issues/' + t.issueNumber + '/attachments/' 
						+ a.id + '/' +  a.files[0].fileName;
				} else {
					if (ment.comment) {
						ment.icon = 'far fa-comment-alt';
						if (ment.comment.meta && ment.comment.meta.messageId) {
							ment.icon = 'far fa-envelope';
						}
					}
				}
			}

			this.info = data;
		},
	},
	watch: {
		'$route' (to, from) {
			var t = this;
			//console.log("Route");
			//console.log(t.$route);
			//console.log(from);
			//console.log(to);
			t.project = t.$route.params.project;
			t.issueNumber = t.$route.params.issueNumber;
			t.update();
		}
	},
	created () {
		var t = this;
		t.update();
		appInfo.get(info => (t.appInfo = info));
	}
};


const issueComment = {
	template: `
<div v-if="ment">
	<nav class="navbar navbar-light bg-light sticky-top">
		<router-link class="nav-item btn" :to="'/edit/' + mznURI(info.descriptionPath)">
			<icon name="edit"/><span-lg> Edit</span-lg>
		</router-link>
		<router-link class="nav-item btn" :to="'/edit/' + mznURI(info.metaPath)">
			<icon name="sliders-h"/><span-lg> Meta</span-lg>
		</router-link>
		<button class="nav-item btn ml-auto" @click="deleteComment()"><icon name="trash-alt"/> Delete</button>
	</nav>
	<div class="p-2">
		<h4 class="comment-title">
			<template v-if="ment.meta">
				<span class="comment-creator" v-if="ment.meta.creator">{{ment.meta.creator}}:</span>
				<span class="comment-title" v-if="ment.meta.title">{{ment.meta.title}}</span>
			</template>
		</h4>
		<div :class="'limit-images ' + (ment.collapsed ? 'collapse' : '')"> 
			<md-html :html="info.descriptionHtml" />
		</div>
	</div>
</div>
`, 
	data () {
		return {
			ment: null,
			info: null,
			project: this.$route.params.project,
			issueNumber: this.$route.params.issueNumber,
			commentId: this.$route.params.commentId,
			uri: null,
			appInfo: null
		}
	},
	methods: {
		update: function() {
			var t = this;
			t.uri = "/api/comment/" + t.project + '-' + t.issueNumber + '/' + t.commentId;
			axios.get(t.uri + "/info")
			.then(response => {
				this.info = response.data;
				this.ment = this.info.comment;
			})
			.catch(displayError);
		},
		deleteComment: function() {
			var t = this;
			axios.delete(t.uri)
			.then(response => {
				store.commit('updateGitStatus');
				t.$router.push({ path: `/issue/${this.project}-${this.issueNumber}` });
				displayMessage("Comment removed: " + t.commentId);
			})
			.catch(displayError);
		}
	},
	created () {
		var t = this;
		t.update();
		appInfo.get(info => (t.appInfo = info));
	}
};

const issueAttachment = {
	template: `
<div v-if="ment">
	<nav class="navbar navbar-light bg-light sticky-top">
		<router-link class="nav-item btn" :to="'/edit/' + mznURI(info.descriptionPath)">
			<icon name="edit"/><span-lg> Edit</span-lg>
		</router-link>
		<router-link class="nav-item btn" :to="'/edit/' + mznURI(info.metaPath)">
			<icon name="sliders-h"/><span-lg> Meta</span-lg>
		</router-link>
		<button class="nav-item btn ml-auto" @click="deleteAttachment()"><icon name="trash-alt"/> Delete</button>
	</nav>
	<div class="p-2">
		<h4 class="comment-title">
			<template v-if="ment.meta">
				<span class="comment-creator">{{ment.id}}:</span>
				<span class="comment-title" v-if="ment.meta.title">{{ment.meta.title}}</span>
			</template>
		</h4>
		<div v-for="file in ment.files">
			<a :href="file.fullFileUri" target="_blank"><icon name="download"/> {{file.fileName}}</a>
		</div>
		<div class="limit-images">
			<md-html :html="info.descriptionHtml" />
			<hr/>
			<a v-for="(preview, index) in ment.previews" :href="preview.fullFileUri" data-toggle='lightbox' 
				:data-title="index + 1">
				<img :key="preview.fullFileUri" :src="preview.fullFileUri"/>
			</a>
		</div>
	</div>
</div>
`, 
	data () {
		return {
			ment: null,
			info: null,
			project: this.$route.params.project,
			issueNumber: this.$route.params.issueNumber,
			id: this.$route.params.id,
			uri: null,
			appInfo: null
		}
	},
	methods: {
		update: function() {
			var t = this;
			t.uri = "/api/attachment/" + t.project + '-' + t.issueNumber + '/' + t.id;
			axios.get(t.uri + "/info")
			.then(response => {
				t.info = response.data;
				t.ment = this.info.attachment;
				var a = t.ment;
				if (jQuery.isEmptyObject(a.thumbnails) && jQuery.isEmptyObject(a.previews)) {
					if (a.thumbnail && a.preview) {
						a.thumbnails = [a.thumbnail];
						a.previews = [a.preview];
					}
				}
					
				if (a.thumbnails && a.previews 
						&& a.thumbnails.length == a.previews.length) {
					a.previews.forEach((preview, index) =>{
						preview.thumbnailUri = a.thumbnails[index].fullFileUri;
					})
				}
			})
			.catch(displayError);
		},
		deleteAttachment: function() {
			var t = this;
			axios.delete(t.uri)
			.then(response => {
				store.commit('updateGitStatus');
				t.$router.push({ path: `/issue/${this.project}-${this.issueNumber}` });
				displayMessage("Attachment removed: " + t.commentId);
			})
			.catch(displayError);
		}
	},
	created () {
		var t = this;
		t.update();
		appInfo.get(info => (t.appInfo = info));
	}
};

const issueRoute = { 
	path: '/issue/:project-:issueNumber', 
	component: { 
		template: 
`
<div>
	<div class="mizoine-title">
		<h1><i v-if="description == null || info == null" class="fas fa-circle-notch fa-spin"> </i>
		<project-link-badge v-if="projectInfo" :info="projectInfo.project" />
		<i-badge v-if="info && projectInfo" :meta="projectInfo.project.meta" :text="project + '-' + issueNumber" />
		{{title}}
		</h1>
	</div>
	<div class="secondary" v-if="description == null"><i class="fas fa-circle-notch fa-spin"></i> Loading...</div>
	<div class="mb-3 no-print" v-if="info">
		<template v-if="info.issue">
			<i-tag v-for="tag in info.issue.status" :key="tag" :appInfo="appInfo" :tag="tag" @remove="removeTag(tag)"/>
			<i-tag v-for="tag in info.issue.tags" :key="tag" :appInfo="appInfo" :tag="tag" @remove="removeTag(tag)"/>
		</template>
	</div>
	<nav class="navbar navbar-light bg-light sticky-top">
		<router-link class="nav-item btn" 
			:to="'/edit/' + mznURI('projects/' + project + '/issues/' + issueNumber + '/description.md')">
			<icon name="edit"/><span-lg> Edit</span-lg>
		</router-link>
		<router-link class="nav-item btn" 
			:to="'/edit/' + mznURI('projects/' + project + '/issues/' + issueNumber + '/meta.json')">
			<icon name="sliders-h"/><span-lg> Meta</span-lg>
		</router-link>
		<div class="nav-item">
			<button :class="'btn btn-light' + (showUploadArea ? ' active' : '')" @click="showUploadArea = !showUploadArea;"
				title="Show upload area">
				<icon name="paperclip"/><span-lg> Upload</span-lg>
			</button>
		</div>
		<button :class="'nav-item btn' + (thumbnailsToUpdate.updating ? '' : ' active')" 
			:disabled="thumbnailsToUpdate.updating" title="Update thumbnails"
			@click="updateThumbnails()">
			<i :class="'fas fa-' + (thumbnailsToUpdate.updating ? 'circle-notch fa-spin' : 'images')"></i>
			<span-lg> Thumbnails</span-lg>
			<span v-if="thumbnailsToUpdate.attachments" class="badge badge-pill badge-danger">{{thumbnailsToUpdate.attachments.length}}</span>
		</button>
		<div class="nav-item ml-auto">
			<form v-on:submit.prevent class="form-inline">
				<div class="input-group">
					<input type="text" v-model="newTag" 
						class="form-control" placeholder="Tag/status" 
						id="newTagInput" name="tag" />
					<div class="input-group-append">
						<button type="submit" @click="addTag()" class="btn btn-secondary" 
							title="Add tag or status">
							<icon name="plus-circle"/>
						</button>
					</div>
				</div>
			</form>
		</div>
	</nav>
	<div :class="(showUploadArea && uri) ? 'my-4' : 'collapse'">
		<form :action="uri + '/upload'">
			<upload-files :uploadUrl="uri + '/upload'" @filebatchuploadcomplete="resetAndUpdate()"/>
		</form>
	</div>
	<div v-if="description" :key="'descr' + info.timestamp">
		<md-html :html="description.html" :imgInfos="imgInfos"/>
	</div>
	
	<nav class="navbar navbar-light bg-light nav-pills sticky-top">
		<div class="nav-item">
			<router-link 
				:class="'nav-link ' + ($route.meta.id == 'ments' ? 'active' : '')" 
				:to="'/issue/' + project + '-' + issueNumber">
				<icon name="mail-bulk"/><span-lg> Comments and attachments</span-lg>
				<span v-if="info && info.ments" class="badge badge-primary">{{ info.ments.length }}</span>
			</router-link>
		</div>
		<div class="nav-item mr-auto">
			<router-link
				:class="'nav-link ' + ($route.meta.id == 'unread-mail' ? 'active' : '')" 
				:to="'/issue/' + project + '-' + issueNumber + '/unread-mail'">
				<icon name="at"/><span-lg> Mail</span-lg>
			</router-link>
		</div>
		<div class="nav-item ml-auto">
			<form v-on:submit.prevent class="form-inline">
				<div class="input-group">
					<input type="text" v-model="newCommentTitle" 
						class="form-control" placeholder="Comment" />
					<div class="input-group-append">
						<button type="submit" @click="newComment()" class="btn btn-secondary" 
							title="Create new comment">
							<icon name="plus-circle"/>
						</button>
					</div>
				</div>
			</form>
		</div>
	</nav>
	<router-view :info="info" :key="'router-view-' + info.timestamp"></router-view>
</div>
`, 
		data () {
			return {
				info: {},
				description: null,
				project: this.$route.params.project,
				projectInfo: null,
				issueNumber: this.$route.params.issueNumber,
				uri: null,
				newTag: null,
				newCommentTitle: null,
				appInfo: null,
				showUploadArea: false,
				thumbnailsToUpdate: {
					updating: false,
					attachments: null,
					counter: 0
				},
				imgInfos: {}
			}
		},
		computed: {
			title: function() {
				var t = this;
				if (t.info) {
					if (t.info.issue && t.info.issue.meta) {
						return t.info.issue.meta.title;
					} 
					return "";
				}
				return 'Loading issue: ' + t.project + '-' + t.issueNumber;
			}
		},
		methods: {
			update: function() {
				var t = this;
				t.uri = "/api/issue/" + t.project + '-' + t.issueNumber
				t.showUploadArea = false;
				axios.get(t.uri + "/description")
					.then(response => {
						this.description = response.data;
					})
					.catch(displayError);
				issuesInfo.get(t.project, t.issueNumber, t.reloadInfo);
				projectsInfo.get(t.project, data => {
					t.projectInfo = data;
				});
				store.commit('updateGitStatus');
				//store.commit('message', 'issue: ' + t.project + '-' + t.issueNumber);
			},
			resetAndUpdate() {
				var t = this;
				issuesInfo.clear(t.project, t.issueNumber);
				t.update();
			},
			reloadInfo: function(data) {
				var t = this;
				t.info = data;
				if (t.info.ments) {
					t.info.ments.forEach( ment => {
						if (ment.attachment) {
							t.imgInfos[ment.attachment.id] = ment.attachment;
						}
					});
				}
			},
			addTag: function() {
				var t = this;
				console.log('Add tag: ' + t.newTag);
				var formData = new FormData();
				formData.append('tag', t.newTag);
				t.newTag = null;

				axios.post(t.uri + "/tag", formData)
				.then(response => {
					issuesInfo.reload(t.project, t.issueNumber, t.reloadInfo);
					store.commit('updateGitStatus');
				})
				.catch(displayError);
			}, 
			removeTag: function(tag) {
				var t = this;
				console.log('Removing tag: ' + tag);
				var formData = new FormData();
				formData.append('tag', tag);

				axios.delete(t.uri + "/tag", {data: formData})
				.then(response => {
					issuesInfo.reload(t.project, t.issueNumber, t.reloadInfo);
					store.commit('updateGitStatus');
				})
				.catch(displayError);
			},
			newComment() {
				//store.commit('message', 'Comment ' + new Date());
				var t = this;
				if (t.newCommentTitle == null) {
					return;
				}
				console.log('Creating comment with title: ' + t.newCommentTitle);
				var formData = new FormData();
				formData.append('title', t.newCommentTitle);
				t.newCommentTitle = null;

				axios.post(t.uri + "/comment", formData)
				.then(response => {
					store.commit('updateGitStatus');
					t.$router.push({ path: `/issue/${this.project}-${this.issueNumber}/comment/` + response.data });
					displayMessage("Comment created: " + response.data);
				})
				.catch(displayError);
			},
			updateThumbnails() {
				var t = this;
				
				if (!t.info.ments) return;
				t.thumbnailsToUpdate.attachments = [];
				
				t.info.ments.forEach( ment => {
					if (ment.attachment) {
						t.thumbnailsToUpdate.attachments.push(ment.attachment);
					}
				});
				
				t.updateFirstThumbnailAndRepeat();
			},
			updateFirstThumbnailAndRepeat() {
				var t = this;
				function stopUpdate() {
					t.thumbnailsToUpdate.attachments = null;
					t.thumbnailsToUpdate.updating = false;
					t.resetAndUpdate();
				};
				if (!t.thumbnailsToUpdate.attachments || t.thumbnailsToUpdate.attachments.lenght <= 0) {
					stopUpdate();
					return;
				}
				var attachment = t.thumbnailsToUpdate.attachments.shift();
				if (!attachment) {
					stopUpdate();
					return;
				}
				t.thumbnailsToUpdate.updating = true;
				axios.post("/api/attachment/" + t.project + "-" + t.issueNumber + "/" + attachment.id + "/update-thumbnails")
				.then(response => {
					t.updateFirstThumbnailAndRepeat();
				})
				.catch(error => {
					displayError(error);
					t.thumbnailsToUpdate.updating = false;
				});
			}
		},
		watch: {
			'$route' (to, from) {
				var t = this;
				//console.log("Route");
				//console.log(t.$route);
				//console.log(from);
				//console.log(to);
				t.project = t.$route.params.project;
				t.issueNumber = t.$route.params.issueNumber;
				t.update();
			}
		},
		created () {
			var t = this;
			t.update();
			appInfo.get(info => (t.appInfo = info));
		}
	},
	children: [
		{
			path: '', 
			component: issueMents,
			meta: { id: 'ments' }
		},
		{
			path: 'unread-mail', 
			component: unreadMailList,
			meta: { id: 'unread-mail' }
		},
		{
			path: 'preview-mail/:mailUri', 
			component: previewMail,
			meta: { id: 'preview-mail' }
		},
		{
			path: 'comment/:commentId', 
			component: issueComment,
			meta: { id: 'comment' }
		},
		{
			path: 'attachment/:id', 
			component: issueAttachment,
			meta: { id: 'attachment' }
		}
	]
}