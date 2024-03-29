class InfoCache {
constructor(uri) {
	var callbacks = null;
	var data = null;
	
	this.get = function(callback) {
		if (data) {
			callback(data);
		} else {
			if (callbacks) {
				callbacks = {callback : callback, next : callbacks};
			} else {
				callbacks = {callback : callback};
				axios.get(uri).then(response => {
					data = this.load(response.data);
					data.timestamp = Date.now();
					while (callbacks) {
						callbacks.callback(data);
						callbacks = callbacks.next;
					}
				}).catch(error => {
					console.error("Caching GET failed");
					console.error(error);
				});
			}
		}
		return data;
	}
}

load(data) {
	return data;
}
}


class AppInfo extends InfoCache {
	
constructor() {
	super('/api/app');
}

load(data) {
	var tags = {};
	for (var tname in data.repositoryMeta.tags ) {
		var tag = data.repositoryMeta.tags[tname]
		tags[tname] = tag;
		if (tag.synonyms) {
			for (var i = 0; i < tag.synonyms.length; i++) {
				tags[tag.synonyms[i]] = tag;
			}
		}
	}
	const finishedName = "finished";
	data.finishedStatuses = {};
	data.finishedStatuses[finishedName] = true;
	for (var tname in data.repositoryMeta.statuses ) {
		var tag = data.repositoryMeta.statuses[tname]
		tags[tname] = tag;
		if (tag.synonyms) {
			var isFinishedSynonym = tname === finishedName;
			for (var i = 0; i < tag.synonyms.length; i++) {
				var sname = tag.synonyms[i];
				if (sname === finishedName) {
					isFinishedSynonym = true;
				}
				tags[sname] = tag;
			}
			if (isFinishedSynonym) {
				for (var i = 0; i < tag.synonyms.length; i++) {
					data.finishedStatuses[tag.synonyms[i]] = true;
				}
			}
		}
	}
	data.tags = tags;
	console.log(data);
	return data;
}
}
const appInfo = new AppInfo();


class ProjectsShortInfo extends InfoCache {
constructor() {
	super('/api/projects');
}
}
const projectsShortInfo = new ProjectsShortInfo();

class ProjectsInfo {
	constructor() {
		var projectsData = {};
		
		this.get = function (project, callback) {
			if (!projectsData[project]) {
				projectsData[project] = new InfoCache("/api/project/" + project + "/info");
			}
			return projectsData[project].get(callback);
		}
	}
}
const projectsInfo = new ProjectsInfo();

class IssuesInfo {
	constructor() {
		var issuesData = {};
		
		this.get = function (project, issueNumber, callback) {
			var projectIssue = project + "-" + issueNumber;
			if (!issuesData[projectIssue]) {
				issuesData[projectIssue] = new InfoCache("/api/issue/" + projectIssue + "/info");
			}
			return issuesData[projectIssue].get(callback);
		}

		this.clear = function (project, issueNumber) {
			var projectIssue = project + "-" + issueNumber;
			console.log("Reset issue: " + projectIssue);
			issuesData[projectIssue] = null;
		}
		
		this.reload = function (project, issueNumber, callback) {
			this.clear(project, issueNumber);
			return this.get(project, issueNumber, callback);
		}
	}
}
const issuesInfo = new IssuesInfo();

var projects = null;
