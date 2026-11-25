package com.gratchev.mizoine.git;

import java.nio.file.Path;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import com.gratchev.mizoine.ApiTexts;
import com.gratchev.mizoine.repositories.RepositoryFiles;
import com.gratchev.mizoine.users.UserEmails;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Git in the browser (steps 43–48): {@code /{repository}/.git/} is a reserved address, never the {@code .git}
 * folder, which stays unreachable as files (D3). For users with Git access (grant {@code .git} or {@code /}, D33);
 * for everyone else 404, as for anything not visible (D2).
 */
@Controller
@Tag(name = "Git")
class GitController {

	private static final String REPOSITORY = "repository";
	private static final String NOT_FOUND = "Unknown repository, or no Git access to it: deliberately the same answer";
	private static final String PROBLEM = "Repository configured wrongly (e.g. missing root folder); or Git failed";
	private static final String NOT_A_REPOSITORY = "The repository's folder is not the top of a Git working tree";
	private static final String TIMEOUT = "The remote did not answer within the time limit; Git was ended";
	private static final String REFUSED = "Not done: not a Git repository, a file not among the changes, nothing to do, "
			+ "or Git refused; the message says why";

	private static final String OUTCOME = "outcome";
	private static final String SELECTION = "Either 'paths' (one or more, as the status names them) or 'all': true";

	private final RepositoryFiles files;
	private final GitCommands git;
	private final GitActions actions;
	private final UserEmails emails;

	GitController(final RepositoryFiles files, final GitCommands git, final GitActions actions, final UserEmails emails) {
		this.files = files;
		this.git = git;
		this.actions = actions;
		this.emails = emails;
	}

