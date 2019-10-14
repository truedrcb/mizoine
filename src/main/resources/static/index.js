// http://momentjs.com/docs/#/use-it/browser/
moment().format();

$(document).on('click', '[data-toggle="lightbox"]', function(event) {
	event.preventDefault();
	$(this).ekkoLightbox();
});

hljs.initHighlightingOnLoad();

// This is a global mixin, it is applied to every vue instance
Vue.mixin({
	data: function() {
		return {
			get faDefaultIcon() {
				return 'fas fa-tag';
			},
			get defaultTitle() {
				return '?';
			}
		}
	},
	methods: {
		moment: function(string) {
			return moment(string, "DD-MM-YYYY HH:mm:ssZ");
		},
		mznURI: function(path) {
			return encodeURIComponent(path.split('/').join('*'));
		}
	}
});

Vue.component('icon', {
	props: ['name'],
	template: `<i v-bind:class="'fas fa-' + name"> </i>`
});

Vue.component('span-lg', {
	template: `<span class="d-none d-lg-inline"><slot></slot></span>`
});

Vue.component('waiting-icon', {
	props: ['name', 'waiting'],
	template: `<i v-bind:class="'fas fa-fw fa-'  + (waiting ? 'circle-notch fa-spin' : name)"> </i>`
});

Vue.component('i-label', {
	props: ['icon', 'text'],
	template: `<span><i v-bind:class="'fas fa-' + icon + ' mr-1'"> </i>{{text}}</span>`
});

// Required in title bar for separate form submits
Vue.component('xsrf', {
	template: `<input type="hidden" name="_csrf" value="notset">`
});

Vue.component('loading-badge', {
	props: ['icon', 'color', 'title'],
	template: 
		`<span :class="'badge badge-' + (color ? color : 'secondary')" :title="title"><i v-bind:class="'fas fa-' + (icon ? icon : 'circle-notch fa-spin') "> </i></span>`
});

const store = new Vuex.Store({
	state: {
		gitStatus: null,
		gitBadge: {icon: null, color: null, title: 'Loading'},
		messages: [null, null, null],
		messagesCounter: 0,
		messagesKey: 0 // required to trigger component update
	},
	mutations: {
		updateGitStatus(state) {
			state.gitBadge.color = null;
			state.gitBadge.icon = null;
			state.gitBadge.title = 'Loading';
			axios.get('/api/git/status').then(response => {
				state.gitStatus = response.data;
				state.gitBadge.color = (state.gitStatus.staged > 0 || state.gitStatus.unstaged > 0) ? 'primary' : 'secondary';
				state.gitBadge.icon = state.gitStatus.staged > 0 ? 'save' : (state.gitStatus.unstaged > 0 ? 'asterisk' : 'check');
				state.gitBadge.title = 'Staged: ' + state.gitStatus.staged + ' | Unstaged: ' + state.gitStatus.unstaged;
			}).catch(error => {
				state.gitBadge.color = 'danger';
				state.gitBadge.icon = 'exclamation-triangle';
				state.gitBadge.title = '' + error;
			});
		},

		message(state, text) {
			state.messages[state.messagesCounter] = {
				header: moment().format("HH:mm:ss"),
				html: text
			};
			console.log("Message: (" + state.messagesCounter + ") " + text);
			console.log(state.messages.length);
			state.messagesKey++;

			state.messagesCounter++;
			if (state.messagesCounter >= state.messages.length) {
				state.messagesCounter = 0;
			}
		},
		
		hideMessage(state, index) {
			if (index < 0 || index >= state.messages.length) {
				console.log("Wrong message index: " + index);
				return;
			}
			console.log("hiding message: " + index);
			state.messages[index] = null;
			state.messagesKey++;
		}
		
	}
});

function displayError(error) {
	store.commit('message', 'Error: ' + error);
}

function displayMessage(message) {
	store.commit('message', message);
}

Vue.component('global-messages', {
	computed: {
		messages() { return store.state.messages; },
		messagesKey() { return store.state.messagesKey; }
	}, 
	template: `
<div class="position-fixed w-100 p-4 d-flex flex-column align-items-end"
	:key="messagesKey" style="top: 0; z-index: 9999; pointer-events: none;">
	<div style="pointer-events: all;" v-for="(message, messageIndex) in messages" 
		v-if="message" :key="messageIndex" 
		class="toast show">
		<div class="toast-header">
			<small>{{message.header}}</small>
			<button type="button" class="btn close ml-auto" @click="hide(messageIndex)">
				<icon name="times" />
			</button>
		</div>
		<div class="toast-body" v-html="message.html"></div>
	</div>
</div>
`,
	methods: {
		hide(messageIndex) {
			store.commit('hideMessage', messageIndex);
		}
	}
});

Vue.component('i-badge', {
	props: ['meta', 'text'],
	template: 
	`<span :class="'mr-1 badge badge-' + (meta ? meta.badgeStyle : 'secondary')">
	<i :class="meta ? meta.icon : faDefaultIcon"></i>
	{{ text }}
	</span>`
	});

