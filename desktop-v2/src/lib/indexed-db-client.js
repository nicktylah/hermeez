// @flow
import { flatMap, uniq, uniqWith, isEqual } from 'lodash';

import {
  CHECKPOINT_OBJECT_STORE,
  MESSAGES_OBJECT_STORE,
  THREADS_OBJECT_STORE,
  CONTACTS_OBJECT_STORE
} from '../../config/constants';
import { getRandomColor } from './colors';

import type { Contact, IDBDatabase, Message, Thread } from '../types';

class HermesIndexedDBClient {
  dbIdentifier: string;
  dbVersion: number;
  db: IDBDatabase;

  constructor(dbIdentifier: string, dbVersion: number) {
    if (!window.indexedDB) {
      throw new Error('IndexedDB is not supported in this environment');
    }

    this.dbIdentifier = dbIdentifier;
    this.dbVersion = dbVersion;
  }

  initDB() {
    return new Promise((resolve, reject) => {
      const openReq = window.indexedDB.open(this.dbIdentifier, this.dbVersion);

      openReq.onerror = event => reject(event.target.error);
      openReq.onupgradeneeded = HermesIndexedDBClient.initSchema;
      openReq.onsuccess = event => {
        this.db = event.target.result;
        this.db.onerror = HermesIndexedDBClient.handleDBError;
        return resolve(this);
      };
    });
  }

  static initSchema(event: Object) {
    const db = event.target.result;

    // Create an object store for keeping track of the last successfully written checkpoint (timestamp-seq)
    db.createObjectStore(CHECKPOINT_OBJECT_STORE);

    // Create an object store called "threads" with a unique threadId.
    // We'll do a PUT request for threads (upsert).
    const threadStore = db.createObjectStore(THREADS_OBJECT_STORE, {
      keyPath: 'threadId'
    });
    threadStore.createIndex('threadId->date', ['threadId', 'date'], {
      unique: true
    });
    threadStore.createIndex('date', 'date', { unique: false });

    // Create an object store called "messages" with the autoIncrement flag set as true.
    // Also index these objects by threadId-date.
    const messageStore = db.createObjectStore(MESSAGES_OBJECT_STORE, {
      autoIncrement: true
    });
    messageStore.createIndex(
      'threadId->date->sender',
      ['threadId', 'date', 'sender'],
      { unique: true }
    );
    messageStore.createIndex('threadId', 'threadId', { unique: false });

    // Create an object store called "contacts" with a unique phoneNumber.
    // We'll do a PUT request periodically for contacts
    const contactStore = db.createObjectStore(CONTACTS_OBJECT_STORE, {
      keyPath: 'phoneNumber'
    });
    contactStore.createIndex('phoneNumber', 'phoneNumber', { unique: true });
  }

  async addMessages(messages: Array<Message>, checkpoint: string): Promise<*> {
    if (messages.length > 1) {
      messages = uniqWith(messages, isEqual);
      messages.sort((a: Message, b: Message) => {
        return a.date - b.date;
      });
    }

    const txPromises = messages.map(async m => {
      return new Promise((resolve, reject) => {
        const tx = this.db.transaction(
          [THREADS_OBJECT_STORE, MESSAGES_OBJECT_STORE],
          'readwrite'
        );

        tx.oncomplete = () => resolve(m);
        tx.onerror = event => {
          const error = event.target.error;
          event.cancelBubble = true;

          // Expect duplicates to fail insertion
          if (error.name === 'ConstraintError') {
            return resolve();
          }

          return reject(error);
        };

        const txThreadStore = tx.objectStore(THREADS_OBJECT_STORE);
        const txMessageStore = tx.objectStore(MESSAGES_OBJECT_STORE);

        const threadObj = {
          // TODO: Fix this app-side. Why do we say the address is our own phone number?
          addresses: m.addresses,
          attachment: m.attachment,
          body: m.body,
          date: new Date(m.date),
          sender: m.sender,
          threadId: m.threadId
        };

        txMessageStore.put(m);
        txThreadStore.put(threadObj);
      });
    });

    const txResults = await Promise.all(txPromises);

    // Make sure that this is a newer batch of messages before we overwrite the checkpoint
    const existingCheckpoint = await this.fetchCheckpoint();
    console.debug(`Existing: ${existingCheckpoint}, current: ${checkpoint}`);
    if (checkpoint < existingCheckpoint) {
      console.debug('trying to set an older checkpoint, aborting');
      return;
    }
    return new Promise((resolve, reject) => {
      // Update the checkpoint into the messages stream in our local DB.
      // Note that the previous inserts must all succeed before we update the checkpoint,
      // but the transactions are not tied together.
      const checkpointTx = this.db.transaction(
        CHECKPOINT_OBJECT_STORE,
        'readwrite'
      );

      checkpointTx.oncomplete = () => resolve(checkpoint);
      checkpointTx.onerror = event => {
        event.cancelBubble = true;
        return reject(event.target.error);
      };

      checkpointTx
        .objectStore(CHECKPOINT_OBJECT_STORE)
        .put(checkpoint, 'checkpoint');
    }).then(insertedCheckpoint => {
      return {
        messages: txResults.filter(res => res),
        checkpoint: insertedCheckpoint
      };
    });
  }

