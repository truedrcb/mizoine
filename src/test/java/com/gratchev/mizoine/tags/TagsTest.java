package com.gratchev.mizoine.tags;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Step 61, D65–D67: what a tag may be, and a folder's set of tags. */
class TagsTest {

	private static List<String> texts(final Tags tags) {
		return tags.list().stream().map(Tag::text).toList();
	}

	@Test
	void aTagIsAWordOfAnyAlphabet() {
		assertThat(Tag.of("project").text()).isEqualTo("project");
		assertThat(Tag.of("Projekt-Übersicht").text()).isEqualTo("Projekt-Übersicht");
		assertThat(Tag.of("проект_2026").text()).isEqualTo("проект_2026");
		assertThat(Tag.of("project/mizoine").text()).isEqualTo("project/mizoine");
		// a leading # is the hashtag form of the same tag
		assertThat(Tag.of("#project")).isEqualTo(Tag.of("project"));
	}

	@Test
	void tagsAreComparedWithoutCaseAndKeepTheirCase() {
		assertThat(Tag.of("Projekt")).isEqualTo(Tag.of("projekt")).hasSameHashCodeAs(Tag.of("PROJEKT"));
		assertThat(Tag.of("Projekt").text()).isEqualTo("Projekt");
	}

	@Test
	void whatIsNoTagIsSaid() {
		assertThatThrownBy(() -> Tag.of("")).isInstanceOf(IllegalArgumentException.class).hasMessage("A tag is empty.");
		assertThatThrownBy(() -> Tag.of("#")).isInstanceOf(IllegalArgumentException.class).hasMessage("A tag is empty.");
		assertThatThrownBy(() -> Tag.of("two words")).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Not a tag: \"two words\" (letters, digits, -, _, / and one : only).");
		assertThatThrownBy(() -> Tag.of("a:b:c")).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("one :");
		assertThatThrownBy(() -> Tag.of(":open")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Tag.of("status:")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Tag.of("x".repeat(51))).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("A tag can be at most 50 characters long: " + "x".repeat(51));
		assertThat(Tag.of("x".repeat(50)).text()).hasSize(50);
	}

	@Test
	void aStatusIsATagWithAKey() {
		assertThat(Tag.of("status:open").key()).hasValue("status");
		assertThat(Tag.of("Status:Open").key()).hasValue("status");
		assertThat(Tag.of("project").key()).isEmpty();
	}

	@Test
	void oneValuePerKeyTheLaterWins() {
		assertThat(texts(Tags.of(List.of("status:open", "project", "status:done")))).containsExactly("project", "status:done");
		assertThat(texts(Tags.of(List.of("Status:open", "status:Done")))).containsExactly("status:Done");
	}

	@Test
	void eachTagOnceTheFirstSpellingKept() {
		assertThat(texts(Tags.of(List.of("Projekt", "projekt", "#PROJEKT")))).containsExactly("Projekt");
	}

	@Test
	void atMost30Tags() {
		final List<String> thirty = java.util.stream.IntStream.range(0, 30).mapToObj(i -> "t" + i).toList();
		assertThat(Tags.of(thirty).list()).hasSize(30);
		final List<String> more = java.util.stream.IntStream.range(0, 31).mapToObj(i -> "t" + i).toList();
		assertThatThrownBy(() -> Tags.of(more)).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("A folder can have at most 30 tags.");
	}

	@Test
	void theFileFormatSpacesCommasLinesAndHashes() {
		final Tags read = Tags.read("project  status:active,\n#prio:high\r\n\n , urgent");

		assertThat(texts(read)).containsExactly("project", "status:active", "prio:high", "urgent");
		assertThat(Tags.read("").list()).isEmpty();
		assertThat(read.format()).isEqualTo("project\nstatus:active\nprio:high\nurgent\n");
		assertThat(Tags.read(read.format())).isEqualTo(read);
		assertThat(Tags.NONE.format()).isEmpty();
	}

	@Test
	void aHandEditedFileKeepsItsGoodTagsAndSaysWhichAreNot() {
		final Tags read = Tags.read("project two:colons:here ok");

		assertThat(texts(read)).containsExactly("project", "ok");
		assertThat(read.problems()).containsExactly("Not a tag: \"two:colons:here\" (letters, digits, -, _, / and one : only).");
		assertThat(Tags.of(List.of("project")).problems()).isEmpty();
	}
}