	@GetMapping(value = "/{repository}/.git/", produces = MediaType.TEXT_HTML_VALUE)
	@Operation(operationId = "gitStatus", summary = "Git status of a repository",
			description = "The Git status: branch, upstream with ahead and behind, the staged and the not "
					+ "staged changes, conflicts; an HTML page (default) or JSON when asked for. File names link to "
					+ "their pages where the user may read them. Only for users with Git access: grant '.git' or '/' "
					+ "in the repository file; the status covers the whole repository. A repository whose folder is not "
					+ "the top of a Git working tree has no status: the page says so, JSON gives 409.")
	@ApiResponse(responseCode = "200", description = "The Git page, or the status as JSON", content = {
			@Content(mediaType = MediaType.TEXT_HTML_VALUE),
			@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = GitStatus.class))})
	@ApiResponse(responseCode = "302", description = ApiTexts.LOGIN_REDIRECT, content = @Content)
	@ApiResponse(responseCode = "401", description = ApiTexts.NOT_LOGGED_IN, content = @Content)
	@ApiResponse(responseCode = "404", description = NOT_FOUND, content = @Content)
	@ApiResponse(responseCode = "409", description = NOT_A_REPOSITORY + " (JSON only)", content = @Content)
	@ApiResponse(responseCode = "500", description = PROBLEM, content = @Content)
	String page(@PathVariable(REPOSITORY) @Parameter(description = "Repository name") final String repository,
			final Principal principal, final Model model) {
		final String user = principal.getName();
		final Optional<GitStatus> status = git.status(files.gitRoot(user, repository));
		model.addAttribute(REPOSITORY, repository);
		model.addAttribute("git", status.map(found -> GitStatusView.of(found, files.fileLinks(user, repository))).orElse(null));
		return "git";
	}

	@GetMapping(value = "/{repository}/.git/", produces = MediaType.APPLICATION_JSON_VALUE)
	@Hidden // documented together with the HTML variant on page(...)
	@ResponseBody
	GitStatus status(@PathVariable(REPOSITORY) final String repository, final Principal principal) {
		return git.status(files.gitRoot(principal.getName(), repository))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, NOT_A_REPOSITORY));
	}

	// the actions (steps 46–48): JSON for API clients, the page's forms for the browser

	// no "produces": a client without an Accept header must reach it too, not the file move on the same pattern
	@PostMapping(value = "/{repository}/.git/stage", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(operationId = "gitStage", summary = "Stage changes",
			description = "Stages changed, new or deleted files for the next commit (git add). Only files of the "
					+ "current status can be named; conflicts are resolved by hand on the server. " + SELECTION + ".")
	@ApiResponse(responseCode = "200", description = "Staged; the new status",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = GitStatus.class)))
	@ApiResponse(responseCode = "400", description = SELECTION, content = @Content)
	@ApiResponse(responseCode = "401", description = ApiTexts.NOT_LOGGED_IN, content = @Content)
	@ApiResponse(responseCode = "404", description = NOT_FOUND, content = @Content)
	@ApiResponse(responseCode = "409", description = REFUSED, content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			schema = @Schema(implementation = Refusal.class)))
	@ApiResponse(responseCode = "500", description = PROBLEM, content = @Content)
	ResponseEntity<Object> stage(@PathVariable(REPOSITORY) @Parameter(description = "Repository name") final String repository,
			@RequestBody final SelectionRequest request, final Principal principal) {
		return act(repository, principal, root -> actions.stage(root, request.selection()));
	}

	// no "produces": a client without an Accept header must reach it too, not the file move on the same pattern
	@PostMapping(value = "/{repository}/.git/unstage", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(operationId = "gitUnstage", summary = "Unstage changes",
			description = "Takes staged files out of the next commit (git restore --staged); their changes stay in "
					+ "the working tree. A renamed file is unstaged with its old name. " + SELECTION + ".")
	@ApiResponse(responseCode = "200", description = "Unstaged; the new status",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = GitStatus.class)))
	@ApiResponse(responseCode = "400", description = SELECTION, content = @Content)
	@ApiResponse(responseCode = "401", description = ApiTexts.NOT_LOGGED_IN, content = @Content)
	@ApiResponse(responseCode = "404", description = NOT_FOUND, content = @Content)
	@ApiResponse(responseCode = "409", description = REFUSED, content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			schema = @Schema(implementation = Refusal.class)))
	@ApiResponse(responseCode = "500", description = PROBLEM, content = @Content)
	ResponseEntity<Object> unstage(@PathVariable(REPOSITORY) @Parameter(description = "Repository name") final String repository,
			@RequestBody final SelectionRequest request, final Principal principal) {
		return act(repository, principal, root -> actions.unstage(root, request.selection()));
	}

	// no "produces": see stage(...)
	@PostMapping(value = "/{repository}/.git/commit", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(operationId = "gitCommit", summary = "Commit the staged changes",
			description = "Commits what is staged (git commit), with the logged-in user as author (name, and the "
					+ "email from the users file, else <name>@mizoine); the committer is the server's own Git identity. "
					+ "The repository's hooks run as with a commit by hand.")
	@ApiResponse(responseCode = "200", description = "Committed; the new status",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = GitStatus.class)))
	@ApiResponse(responseCode = "400", description = "No message", content = @Content)
	@ApiResponse(responseCode = "401", description = ApiTexts.NOT_LOGGED_IN, content = @Content)
	@ApiResponse(responseCode = "404", description = NOT_FOUND, content = @Content)
	@ApiResponse(responseCode = "409", description = REFUSED + " (e.g. nothing staged, a hook refused)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = Refusal.class)))
	@ApiResponse(responseCode = "500", description = PROBLEM, content = @Content)
	ResponseEntity<Object> commit(@PathVariable(REPOSITORY) @Parameter(description = "Repository name") final String repository,
			@RequestBody final CommitRequest request, final Principal principal) {
		return act(repository, principal,
				root -> actions.commit(root, new CommitMessage(request.message()), author(principal.getName())));
	}

	// no "produces": see stage(...); the body may be empty
	@PostMapping(value = "/{repository}/.git/pull", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(operationId = "gitPull", summary = "Pull from the upstream",
			description = "Fast-forwards the current branch to its upstream (git pull --ff-only), never a merge or a "
					+ "rebase: a branch that has diverged is refused, to be resolved by hand on the server. Mizoine's own "
					+ "file writes wait while it runs. The remote is reached with the server's own Git credentials. "
					+ "Send Content-Type: application/json; the body may be empty.")
	@ApiResponse(responseCode = "200", description = "Pulled; the new status",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = GitStatus.class)))
	@ApiResponse(responseCode = "401", description = ApiTexts.NOT_LOGGED_IN, content = @Content)
	@ApiResponse(responseCode = "404", description = NOT_FOUND, content = @Content)
	@ApiResponse(responseCode = "409", description = REFUSED + " (e.g. no upstream, diverged)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = Refusal.class)))
	@ApiResponse(responseCode = "500", description = PROBLEM, content = @Content)
	@ApiResponse(responseCode = "504", description = TIMEOUT, content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			schema = @Schema(implementation = Refusal.class)))
	ResponseEntity<Object> pull(@PathVariable(REPOSITORY) @Parameter(description = "Repository name") final String repository,
			final Principal principal) {
		return act(repository, principal, actions::pull);
	}

	@PostMapping(value = "/{repository}/.git/push", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(operationId = "gitPush", summary = "Push to the upstream",
			description = "Pushes the current branch to its upstream branch only, never forced: a push that is "
					+ "behind is rejected (pull first). The remote is reached with the server's own Git credentials. "
					+ "Send Content-Type: application/json; the body may be empty.")
	@ApiResponse(responseCode = "200", description = "Pushed; the new status",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = GitStatus.class)))
	@ApiResponse(responseCode = "401", description = ApiTexts.NOT_LOGGED_IN, content = @Content)
	@ApiResponse(responseCode = "404", description = NOT_FOUND, content = @Content)
	@ApiResponse(responseCode = "409", description = REFUSED + " (e.g. no upstream, rejected)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = Refusal.class)))
	@ApiResponse(responseCode = "500", description = PROBLEM, content = @Content)
	@ApiResponse(responseCode = "504", description = TIMEOUT, content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			schema = @Schema(implementation = Refusal.class)))
	ResponseEntity<Object> push(@PathVariable(REPOSITORY) @Parameter(description = "Repository name") final String repository,
			final Principal principal) {
		return act(repository, principal, actions::push);
	}

	/** The JSON body of a commit. */
	record CommitRequest(@Schema(description = "The commit message; not empty") String message) {
	}

	private Author author(final String user) {
		return Author.of(user, emails.email(user));
	}

	/** Why an action was not done, often in Git's own words. */
	record Refusal(@Schema(description = "Why, often in Git's own words") String message) {
	}

	/** The JSON body of stage and unstage, read once into a {@link Selection}. */
	record SelectionRequest(
			@Schema(description = "Paths as the status names them, from the repository root") List<String> paths,
			@Schema(description = "true: every file of the list instead") Boolean all) {

		/** @throws IllegalArgumentException if it names nothing */
		Selection selection() {
			return new Selection(paths, Boolean.TRUE.equals(all));
		}
	}

	/** An action of the JSON API: only with Git access (404 otherwise), then the new status or why not. */
	private ResponseEntity<Object> act(final String repository, final Principal principal,
			final Function<Path, GitOutcome> action) {
		final Path root = files.gitRoot(principal.getName(), repository);
		return answer(root, action.apply(root));
	}

	/**
	 * Always JSON, also without an Accept header: the type is set here, so the default of the pages (HTML when the
	 * request states no preference) does not apply.
	 */
	private ResponseEntity<Object> answer(final Path root, final GitOutcome outcome) {
		return switch (outcome) {
			case GitOutcome.Done done -> json(HttpStatus.OK, git.status(root)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, NOT_A_REPOSITORY)));
			case GitOutcome.Refused refused -> json(HttpStatus.CONFLICT, new Refusal(refused.message()));
		};
	}

	private static ResponseEntity<Object> json(final HttpStatus status, final Object body) {
		return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
	}

	/** The page's forms: the action, then back to the page (Post/Redirect/Get, 303), which shows the result. */
	@PostMapping(value = "/{repository}/.git/{action:stage|unstage|commit|pull|push}",
			consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
	@Hidden // the browser's way to the operations documented above
	RedirectView form(@PathVariable(REPOSITORY) final String repository, @PathVariable("action") final String action,
			@RequestParam(name = "path", required = false) final List<String> paths,
			@RequestParam(name = "all", defaultValue = "false") final boolean all,
			@RequestParam(name = "message", required = false) final String message,
			final Principal principal, final RedirectAttributes redirect) {
		final Path root = files.gitRoot(principal.getName(), repository);
		redirect.addFlashAttribute(OUTCOME, outcome(root, action, new Form(paths, all, message), principal.getName()));
		final RedirectView page = new RedirectView("/" + repository + "/.git/");
		page.setStatusCode(HttpStatus.SEE_OTHER);
		return page;
	}

	/** The fields of the page's forms; each action reads its own. */
	private record Form(List<String> paths, boolean all, String message) {
	}

	/** Input that names nothing, and a remote that does not answer, are said on the page, as Git's refusals are. */
	private GitOutcome outcome(final Path root, final String action, final Form form, final String user) {
		try {
			return switch (action) {
				case "stage" -> actions.stage(root, new Selection(form.paths(), form.all()));
				case "unstage" -> actions.unstage(root, new Selection(form.paths(), form.all()));
				case "commit" -> actions.commit(root, new CommitMessage(form.message()), author(user));
				case "pull" -> actions.pull(root);
				default -> actions.push(root);
			};
		} catch (final IllegalArgumentException | GitTimeoutException e) {
			return new GitOutcome.Refused(e.getMessage());
		}
	}

	/** A request body that is no JSON of this form. */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<Object> unreadable(final HttpMessageNotReadableException e) {
		return json(HttpStatus.BAD_REQUEST, new Refusal("Unreadable request: " + SELECTION));
	}

	/**
	 * A request that names nothing (no files, not all, no message): 400, the message says what is missing. A remote
	 * that did not answer in time: 504, Git was ended.
	 */
	@ExceptionHandler({IllegalArgumentException.class, GitTimeoutException.class})
	ResponseEntity<Object> notDone(final RuntimeException e) {
		return json(e instanceof GitTimeoutException ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_REQUEST,
				new Refusal(e.getMessage()));
	}

	@GetMapping(value = "/{repository}/.git", produces = MediaType.TEXT_HTML_VALUE)
	@Operation(operationId = "gitRedirect", summary = "Git page without trailing slash",
			description = "Redirects to /{repository}/.git/ for users with Git access (folder URLs end with '/').")
	@ApiResponse(responseCode = "302", description = "Redirect to /{repository}/.git/; or " + ApiTexts.LOGIN_REDIRECT,
			content = @Content)
	@ApiResponse(responseCode = "401", description = ApiTexts.NOT_LOGGED_IN, content = @Content)
	@ApiResponse(responseCode = "404", description = NOT_FOUND, content = @Content)
	@ApiResponse(responseCode = "500", description = PROBLEM, content = @Content)
	String withoutTrailingSlash(@PathVariable(REPOSITORY) @Parameter(description = "Repository name") final String repository,
			final Principal principal) {
		files.gitRoot(principal.getName(), repository);
		return "redirect:/" + repository + "/.git/";
	}
}