  addContact(contact: Contact): Promise<*> {
    return new Promise((resolve, reject) => {
      const tx = this.db.transaction(CONTACTS_OBJECT_STORE, 'readwrite');
      tx.oncomplete = () => resolve(contact);
      tx.onerror = event => {
        const error = event.target.error;
        event.cancelBubble = true;

        // This contact already exists
        if (error.name === 'ConstraintError') {
          return resolve(contact);
        }

        return reject(error);
      };

      const txContactStore = tx.objectStore(CONTACTS_OBJECT_STORE);
      const contactObject = {
        color: getRandomColor(),
        ...contact,
        lastUpdated: new Date()
      };

      txContactStore.put(contactObject);
    });
  }

  fetchContact(phoneNumber: number): Promise<?Contact> {
    return new Promise((resolve, reject) => {
      const cursorReq = this.db
        .transaction(CONTACTS_OBJECT_STORE)
        .objectStore(CONTACTS_OBJECT_STORE)
        .index('phoneNumber')
        .openCursor(window.IDBKeyRange.only(phoneNumber), 'prev');

      cursorReq.onerror = event => reject(event.target.error);
      cursorReq.onsuccess = event => {
        const cursor = event.target.result;
        if (!cursor) {
          return resolve();
        }

        return resolve(cursor.value);
      };
    });
  }

  fetchMessages(
    threadId: number,
    limit: number,
    offset: number
  ): Promise<Array<Message>> {
    const start = Date.now();
    return new Promise((resolve, reject) => {
      let i = 0;
      const messages = [];

      const threadIdRange = window.IDBKeyRange.only(threadId);
      const cursorReq = this.db
        .transaction(MESSAGES_OBJECT_STORE)
        .objectStore(MESSAGES_OBJECT_STORE)
        .index('threadId')
        .openCursor(threadIdRange, 'prev');

      cursorReq.onerror = event => reject(event.target.error);
      cursorReq.onsuccess = event => {
        const cursor = event.target.result;
        if (!cursor) {
          console.debug(
            `Took ${Date.now() - start} ms to fetch ${messages.length} messages`
          );
          return resolve(messages);
        }
        if (Number.isInteger(offset) && i < offset) {
          return cursor.continue();
        } else if (i >= limit) {
          console.debug(
            `Took ${Date.now() - start} ms to fetch ${messages.length} messages`
          );
          return resolve(messages);
        }

        messages.push(cursor.value);
        i++;

        cursor.continue();
      };
    });
  }

  fetchThreads(opts: { limit: ?number, threadId: ?number }): Promise<Thread[]> {
    const limit = opts.limit || null;
    const threads = [];
    return new Promise((resolve, reject) => {
      let i = 0;
      const cursorReq = this.db
        .transaction(THREADS_OBJECT_STORE)
        .objectStore(THREADS_OBJECT_STORE)
        .index('date')
        .openCursor(null, 'prev');

      cursorReq.onerror = event => reject(event.target.error);
      cursorReq.onsuccess = event => {
        const cursor = event.target.result;
        if (!cursor) {
          return resolve(threads);
        }
        if (limit && i >= limit) {
          return resolve(threads);
        }
        if (Number.isInteger(opts.threadId)) {
          if (cursor.value.threadId === opts.threadId) {
            return resolve([cursor.value]);
          }
        }

        threads.push(cursor.value);
        i++;

        cursor.continue();
      };
    }).then(async () => {
      // Fetch all the contacts associated with these threads from
      // the local DB if they exist. If they don't, leave them as
      // simple { phoneNumber: number } objects
      const addresses = uniq(flatMap(threads, t => t.addresses));
      const contacts = {};
      await Promise.all(
        addresses.map(async addr => {
          const contact = await this.fetchContact(addr);
          contacts[addr] = contact || { phoneNumber: addr };
        })
      );

      return threads.map(t => {
        return {
          ...t,
          contacts: t.addresses.map(addr => contacts[addr])
        };
      });
    });
  }

  // searchThreads returns an array of threads that match the given
  // query. Matches are based on presence of a contact name or the
  // thread name itself
  searchThreads(query: string): Promise<Thread[]> {
    return Promise.resolve([]);
  }

