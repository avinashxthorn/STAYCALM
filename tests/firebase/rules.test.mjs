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

test('planner bytes and file manifests are private and immutable when complete', async () => {
  const { Bytes, writeBatch } = await import('firebase/firestore');
  const id = 'a'.repeat(32);
  const filePath = `users/alice/plannerFiles/${id}`;
  const chunkPath = `${filePath}/chunks/0`;
  const data = { data: Bytes.fromUint8Array(new Uint8Array([1, 2, 3])) };
  const state = { size: 3, sha256: 'b'.repeat(64), chunks: 1, uploaded: 1, complete: true };
  const ownDb = db('alice');
  const batch = writeBatch(ownDb);
  batch.set(doc(ownDb, chunkPath), data);
  batch.set(doc(ownDb, filePath), state);
  await assertSucceeds(batch.commit());
  await assertSucceeds(getDoc(doc(db('alice'), chunkPath)));
  await assertSucceeds(setDoc(doc(db('alice'), chunkPath), data)); // Retry of same bytes.
  await assertFails(getDoc(doc(db('bob'), chunkPath)));
  await assertFails(getDoc(doc(db('alice', false), filePath)));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), chunkPath)));
  await assertFails(setDoc(doc(db('alice'), chunkPath), { data: Bytes.fromUint8Array(new Uint8Array([3, 2, 1])) }));
  await assertFails(setDoc(doc(db('alice'), filePath), { ...state, sha256: 'c'.repeat(64) }));
  await assertFails(deleteDoc(doc(db('alice'), chunkPath)));
});

test('rejects oversized bytes, unsupported fields and false completion states', async () => {
  const { Bytes } = await import('firebase/firestore');
  const filePath = `users/alice/plannerFiles/${'a'.repeat(32)}`;
  await assertFails(setDoc(doc(db('alice'), `${filePath}/chunks/0`), { data: Bytes.fromUint8Array(new Uint8Array(524289)) }));
  await assertFails(setDoc(doc(db('alice'), `${filePath}/chunks/160`), { data: Bytes.fromUint8Array(new Uint8Array([1])) }));
  await assertFails(setDoc(doc(db('alice'), `${filePath}/chunks/0`), { data: 'not binary' }));
  await assertFails(setDoc(doc(db('alice'), filePath), { size: 3, sha256: 'b'.repeat(64), chunks: 1, uploaded: 0, complete: true }));
});

test('partial file checkpoints may advance but cannot change identity or go backwards', async () => {
  const ref = doc(db('alice'), `users/alice/plannerFiles/${'c'.repeat(32)}`);
  const partial = { size: 524289, sha256: 'a'.repeat(64), chunks: 2, uploaded: 1, complete: false };
  await assertSucceeds(setDoc(ref, partial));
  await assertFails(setDoc(ref, { ...partial, size: 524290 }));
  await assertFails(setDoc(ref, { ...partial, uploaded: 0 }));
  await assertSucceeds(setDoc(ref, { ...partial, uploaded: 2, complete: true }));
  await assertFails(setDoc(ref, partial));
});