Vue.component('i-tag', {
	props: ['appInfo', 'tag'],
	computed: {
		badgeStyle: function() {
			if (this.appInfo && this.tag && this.appInfo.tags) {
				var tagInfo = this.appInfo.tags[this.tag];
				if (tagInfo && tagInfo.badgeStyle) {
					return tagInfo.badgeStyle;
				}
			}
			return 'secondary';
		},
		icon: function() {
			if (this.appInfo && this.tag && this.appInfo.tags) {
				var tagInfo = this.appInfo.tags[this.tag];
				if (tagInfo && tagInfo.icon) {
					return tagInfo.icon;
				}
			}
			return 'fas fa-tag';
		}
	},
	methods: {
		clickRemove: function() {
			this.$emit("remove");
		}
	},
	template: ` 
<div v-if="tag" class="btn-group mr-1">
	<router-link :to="'/search/tag/' + tag" :class="'btn btn-' + badgeStyle" :title="'Search by tag: ' + tag">
			<i :class="icon"></i> <span>{{tag}}</span>
	</router-link>
	<button v-on:click='clickRemove' :class="'btn btn-' + badgeStyle" :title="'Remove tag: ' + tag">
		<icon name="times" />
	</button>
</div>`
	});

Vue.component('i-link-badge', {
	props: ['meta', 'text', 'to'],
	template: 
	`<router-link :to="to" class="mr-1 badge badge-light">
	<i v-bind:class="meta ? meta.icon : faDefaultIcon"></i>
	{{ text }}
	</router-link>`
	});

Vue.component('project-badge', {
	props: ['meta', 'text'],
	template: 
	`<span v-bind:class="'project-badge mr-2 badge badge-' + (meta ? meta.badgeStyle : 'secondary')">
	<i v-bind:class="meta ? meta.icon : faDefaultIcon"></i>
	{{ text }}
	</span>`
	});

Vue.component('project-link-badge', {
	props: ['info'],
	template: 
	`<router-link v-if="info" :to="'/issues/' + info.project" class="project-badge mr-2 badge badge-light" 
		:title="info.meta ? info.meta.title : info.project">
	<i v-bind:class="info.meta ? info.meta.icon : faDefaultIcon"></i>
	{{ info.project }}
	</router-link>`
	});

Vue.component('repository-tree', {
	props: ['repository'],
	template: 
	`
<div class="list-group" v-if="repository">
	<template v-for="(projectMeta, project) in repository.projects">
		<router-link 
			class="list-group-item list-group-item-action" 
			:to="'/issues/' + project">
			<project-badge :meta="projectMeta" :text="project"/> {{projectMeta.title}}
		</router-link>
		<template v-for="(issueMeta, issueNumber) in projectMeta.issues">
			<router-link 
				class="list-group-item list-group-item-action" 
				:to="'/issue/' + project + '-' + issueNumber">
				<i-badge :meta="projectMeta" :text="project + '-' + issueNumber"/> {{issueMeta.title}}
			</router-link>
			<template v-for="ment in issueMeta.ments">
				<router-link 
					class="list-group-item list-group-item-action small"
					v-if="ment.attachment" 
					:to="'/issue/' + project + '-' + issueNumber + '/attachment/' + ment.attachment.id">
					<icon name="file-image" /> {{ment.attachment.title}}<template v-if="ment.attachment.meta"> ({{ment.attachment.meta.fileName}})</template>
				</router-link>
				<router-link 
					class="list-group-item list-group-item-action small"
					v-if="ment.comment" 
					:to="'/issue/' + project + '-' + issueNumber + '/comment/' + ment.comment.id">
					<icon name="comment-alt" /> <template v-if="ment.comment.meta">{{ment.comment.meta.creator}}: {{ment.comment.meta.title}}</template>
					<template v-if="!ment.comment.meta">{{ment.comment.id}}</template>
				</router-link>
			</template>
		</template>
	</template>
</div>
`	});
	
const routes = [
{ 
	path: '/', 
	component: { 
		template: 
`
<div>
	<div class="mizoine-title">
		<h1>{{title}}</h1>
	</div>
	<div class="secondary" v-if="projects == null"><i class="fas fa-circle-notch fa-spin"></i> Loading...</div>
	<div class="danger" v-if="error">{{ error }}</div>
	<nav class="navbar navbar-light bg-light sticky-top">
		<router-link class="nav-item nav-link btn btn-light" 
			:to="'/edit/' + mznURI('description.md')">
			<icon name="edit"/><span-lg> Edit</span-lg>
		</router-link>
		<router-link class="nav-item nav-link btn btn-light" 
			:to="'/edit/' + mznURI('meta.json')">
			<icon name="sliders-h"/><span-lg> Meta</span-lg>
		</router-link>
	</nav>
	<div class="list-group" v-if="projects">
		<router-link :to="'/issues/' + p.project" v-for="p in projects"  :key="p.project" 
			class="list-group-item list-group-item-action">
			<project-badge :meta="p.meta" :text="p.project" />{{ p.meta ? p.meta.title : p.project }}</router-link>
	</div>
</div>
`, 
		data () {
			return {
				post: null,
				error: null,
				title: 'Projects',
				projects: []
			}
		},
		created () {
			var t = this;
			t.error = null;
			projectsShortInfo.get(data => {
				t.projects = data;
			});
		}
	} 
},
projectRoute,
issueRoute,
gitRoute,
viewerRoute,
editorRoute,
{
	path: '/search/q/:query', 
	component: searchComponent 
},
{
	path: '/search/q/:query/tag/:tag', 
	component: searchComponent 
},
{
	path: '/search/tag/:tag', 
	component: searchComponent 
}
]

const router = new VueRouter({
	mode: 'history',
	routes: [
		{
			path: '/',
			component: headerComponent,
			children: routes
		}
	]
})

const app = new Vue({
	router
}).$mount('#app')
