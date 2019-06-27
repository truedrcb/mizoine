const viewerRoute = {
	path: '/view/:filePath',
	component: {
		template: 
`
<div>
	<div class="mizoine-title">
		<h1><i v-if="info == null" class="fas fa-circle-notch fa-spin"> </i>
		<project-link-badge v-if="projectInfo" :info="projectInfo.project" />
		<i-link-badge v-if="issueNumber && projectInfo" :to="'/issue/' + project + '-' + issueNumber" 
			:meta="projectInfo.project.meta" :text="project + '-' + issueNumber" />
		{{filePath}}
		</h1>
	</div>
	<nav class="navbar navbar-light bg-light sticky-top" v-if="info">
		<router-link class="nav-item btn" 
			:to="'/edit/' + filePath">
			<icon name="edit"/><span-lg> Edit</span-lg>
		</router-link>
		<a :href="info.info.fullFileUri" v-if="info.info.fullFileUri"
			class="nav-item btn">
			<icon name="download"/><span-lg> Download</span-lg>
		</a>
	</nav>
{{info}}
</div>
`,
		data () {
			return {
				filePath: null,
				servicePath: null,
				info: null,
				project: null,
				projectInfo: null,
				issueNumber: null,
				issueInfo: null
			}
		},
		methods: {
			
		},
		mounted () {
			var t = this;
			this.filePath = this.$route.params.filePath;
			this.servicePath = "/api/file/" + this.filePath;
			axios
			.get(this.servicePath)
			.then(response => {
				t.info = response.data;
				if (t.info.info) {
					t.project = t.info.info.project;
					t.issueNumber = t.info.info.issueNumber;
				}

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
			}).catch(displayError);
			
		}
	}
}