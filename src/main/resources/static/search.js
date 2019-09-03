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
