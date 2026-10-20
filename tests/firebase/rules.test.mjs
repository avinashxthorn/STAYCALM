import { readFileSync } from 'node:fs';
import { before, after, beforeEach, test } from 'node:test';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, setDoc, getDoc, deleteDoc, serverTimestamp } from 'firebase/firestore';
let env;
const path = 'users/alice/backups/phone';
const backup = () => ({ schema: 1, payload: '{"schema":1,"values":{"plans":"[]","logs":"[]"}}', label: 'Phone', updatedAt: serverTimestamp() });
const db = (uid, verified = true) => env.authenticatedContext(uid, { email_verified: verified }).firestore();
before(async () => { env = await initializeTestEnvironment({ projectId: 'demo-staycalm', firestore: { rules: readFileSync('../../firestore.rules', 'utf8') } }); });
after(async () => { await env.cleanup(); });
beforeEach(async () => { await env.clearFirestore(); });
test('only verified owner can create, read and update a backup', async () => {
  await assertSucceeds(setDoc(doc(db('alice'), path), backup()));
  await assertSucceeds(getDoc(doc(db('alice'), path)));
  await assertSucceeds(setDoc(doc(db('alice'), path), backup()));
  await assertFails(getDoc(doc(db('bob'), path)));
  await assertFails(setDoc(doc(db('bob'), path), backup()));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), path)));
  await assertFails(setDoc(doc(db('alice', false), path), backup()));
});
test('rejects extra fields, malformed types, oversize and client timestamps', async () => {
  const ref = doc(db('alice'), path);
  await assertFails(setDoc(ref, { ...backup(), token: 'no' }));
  await assertFails(setDoc(ref, { ...backup(), payload: [] }));
  await assertFails(setDoc(ref, { ...backup(), payload: 'x'.repeat(750001) }));
  await assertFails(setDoc(ref, { ...backup(), updatedAt: new Date(0) }));
});
test('client cannot delete recovery copies or write outside allowed path', async () => {
  await assertSucceeds(setDoc(doc(db('alice'), path), backup()));
  await assertFails(deleteDoc(doc(db('alice'), path)));
  await assertFails(setDoc(doc(db('alice'), 'users/alice'), { public: true }));
});

test('planner file uploads and cloud reads are disabled', async () => {
  const filePath = `users/alice/plannerFiles/${'a'.repeat(32)}`;
  const chunkPath = `${filePath}/chunks/0`;
  // Existing server data is retained, but is no longer accessible to app clients.
  await env.withSecurityRulesDisabled(async context => {
    await setDoc(doc(context.firestore(), filePath), { complete: true });
    await setDoc(doc(context.firestore(), chunkPath), { data: 'old bytes' });
  });
  await assertFails(getDoc(doc(db('alice'), filePath)));
  await assertFails(getDoc(doc(db('alice'), chunkPath)));
  await assertFails(setDoc(doc(db('alice'), chunkPath), { data: 'new bytes' }));
  await assertFails(setDoc(doc(db('alice'), filePath), { complete: true }));
  await assertFails(getDoc(doc(db('bob'), chunkPath)));
  await assertFails(deleteDoc(doc(db('alice'), chunkPath)));
  await assertSucceeds(setDoc(doc(db('alice'), path), backup()));
});
