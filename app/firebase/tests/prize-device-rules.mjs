import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
const require=createRequire(new URL('../../admin/package.json',import.meta.url));
const {initializeTestEnvironment}=require('@firebase/rules-unit-testing');
// Installs rules only on the hardcoded local preview emulator. No data is cleared.
const env=await initializeTestEnvironment({projectId:'demo-arena',firestore:{host:'127.0.0.1',port:8080,rules:await readFile(new URL('../firestore.rules',import.meta.url),'utf8')}});
await env.cleanup();
console.log('Current rules installed on demo-arena localhost:8080; preview data preserved.');
