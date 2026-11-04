import {initializeApp} from 'firebase/app';
import {getAuth, connectAuthEmulator} from 'firebase/auth';
import {getFirestore, connectFirestoreEmulator} from 'firebase/firestore';
// Local-only at this milestone. No credentials or production project are embedded.
export const app = initializeApp({projectId:'demo-arena',apiKey:'local-emulator-key',appId:'1:1234567890:web:arena-local'});
export const auth = getAuth(app);
export const db = getFirestore(app);
connectAuthEmulator(auth,`http://${location.hostname}:9099`,{disableWarnings:true});
connectFirestoreEmulator(db,location.hostname,8080);
