const headerComponent = {
	template:
`
<div>
<header>
	<nav class="navbar navbar-expand-lg navbar-dark bg-dark">
		<router-link class="navbar-brand" to="/">
			<img src="/res/Mizoine-logo-transparent.png" width="24px"><span class="ml-1">Mizoine</span>
		</router-link>
		<button class="navbar-toggler" type="button" data-toggle="collapse"
			data-target="#navbarSupportedContent"
			aria-controls="navbarSupportedContent" aria-expanded="false"
			aria-label="Toggle navigation">
			<i class="fas fa-bars"></i>
		</button>

		<div class="collapse navbar-collapse" id="navbarSupportedContent">
			<ul class="navbar-nav mr-auto">
				<li class="nav-item">
					<router-link class="nav-link" to="/git"><icon name="code-branch"></icon>
					Git
					<loading-badge :icon="gitBadge.icon" :color="gitBadge.color" :title="gitBadge.title"/>
					</router-link>
				</li>
			</ul>
			<ul class="navbar-nav">
				<li class="nav-item">
					<form class="form-inline my-2 my-lg-0" v-on:submit.prevent="search()">
						<input class="form-control mr-sm-2 w-auto" type="search"
							placeholder="Search" aria-label="Search"
							v-model="searchQuery">
						<button class="btn btn-secondary my-2 my-sm-0" type="submit">
							<icon name="search"/>
						</button>
						<button class="btn btn-dark my-2 my-sm-0" type="button"
							title="Rebuild search index" @click="rebuildIndex()" :disabled="rebuildingIndex">
							<waiting-icon name="glasses" :waiting="rebuildingIndex"/>
						</button>
						<xsrf></xsrf>
					</form>
				</li>
				<li class="nav-item dropdown">
					<button class="btn btn-outline-secondary dropdown-toggle"
						type="button" id="currentUserDropdownButton"
						data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
						<i-label v-if="info != null" icon="user-circle" v-bind:text="info.userName"/>
					</button>
					<div class="dropdown-menu dropdown-menu-right" aria-labelledby="currentUserDropdownButton">
						<form class="form-inline" action="/logout" method="post">
							<xsrf></xsrf>
							<button class="dropdown-item" type="submit">
								<i-label icon="sign-out-alt" text="Exit"/>
							</button>
						</form>
						<form class="form-inline" action="/shutdown" method="post">
							<xsrf></xsrf>
							<button class="dropdown-item" type="submit">
								<i-label icon="power-off" text="Restart"/>
							</button>
						</form>
					</div>
				</li>
			</ul>
		</div>
	</nav>
	<global-messages/>
	<div><!-- workaround: without this div global-messages do not work --></div>
</header>

<main class="container-fluid">
	<router-view></router-view>
</main>
</div>
`, computed: {
	gitStatus() { return store.state.gitStatus; },
	gitBadge() { return store.state.gitBadge; },
	messages() { return store.state.messages; }
}, 
data () {
	return {
		username: 'loading',
		info: null,
		rebuildingIndex: false,
		searchQuery: null
	}
},
mounted () {
	appInfo.get(info => (this.info = info));
	store.commit('updateGitStatus');
},
methods: {
	rebuildIndex() {
		var t = this;
		t.rebuildingIndex = true;
		axios.post("/api/search/index")
		.then((response) => {
			t.rebuildingIndex = false;
		})
		.catch(displayError);
	},
	search() {
		var t = this;
		t.$router.push({ path: `/search/${this.searchQuery}` });
	}
}

};