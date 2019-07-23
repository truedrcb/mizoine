const searchComponent = { 
	template: 
`
<div v-if="found">
	<h1>q: {{found.query}}, tag: {{found.tag}}</h1>
	<repository-tree :repository="found.repository" />
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
