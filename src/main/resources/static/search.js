const searchComponent = { 
	template: 
`
<div v-if="found">
	<h1>q: {{found.query}}, tag: {{found.tag}}</h1>
	<div class="list-group" v-if="found.repository">
		<template v-for="(projectMeta, project) in found.repository.projects">
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
			</template>
		</template>
	</div>
	<div class="list-group" v-if="found.hits">
		<template v-for="hit in found.hits">
			<router-link 
				class="list-group-item list-group-item-action" 
				v-if="hit.issueNumber"
				:to="'/issue/' + hit.project + '-' + hit.issueNumber">
				{{hit.project}}-{{hit.issueNumber}}
				<small>{{hit}}</small>
			</router-link>
		</template>
	</div>
</div>
`, 
	data () {
		return {
			found: null,
			query: this.$route.params.query,
			tag: this.$route.params.tag,
			appInfo: null
		}
	},
	methods: {
		update() {
			var t = this;
			console.log(t.query);
			axios
				.get("/api/search/find", { params: { q: t.query, tag: t.tag } })
				.then(response => (this.found = response.data))
				.catch(displayError);
		}
	},
	created () {
		this.update();
		appInfo.get(info => (this.appInfo = info));
	},
	watch: {
		'$route' (to, from) {
			var t = this;
			t.query = t.$route.params.query;
			t.update();
		}
	},

}; 
