package com.gratchev.mizoine;

import static com.gratchev.mizoine.api.IssueApiController.MENTS_COMPARATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.gratchev.mizoine.api.IssueApiController.WithDescription;
import com.gratchev.mizoine.repository.Comment;
import com.gratchev.mizoine.repository.meta.CommentMeta;

public class IssueControlerMENTS_COMPARATORTest {
	final WithDescription empty = new WithDescription();
	final WithDescription emptyMent = new WithDescription();
	final WithDescription emptyMeta = new WithDescription();
	final WithDescription d1 = new WithDescription();
	final WithDescription d2 = new WithDescription();
	final WithDescription d0 = new WithDescription();
	final WithDescription id1 = new WithDescription();
	final WithDescription id2 = new WithDescription();
	final WithDescription id0 = new WithDescription();
	final List<WithDescription> sortedAll = Arrays.asList(d0, d1, d2, id0, id1, id2, emptyMeta);
	int permutationCounter;

	@Before
	public void setup() {
		emptyMent.comment = new Comment();

		init(emptyMeta);
		
		init(d0);
		init(d1);
		init(d2);
		init(id0);
		init(id1);
		init(id2);

		final Date date = new Date();

		d0.comment.meta.creationDate = new Date(date.getTime() + 10000); // Latest dates top
		d1.comment.meta.creationDate = date;
		d2.comment.meta.creationDate = new Date(date.getTime() - 10000); // Oldest dates bottom
		
		
		id0.comment.id = "ba2"; // Descending sort by id
		id1.comment.id = "ba1";
		id2.comment.id = "aa2";
	}

	private void init(WithDescription wd) {
		wd.comment = new Comment();
		wd.comment.meta = new CommentMeta();
	}
	
	@Test
	public void sort1() {
		final List<WithDescription> list = Arrays.asList(new WithDescription());
		list.sort(MENTS_COMPARATOR);
		assertThat(list).hasSize(1);
	}

	@Test
	public void compareEmpties() {
		assertThat(MENTS_COMPARATOR.compare(empty, empty)).isEqualTo(0);

		assertThat(MENTS_COMPARATOR.compare(empty, emptyMent)).isEqualTo(0);
		assertThat(MENTS_COMPARATOR.compare(emptyMent, empty)).isEqualTo(0);
		assertThat(MENTS_COMPARATOR.compare(empty, emptyMeta)).isEqualTo(0);
		assertThat(MENTS_COMPARATOR.compare(emptyMeta, empty)).isEqualTo(0);
		assertThat(MENTS_COMPARATOR.compare(emptyMent, emptyMeta)).isEqualTo(0);
		assertThat(MENTS_COMPARATOR.compare(emptyMeta, emptyMent)).isEqualTo(0);
	}
	
	@Test
	public void compareDateWithEmpty() {
		assertThat(MENTS_COMPARATOR.compare(d1, emptyMeta)).isLessThan(0);
		assertThat(MENTS_COMPARATOR.compare(emptyMeta, d1)).isGreaterThan(0);
		assertThat(MENTS_COMPARATOR.compare(d0, emptyMeta)).isLessThan(0);
		assertThat(MENTS_COMPARATOR.compare(emptyMeta, d0)).isGreaterThan(0);
		assertThat(MENTS_COMPARATOR.compare(d2, emptyMeta)).isLessThan(0);
		assertThat(MENTS_COMPARATOR.compare(emptyMeta, d2)).isGreaterThan(0);
	}

	@Test
	public void compareIdWithEmpty() {
		assertThat(MENTS_COMPARATOR.compare(id1, emptyMeta)).isLessThan(0);
		assertThat(MENTS_COMPARATOR.compare(emptyMeta, id1)).isGreaterThan(0);
		assertThat(MENTS_COMPARATOR.compare(id0, emptyMeta)).isLessThan(0);
		assertThat(MENTS_COMPARATOR.compare(emptyMeta, id0)).isGreaterThan(0);
		assertThat(MENTS_COMPARATOR.compare(id2, emptyMeta)).isLessThan(0);
		assertThat(MENTS_COMPARATOR.compare(emptyMeta, id2)).isGreaterThan(0);
	}

	@Test
	public void compareDateWithEmptyDates() {
		assertThat(MENTS_COMPARATOR.compare(d1, id1)).isLessThan(0);
		assertThat(MENTS_COMPARATOR.compare(id1, d1)).isGreaterThan(0);

		assertThat(MENTS_COMPARATOR.compare(d2, id1)).isLessThan(0);
		assertThat(MENTS_COMPARATOR.compare(id1, d2)).isGreaterThan(0);
	}

	@Test
	public void compareDates() {
		assertThat(MENTS_COMPARATOR.compare(d1, d2)).isLessThan(0);
		assertThat(MENTS_COMPARATOR.compare(d2, d1)).isGreaterThan(0);
		assertThat(MENTS_COMPARATOR.compare(d0, d2)).isLessThan(0);
		assertThat(MENTS_COMPARATOR.compare(d2, d0)).isGreaterThan(0);
		assertThat(MENTS_COMPARATOR.compare(d0, d1)).isLessThan(0);
		assertThat(MENTS_COMPARATOR.compare(d1, d0)).isGreaterThan(0);
	}

	@Test
	public void compareIds() {
		assertThat(MENTS_COMPARATOR.compare(id1, id2)).isLessThan(0);
		assertThat(MENTS_COMPARATOR.compare(id2, id1)).isGreaterThan(0);
		assertThat(MENTS_COMPARATOR.compare(id0, id2)).isLessThan(0);
		assertThat(MENTS_COMPARATOR.compare(id2, id0)).isGreaterThan(0);
		assertThat(MENTS_COMPARATOR.compare(id0, id1)).isLessThan(0);
		assertThat(MENTS_COMPARATOR.compare(id1, id0)).isGreaterThan(0);
	}
	
	
	private void permutation(final List<WithDescription> prefix, final List<WithDescription> suffix) {
		if (suffix.isEmpty()) {
			final ArrayList<WithDescription> sorted = new ArrayList<>(prefix);
			sorted.sort(MENTS_COMPARATOR);
			assertThat(sorted).containsExactlyElementsOf(sortedAll);
			permutationCounter ++;
			return;
		}
		
		for (final WithDescription c : suffix) {
			final ArrayList<WithDescription> prefixExt = new ArrayList<>(prefix);
			prefixExt.add(c);

			final ArrayList<WithDescription> suffixReduced = new ArrayList<>(suffix);
			assertTrue(suffixReduced.remove(c));
			permutation(prefixExt, suffixReduced);
		}
	}
	
	@Test
	public void sortEverything() {
		permutationCounter = 0;
		permutation(Arrays.asList(), sortedAll);
		
		assertThat(permutationCounter).isEqualTo(5040); // 7!
	}
}
