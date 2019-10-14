Vue.component('git-button', {
	props: ['title', 'icon', 'disabled'],
	methods: {
		click: function() {
			this.$emit("click");
		}
	},
	template:
`
<button v-on:click='click' :title="title" type='button' 
		class='nav-item btn btn-light' data-toggle="tooltip" data-placement="top"
		:disabled='disabled'>
	<icon v-if="icon" :name="icon" />
	<span-lg>{{title}}</span-lg>
</button>
`
})


const gitRoute = 
{
	path: '/git', 
	component: { 
		template: 
`
<div>
	<div class="mizoine-title">
		<h1>{{title}}</h1>
	</div>
	<div class="secondary" v-if="stage == null"><i class="fas fa-circle-notch fa-spin"></i> Loading...</div>
	<div class="danger" v-if="error">{{ error }}</div>
	<div class="primary" v-if="message"><pre>{{ message }}</pre></div>
	<nav class="navbar navbar-light bg-light sticky-top">
		<git-button @click='gitRefresh()' title="Refresh" icon='sync' />
		<git-button @click='gitStageAll()' title="Stage all" icon='plus-square' :disabled='isEmpty("unstaged")' />
		<git-button @click='gitUnstageAll()' title="Unstage all" icon='minus-square'  :disabled='isEmpty("staged")' />
		<div class="nav-item ml-auto">
			<form v-on:submit.prevent class="form-inline">
				<div class="input-group">
					<input type="text" v-model="commitMessage" 
						class="form-control" placeholder="Commit message" />
					<div class="input-group-append">
						<button type="submit" @click="gitCommit()" class="btn btn-primary"
							 :disabled='isEmpty("staged") || (!commitMessage)'
							title="Commit staged changes">
							<icon name="archive"/>
						</button>
					</div>
				</div>
			</form>
		</div>
		<git-button @click='gitPull()' title="Pull" icon='cloud-download-alt' />
		<git-button @click='gitPush()' title="Push" icon='cloud-upload-alt' />
	</nav>
	<div class="row">
	<div class="col" v-if="stage" v-for="aged in ['unstaged', 'staged']">
		<h4>{{aged}}</h4>
		<ul class="list-group" v-if="stage[aged]">
			<li class="list-group-item" v-for="(info, path) in stage[aged]">
				<div v-if="info.project">
					<project-link-badge :info="projectsMap[info.project]" />
					<i-badge v-if="info.issueNumber && projectsMap[info.project]" :meta="projectsMap[info.project].meta" 
						:text="info.project + '-' + info.issueNumber" />
					{{info.fileName}}
				</div>
				<div>
					<small>
						<i v-for="status in info.status" :title="status" :class="'text-info ' + statusIcons[status]" />
						<router-link v-if="info.path" :to="'/view/' + mznURI(info.path)">{{path}}</router-link>
						<span v-if="!info.path">{{path}}</span>
					</small>
				</div>
			</li>
		</ul>
	</div>
	</div>
	<h2><icon name="clock"/> History</h2>
	<ul class="list-group" v-if="log">
		<li class="list-group-item" v-for="item in log" :key="item.name">
			<h5><small class="git-commit-name" :title="item.name">{{item.name}}</small>
			<span v-for="tag in item.tags" :key="tag" class="badge badge-warning mr-1">{{tag}}</span>
			{{item.shortMessage}}</h5>
			<div v-if="!item.repository">
				<project-link-badge v-for="project in item.projects" :info="projectsMap[project]" />
				<span v-for="issue in item.issues">
					<router-link :to="'/issue/' + issue">{{issue}}</router-link>, 
				</span>
			</div>
			<repository-tree :repository="item.repository" />
		</li>
	</ul>
</div>
`, 
		data () {
			return {
				stage: null,
				error: null,
				message: null,
				title: 'Git',
				appInfo: null,
				statusIcons: {
					"modified": "fas fa-greater-than", "untracked": "fas fa-question", "missing": "fas fa-times",
					"changed": "fas fa-asterisk", "added": "fas fa-plus", "removed": "fas fa-times-circle"
				},
				projectsMap: {},
				log: null,
				commitMessage: null
			}
		},
		methods: {
			gitRefresh: function () {
				this.gitStatus();
				this.gitLog();
			},
			gitStatus: function () {
				var t = this;
				axios
					.get("/api/git/stage")
					.then(response => {
						t.stage = response.data;
					}).catch(displayError);
				store.commit('updateGitStatus');
			},
			gitLog: function () {
				axios.get("/api/git/log").then(response => {
					this.log = response.data;
				}).catch(displayError);
			},
			gitPull: function () {
				var t = this;
				axios.post("/api/git/pull").then(response => {
					t.message = response.data;
					t.gitLog();
				}).catch(displayError);
			},
			gitPush: function () {
				var t = this;
				axios.post("/api/git/push").then(response => {
					t.message = response.data;
					t.gitLog();
				}).catch(displayError);
			},
			gitStageAll: function () {
				var t = this;
				axios.post("/api/git/stage-all").then(response => {
					t.message = response.data;
					t.gitStatus();
				}).catch(displayError);
			},
			gitUnstageAll: function () {
				var t = this;
				axios.post("/api/git/unstage-all").then(response => {
					t.message = response.data;
					t.gitStatus();
				}).catch(displayError);
			},
			gitCommit: function () {
				var t = this;
				if (!t.commitMessage) {
					displayError("Enter a commit message!");
					return;
				}
				console.log('Creating commit with message: ' + t.commitMessage);
				var formData = new FormData();
				formData.append('commitMessage', t.commitMessage);
				t.commitMessage = null;

				axios.post("/api/git/commit", formData).then(response => {
					t.message = response.data;
					t.gitStatus();
					t.gitLog();
				}).catch(displayError);
			},
			isEmpty(listName) {
				var t = this;
				if (!t.stage) {
					return true;
				}
				return jQuery.isEmptyObject(t.stage[listName]);
			}
		},
		created () {
			var t = this;
			t.error = null;
			t.gitStatus();
			appInfo.get(info => (this.appInfo = info));
			projectsShortInfo.get(data => {
				for (i in data) {
					var projectData = data[i];
					if (projectData && projectData.project) {
						t.projectsMap[projectData.project] = projectData;
					}
				}
			});
			t.gitLog();
		}
	} 
};