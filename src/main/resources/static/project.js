const projectRoute = { 
	path: '/issues/:project', 
	component: { 
		template: 
`
<div>
	<div class="mizoine-title">
		<h1><i v-if="issues == null || info == null" class="fas fa-circle-notch fa-spin"> </i>
			<project-badge v-if="info" :meta="info.project.meta" :text="project" />
				<span v-if="info">{{info.project.meta ? info.project.meta.title : defaultTitle}}</span>
			</h1>
	</div>
	<div class="secondary" v-if="issues == null || info == null"><i class="fas fa-circle-notch fa-spin"></i> Loading...</div>
	<div class="danger" v-if="error">{{ error }}</div>
	<div v-if="info &amp;&amp; info.description" :key="'descr' + info.timestamp">
		<md-html :html="info.description.html"/>
	</div>
	<nav class="navbar navbar-light bg-light sticky-top">
		<router-link class="nav-item btn btn-light" 
			:to="'/edit/' + mznURI('projects/' + project + '/description.md')">
			<icon name="edit"/><span-lg> Edit</span-lg>
		</router-link>
		<router-link class="nav-item btn btn-light" 
			:to="'/edit/' + mznURI('projects/' + project + '/meta.json')">
			<icon name="sliders-h"/><span-lg> Meta</span-lg>
		</router-link>
		<button v-if="finishedCount > 0" 
			:class="'nav-item btn-light btn' + (showFinished ? ' active' : '')" 
			@click="showFinished = !showFinished">
			Show finished <span class="badge badge-secondary"><icon name="clipboard-check"/> {{finishedCount}}</span>
		</button>
		<div class="nav-item ml-auto">
			<form v-on:submit.prevent class="form-inline">
				<div class="input-group">
					<input type="text" v-model="newIssueTitle" 
						class="form-control" placeholder="Issue"/>
					<div class="input-group-append">
						<button type="submit" @click="newIssue()" class="btn btn-secondary" 
							title="Create new issue">
							<icon name="plus-circle"/>
						</button>
					</div>
				</div>
			</form>
		</div>
	</nav>
	<div class="list-group" v-if="issues">
		<router-link :to="'/issue/' + project + '-' + p.issueNumber" v-for="p in issues" :key="p.issueNumber"
			class="list-group-item list-group-item-action" v-show="showFinished || !p.finished">
			<i-badge v-if="info" :meta="info.project.meta" :text="project + '-' + p.issueNumber" />
			<i-badge v-if="appInfo" v-for="t in p.status" :key="t" :meta="appInfo.tags[t]" :text="t" />{{ p.meta ? p.meta.title : defaultTitle }}
			<i-badge v-if="appInfo" v-for="t in p.tags" :key="t" :meta="appInfo.tags[t]" :text="t" />
		 </router-link>
	</div>
</div>
`, 
		data () {
			return {
				post: null,
				error: null,
				issues: null,
				showFinished: false,
				finishedCount: 0,
				newIssueTitle: null,
				project: this.$route.params.project,
				info: null,
				appInfo: null
			}
		},
		methods: {
			newIssue() {
				var t = this;
				if (t.newIssueTitle == null) {
					return;
				}
				console.log('Creating issue with title: ' + t.newIssueTitle);
				var formData = new FormData();
				formData.append('title', t.newIssueTitle);
				t.newIssueTitle = null;

				axios.post("/api/project/" + t.project + "/issue", formData)
				.then(response => {
					store.commit('updateGitStatus');
					t.$router.push({ path: `/issue/` + response.data });
					displayMessage("Issue created: " + response.data);
				})
				.catch(displayError);
			}			
		},
		created () {
			var t = this;
			var project = this.$route.params.project;
			console.log(project);
			t.issues = null;
			t.error = null;
			axios
				.get("/api/project/" + project + "/issues")
				.then(response => {
					t.issues = response.data;
					if (!t.issues) return;
					appInfo.get(info => (
						t.issues.forEach(issue => {
							if (!issue.status) return;
							for (var i = 0; i < issue.status.length; i++) {
								if (info.finishedStatuses[issue.status[i]]) {
									issue.finished = true;
									t.finishedCount++;
								}
							}
						})
					));
				})
				.catch(displayError);
			
			projectsInfo.get(project, data => {
				t.info = data;
			});
			appInfo.get(info => (t.appInfo = info));
		}
	}
};
