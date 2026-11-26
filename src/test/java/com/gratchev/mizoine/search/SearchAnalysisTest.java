package com.gratchev.mizoine.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Step 55: how text is cut into searchable words. "Found by" means: every word of the query, analyzed for a field,
 * is among the words of the text analyzed for the same field (as the index and the query will do it).
 */
class SearchAnalysisTest {

	private final SearchAnalysis analysis = SearchAnalysis.of(Language.parse("en,de,fr,ru"));

	private boolean found(final String field, final String text, final String query) {
		final List<String> words = analysis.terms(field, text);
		final List<String> asked = analysis.terms(field, query);
		return !asked.isEmpty() && words.containsAll(asked);
	}

	@Test
	void germanSpellingsOfALetterAreOne() {
		assertThat(analysis.terms(SearchAnalysis.EXACT, "Äpfel Aepfel Apfel apfel")).containsOnly("apfel");
		assertThat(analysis.terms(SearchAnalysis.EXACT, "Straße Strasse")).containsOnly("strasse");
		assertThat(analysis.terms(SearchAnalysis.EXACT, "Café Cafe")).containsOnly("cafe");
		// "ue" after a vowel is no "ü"
		assertThat(analysis.terms(SearchAnalysis.EXACT, "Feuer")).containsExactly("feuer");
	}

	@Test
	void wordFormsPerLanguage() {
		assertThat(found("de", "Häuser", "Haus")).isTrue();
		assertThat(found("en", "running meetings", "meeting")).isTrue();
		assertThat(found("fr", "l'école", "écoles")).isTrue();
		assertThat(found("ru", "книги", "книга")).isTrue();
		// only through the stems: the words as written differ
		assertThat(found(SearchAnalysis.EXACT, "Häuser", "Haus")).isFalse();
	}

	@Test
	void frenchAccentsAndElision() {
		assertThat(found(SearchAnalysis.EXACT, "l'école", "ecole")).isTrue();
		assertThat(found(SearchAnalysis.EXACT, "qu'il", "il")).isTrue();
		assertThat(found(SearchAnalysis.EXACT, "Noël", "noel")).isTrue();
		assertThat(found(SearchAnalysis.EXACT, "cœur", "coeur")).isTrue();
	}

	@Test
	void russianLetters() {
		assertThat(found(SearchAnalysis.EXACT, "ёлка", "елка")).isTrue();
		assertThat(found(SearchAnalysis.EXACT, "Москва", "москва")).isTrue();
	}

	@Test
	void onlyTheChosenLanguagesAreStemmed() {
		final SearchAnalysis germanEnglish = SearchAnalysis.of(Language.parse("en,de"));

		assertThat(germanEnglish.textFields()).containsExactly(SearchAnalysis.EXACT, "en", "de");
		assertThat(germanEnglish.textFields().stream()
				.anyMatch(field -> germanEnglish.terms(field, "книги").contains(germanEnglish.terms(field, "книга").getFirst())))
				.isFalse();
		// another list of languages is another index format: the index must be built anew
		assertThat(germanEnglish.format()).isNotEqualTo(analysis.format());
		assertThat(SearchAnalysis.of(Language.parse("de, en")).format()).isEqualTo(germanEnglish.format());
	}

	@Test
	void unknownLanguagesAreRefusedWithTheKnownOnes() {
		assertThatThrownBy(() -> Language.parse("en,xx"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("xx").hasMessageContaining("en, de, fr, ru");
		assertThatThrownBy(() -> Language.parse(" ")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void emailAddressesAndUrlsWholeAndInParts() {
		final String text = "Write to artem@example.org, see https://example.org/notes.";

		assertThat(found(SearchAnalysis.TOKENS, text, "artem@example.org")).isTrue();
		assertThat(found(SearchAnalysis.TOKENS, text, "https://example.org/notes")).isTrue();
		assertThat(found(SearchAnalysis.EXACT, text, "example.org")).isTrue();
		assertThat(found(SearchAnalysis.EXACT, text, "artem")).isTrue();
	}

	@Test
	void fileNamesWholeAndInParts() {
		assertThat(analysis.terms(SearchAnalysis.TOKENS, "(siehe bericht-2026.pdf).")).containsExactly("siehe", "bericht-2026.pdf");
		assertThat(found(SearchAnalysis.EXACT, "bericht-2026.pdf", "bericht")).isTrue();
		assertThat(found(SearchAnalysis.EXACT, "bericht-2026.pdf", "pdf")).isTrue();

		assertThat(analysis.terms(SearchAnalysis.NAME, "Bericht 2026-11.pdf")).containsExactly("bericht 2026-11.pdf");
		assertThat(analysis.terms(SearchAnalysis.NAME_PARTS, "Bericht 2026-11.pdf")).contains("bericht", "2026", "11", "pdf");
		assertThat(found(SearchAnalysis.NAME, "Übersicht.md", "uebersicht.md")).isTrue();
		assertThat(found(SearchAnalysis.NAME_PARTS, "MeetingNotes.md", "meeting notes")).isTrue();
		assertThat(found(SearchAnalysis.NAME_PARTS, "my_notes-v2.txt", "notes")).isTrue();
	}

	/**
	 * Fails whenever the analysis changes: then raise {@link SearchAnalysis#ANALYSIS_VERSION} (old indexes must be
	 * built anew, D58) and update these words together.
	 */
	@Test
	void theAnalysisOfThisVersion() {
		assertThat(SearchAnalysis.ANALYSIS_VERSION).isEqualTo(2);
		assertThat(analysis.format()).isEqualTo("a2-de-en-fr-ru");
		// version 2: folder tags, whole and folded like names
		assertThat(analysis.terms(SearchAnalysis.TAG, "Status:Erledigt-Ä")).containsExactly("status:erledigt-a");
		final String text = "Die Häuser von l'École: Ёлки, cœur! Meetings at artem@example.org (bericht-2026.pdf).";
		assertThat(Map.of(
				SearchAnalysis.EXACT, analysis.terms(SearchAnalysis.EXACT, text),
				"en", analysis.terms("en", text),
				"de", analysis.terms("de", text),
				"fr", analysis.terms("fr", text),
				"ru", analysis.terms("ru", text),
				SearchAnalysis.TOKENS, analysis.terms(SearchAnalysis.TOKENS, text)))
				.isEqualTo(Map.of(
						SearchAnalysis.EXACT, List.of("die", "hauser", "von", "ecole", "елки", "cour", "meetings", "at", "artem",
								"example.org", "bericht", "2026", "pdf"),
						"en", List.of("die", "hauser", "von", "ecol", "елки", "cour", "meet", "at", "artem", "example.org",
								"bericht", "2026", "pdf"),
						"de", List.of("die", "haus", "von", "ecol", "елки", "cour", "meeting", "at", "art", "example.org",
								"bericht", "2026", "pdf"),
						"fr", List.of("die", "haus", "von", "ecol", "елки", "cour", "meting", "at", "artem", "example.org",
								"bericht", "2026", "pdf"),
						"ru", List.of("die", "hauser", "von", "ecole", "елк", "cour", "meetings", "at", "artem", "example.org",
								"bericht", "2026", "pdf"),
						SearchAnalysis.TOKENS, List.of("die", "hauser", "von", "l'ecole", "елки", "cour", "meetings", "at",
								"artem@example.org", "bericht-2026.pdf")));
		assertThat(analysis.terms(SearchAnalysis.NAME_PARTS, "MeetingNotes 2026.md")).containsExactly("meeting", "notes", "2026", "md");
	}
}
