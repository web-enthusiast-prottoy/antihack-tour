package bd.arena.prototype;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Exercises the real history/feed controllers with SDK listener outcomes, without live writes. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MyMatchResultsTest {
    private AdminSession session;
    private FirebaseFirestore db;
    private MyMatchResults history;
    private final Map<String, com.google.firebase.firestore.EventListener<QuerySnapshot>> feeds = new HashMap<>();
    private final Map<String, com.google.firebase.firestore.EventListener<DocumentSnapshot>> documents = new HashMap<>();

    @Before public void setup() {
        session=mock(AdminSession.class);db=mock(FirebaseFirestore.class);
        when(session.getUid()).thenReturn("player");when(session.getDb()).thenReturn(db);
        for(String name:Arrays.asList("entries","resultLinks")) {
            CollectionReference query=mock(CollectionReference.class,RETURNS_SELF);
            when(db.collection(name)).thenReturn(query);
            doAnswer(call->{feeds.put(name,call.getArgument(1));return mock(ListenerRegistration.class);})
                .when(query).addSnapshotListener(eq(MetadataChanges.INCLUDE),org.mockito.ArgumentMatchers.<com.google.firebase.firestore.EventListener<QuerySnapshot>>any());
        }
        when(db.document(anyString())).thenAnswer(call->{
            String path=call.getArgument(0);DocumentReference ref=mock(DocumentReference.class);
            doAnswer(event->{documents.put(path,event.getArgument(1));return mock(ListenerRegistration.class);})
                .when(ref).addSnapshotListener(eq(MetadataChanges.INCLUDE),org.mockito.ArgumentMatchers.<com.google.firebase.firestore.EventListener<DocumentSnapshot>>any());
            return ref;
        });
        history=new MyMatchResults(session);history.load();
    }
    private DocumentSnapshot doc(String id,String...pairs) {
        DocumentSnapshot d=mock(DocumentSnapshot.class);Map<String,String> values=new HashMap<>();
        for(int i=0;i<pairs.length;i+=2)values.put(pairs[i],pairs[i+1]);
        when(d.getId()).thenReturn(id);when(d.exists()).thenReturn(true);
        when(d.getString(anyString())).thenAnswer(call->values.get(call.getArgument(0)));
        when(d.getMetadata()).thenReturn(mock(SnapshotMetadata.class));
        when(d.getTimestamp("startsAt")).thenReturn(new Timestamp(new Date(1000)));
        return d;
    }
    private void page(String feed,DocumentSnapshot...docs) {
        QuerySnapshot s=mock(QuerySnapshot.class);when(s.getDocuments()).thenReturn(Arrays.asList(docs));
        when(s.size()).thenReturn(docs.length);when(s.getMetadata()).thenReturn(mock(SnapshotMetadata.class));
        feeds.get(feed).onEvent(s,null);
    }
    private void confirmed(String...ids) {
        DocumentSnapshot[] entries=Arrays.stream(ids).map(id->doc("entry-"+id,"match",id,"status","confirmed")).toArray(DocumentSnapshot[]::new);
        page("entries",entries);page("resultLinks");
    }
    @Test public void removedMatchDoesNotBreakOtherParticipatedResults() {
        confirmed("kept","removed");
        documents.get("matches/kept").onEvent(doc("kept","title","Kept match","mode","solo","status","review"),null);
        documents.get("matches/removed").onEvent(null,new FirebaseFirestoreException("Missing public match",FirebaseFirestoreException.Code.PERMISSION_DENIED));
        assertFalse(history.getLoading());assertFalse(history.getError());
        assertTrue(history.getRecordsUnavailable());
        assertEquals(1,history.getMatches().size());assertEquals("kept",history.getMatches().get(0).getId());
    }
    @Test public void permissionRevocationRemovesPreviouslyReadMatchInsteadOfLeavingStaleData() {
        confirmed("one");
        documents.get("matches/one").onEvent(doc("one","title","Public match","status","open"),null);
        assertEquals(1,history.getMatches().size());
        documents.get("matches/one").onEvent(null,new FirebaseFirestoreException("Now private",FirebaseFirestoreException.Code.PERMISSION_DENIED));
        assertTrue(history.getMatches().isEmpty());assertFalse(history.getError());
    }
    @Test public void transientFailuresStillOfferRetry() {
        confirmed("one");
        documents.get("matches/one").onEvent(null,new FirebaseFirestoreException("Offline",FirebaseFirestoreException.Code.UNAVAILABLE));
        assertTrue(history.getError());assertFalse(history.getLoading());
    }
    @Test public void ownershipFeedErrorsAreNotHidden() {
        page("entries");feeds.get("resultLinks").onEvent(null,new FirebaseFirestoreException("Denied",FirebaseFirestoreException.Code.PERMISSION_DENIED));
        assertTrue(history.getError());assertFalse(history.getLoading());
    }
    @Test public void callbacksFromPreviousLoadCannotOverwriteNewHistory() {
        confirmed("old");com.google.firebase.firestore.EventListener<DocumentSnapshot> stale=documents.get("matches/old");
        history.load();confirmed("new");documents.get("matches/new").onEvent(doc("new","title","New match","status","review"),null);
        stale.onEvent(doc("old","title","Old match","status","review"),null);
        assertEquals(1,history.getMatches().size());assertEquals("new",history.getMatches().get(0).getId());
    }
    @Test public void publishedOwnershipLinkRestoresHistoryWithoutTheOriginalMatch() {
        confirmed("gone");
        documents.get("matches/gone").onEvent(null,new FirebaseFirestoreException("Removed",FirebaseFirestoreException.Code.PERMISSION_DENIED));
        page("resultLinks",doc("result-one","match","gone","result","result-one"));
        documents.get("results/result-one").onEvent(doc("result-one","title","Published result","mode","solo","status","settled"),null);
        assertFalse(history.getError());assertFalse(history.getRecordsUnavailable());
        assertTrue(history.getPublished().containsKey("gone"));assertEquals("Published result",history.getMatches().get(0).getTitle());
    }
    @Test public void emptyLegacyReferencesDoNotCreateInvalidDocumentReads() {
        page("entries",doc("legacy","status","confirmed"));page("resultLinks");
        verify(db,never()).document(anyString());assertFalse(history.getLoading());assertFalse(history.getError());
    }
}
