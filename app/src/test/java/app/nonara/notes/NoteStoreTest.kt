package app.nonara.notes

import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class NoteStoreTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun codecPreservesBodyAndExtensions() {
        val note = Note.create("\nÄpfel 🍎\r\n---\nend  \n").copy(extra = mapOf("custom" to "value"))
        assertEquals(note, NoteCodec.decode(NoteCodec.encode(note)))
    }
    @Test fun rejectsDuplicateMetadata() {
        val text = NoteCodec.encode(Note.create("body")).replace("title: null", "title: null\ntitle: null")
        assertThrows(IllegalArgumentException::class.java) { NoteCodec.decode(text) }
    }
    @Test fun rejectsFilenameMismatchWithoutDeletingSource() {
        val store = NoteStore(temp.root)
        val file = temp.root.resolve("notes/00000000-0000-0000-0000-000000000000.md")
        file.writeText(NoteCodec.encode(Note.create("keep")))
        assertTrue(store.list().isEmpty())
        assertTrue(file.exists())
        assertEquals(1, store.problems.size)
    }
    @Test fun savesReopensSearchesAndRestores() {
        val store = NoteStore(temp.root)
        val note = Note.create("Grüße Berlin")
        store.save(note, 2)
        store.save(note.copy(body = "old"), 1)
        assertEquals(note.body, NoteStore(temp.root).list().single().body)
        store.trash(note.id)
        assertTrue(store.list().isEmpty())
        store.restore(note.id)
        assertEquals(note, store.list().single())
    }
    @Test fun captureCommitIsIdempotentAndPreservesLaterEdits() {
        val store = NoteStore(temp.root)
        val note = Note.create("original")
        store.save(note, 1)
        val capture = newId()
        store.appendCapture(note.id, capture, "spoken", "original", 0)
        store.appendCapture(note.id, capture, "spoken", "original", 0)
        assertEquals("spoken original", store.list().single().body)
    }
    @Test fun changedAnchorAppendsInsteadOfReplacing() {
        val store = NoteStore(temp.root)
        val note = Note.create("edited")
        store.save(note, 1)
        store.appendCapture(note.id, newId(), "spoken", "old", 0)
        assertEquals("edited\n\nspoken", store.list().single().body)
    }

    @Test fun recoveryJournalSurvivesRestartBeforeSave() {
        val store = NoteStore(temp.root)
        val note = Note.create("draft")
        store.journal(note, 1)
        assertEquals("draft", NoteStore(temp.root).list().single().body)
    }
    @Test fun staleEditorCannotOverwriteTranscription() {
        val store = NoteStore(temp.root)
        val note = Note.create("original")
        store.save(note, 1)
        store.appendCapture(note.id, newId(), "spoken", null, null)
        assertThrows(IllegalStateException::class.java) {
            store.saveEdited(note.copy(body="typed"), 100, "original")
        }
        assertEquals("original\n\nspoken", store.get(note.id)!!.body)
    }
    @Test fun aliasesNestedMapsFutureSchemasAndInvalidDatesAreRejected() {
        val raw=NoteCodec.encode(Note.create("body"))
        for(bad in listOf(raw.replace("schema_version: 1","schema_version: 2"),
            raw.replace("title: null","title: {nested: value}"),
            raw.replace("title: null","title: &anchor hi\nother: *anchor"),
            raw.replace(Regex("created_at: .*"),"created_at: invalid"))) {
            assertThrows(IllegalArgumentException::class.java) { NoteCodec.decode(bad) }
        }
    }
    @Test fun bomCrlfAndQuotedYamlCharactersRoundTrip() {
        val note=Note.create("\nbody\n").copy(title="hello &world ! text")
        val encoded=NoteCodec.encode(note)
        assertEquals(note,NoteCodec.decode("\uFEFF"+encoded))
        assertEquals("body",NoteCodec.decode(NoteCodec.encode(Note.create("body")).replace("\n","\r\n")).body)
    }
}
