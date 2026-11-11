package bd.arena.prototype;

import android.os.Looper;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class WalletRefreshTest {
    @Test public void refreshReadsServerOnceWithoutReplacingLiveBalanceOrWritingMoney() {
        AdminSession session=mock(AdminSession.class);FirebaseFirestore db=mock(FirebaseFirestore.class);DocumentReference wallet=mock(DocumentReference.class);
        when(session.getUid()).thenReturn("player");when(session.getDb()).thenReturn(db);when(db.document("wallets/player")).thenReturn(wallet);
        when(wallet.get(Source.SERVER)).thenReturn(Tasks.forResult(mock(DocumentSnapshot.class)));
        PlayerData data=new PlayerData(session);data.setBalance(45000);data.setWinnings(25000);
        data.refreshWallet();data.refreshWallet();Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertFalse(data.getWalletRefreshing());assertEquals(45000,data.getBalance());assertEquals(25000,data.getWinnings());
        verify(wallet,times(1)).get(Source.SERVER);verifyNoMoreInteractions(wallet);
        verify(db,times(1)).document("wallets/player");verifyNoMoreInteractions(db);
    }
    @Test public void failedRefreshKeepsSavedValuesAndCanRetry() {
        AdminSession session=mock(AdminSession.class);FirebaseFirestore db=mock(FirebaseFirestore.class);DocumentReference wallet=mock(DocumentReference.class);
        when(session.getUid()).thenReturn("player");when(session.getDb()).thenReturn(db);when(db.document("wallets/player")).thenReturn(wallet);
        when(wallet.get(Source.SERVER)).thenReturn(Tasks.forException(new FirebaseFirestoreException("Offline",FirebaseFirestoreException.Code.UNAVAILABLE)));
        PlayerData data=new PlayerData(session);data.setBalance(45000);data.setBalanceCached(false);
        data.refreshWallet();Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertFalse(data.getWalletRefreshing());assertTrue(data.getBalanceCached());assertEquals(45000,data.getBalance());assertFalse(data.getError().isEmpty());
        when(wallet.get(Source.SERVER)).thenReturn(Tasks.forResult(mock(DocumentSnapshot.class)));
        data.refreshWallet();Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals("",data.getError());
    }
}