  // fetchCheckpoint returns the database's idea of what our messages
  // stream checkpoint is. Returns "0-0" (no checkpoint) if none is found.
  fetchCheckpoint(): Promise<string> {
    return new Promise((resolve, reject) => {
      const tx = this.db
        .transaction(CHECKPOINT_OBJECT_STORE)
        .objectStore(CHECKPOINT_OBJECT_STORE)
        .get('checkpoint');
      tx.onerror = event => reject(event.target.error);
      tx.onsuccess = event => resolve(event.target.result || '0-0');
    });
  }

  // TESTING/DEBUGGING
  testAddData() {
    const tx = this.db.transaction(
      [THREADS_OBJECT_STORE, MESSAGES_OBJECT_STORE],
      'readwrite'
    );

    // Do something when all the data is added to the database.
    tx.oncomplete = function(event) {
      console.log('All done!', event);
    };

    tx.onerror = function(event) {
      console.error('transaction error,', event);
    };

    const messages = [
      {
        body: 'Mms to myself1',
        contentType: 'text/plain',
        date: '2017-12-25T21:38:37Z',
        sender: 0,
        threadId: 0,
        type: 2
      },
      {
        body: 'Mms to myself2',
        contentType: 'text/plain',
        date: '2017-12-25T21:39:37Z',
        sender: 0,
        threadId: 0,
        type: 1
      }
    ];

    const txThreadStore = tx.objectStore(THREADS_OBJECT_STORE);
    const txMessageStore = tx.objectStore(MESSAGES_OBJECT_STORE);

    messages.forEach(message => {
      const date = new Date(message.date).valueOf();
      const threadAddReq = txThreadStore.put({
        date,
        threadId: message.threadId,
        body: message.body,
        sender: message.sender
      });
      const messageAddReq = txMessageStore.add({
        ...message,
        date
      });

      threadAddReq.onsuccess = function(event) {
        console.log('threadAdd successful!', event);
      };
      messageAddReq.onsuccess = function(event) {
        console.log('messageAdd successful!', event);
      };

      threadAddReq.onerror = function(event) {
        console.log('threadAdd error', event);
      };
      messageAddReq.onerror = function(event) {
        console.log('messageAdd error', event);
      };
    });
  }

  testGetNumItems(requestedStore: string): Promise<void> {
    let count = 0;
    const numToShow = 5;
    const toShow = [];

    return new Promise((resolve, reject) => {
      let store;
      switch (requestedStore) {
        case 'messages':
          store = MESSAGES_OBJECT_STORE;
          break;
        case 'threads':
          store = THREADS_OBJECT_STORE;
          break;
        case 'contacts':
          store = CONTACTS_OBJECT_STORE;
          break;
        default:
          reject(new Error('provide a valid store (messages, threads)'));
      }
      this.db
        .transaction(store)
        .objectStore(store)
        .openCursor().onsuccess = event => {
        const cursor = event.target.result;
        if (cursor) {
          count++;
          toShow.push(cursor.value);
          cursor.continue();
        } else {
          return resolve(count);
        }
      };
    })
      .then(count => {
        console.debug(
          `There are ${count} items in the ${requestedStore} store.`
        );
        toShow.sort((a, b) => a.date - b.date);
        console.debug(toShow.slice(0, 10));
      })
      .catch(err => console.error('Error getting numItems', err));
  }

  testGetNumMessagesInThread(threadId: number): Promise<void> {
    let count = 0;
    const messages = [];

    return new Promise((resolve, reject) => {
      const threadIdRange = window.IDBKeyRange.only(threadId);
      const cursorReq = this.db
        .transaction(MESSAGES_OBJECT_STORE)
        .objectStore(MESSAGES_OBJECT_STORE)
        .index('threadId')
        .openCursor(threadIdRange, 'prev');

      cursorReq.onerror = event => reject(event.target.error);
      cursorReq.onsuccess = event => {
        const cursor = event.target.result;
        if (!cursor) {
          return resolve(messages);
        }

        count++;
        messages.push(cursor.value);
        cursor.continue();
      };
    })
      .then(messages => {
        console.debug(`There are ${count} messages for threadId ${threadId}`);
        console.debug(messages);
      })
      .catch(err => console.error('Error getting messages', err));
  }
  // END TESTING/DEBUGGING

  static handleDBError(event: DOMError) {
    console.error('Unhandled database error:', event);
  }
}

let hermesIndexedDBClient;

export default function(dbIdentifier: ?string, dbVersion: ?number): * {
  if (!hermesIndexedDBClient) {
    if (!dbIdentifier || !dbVersion)
      return Promise.reject('DB identifier or version not provided');

    hermesIndexedDBClient = new HermesIndexedDBClient(dbIdentifier, dbVersion);
    return hermesIndexedDBClient.initDB();
  }

  return hermesIndexedDBClient;
}
