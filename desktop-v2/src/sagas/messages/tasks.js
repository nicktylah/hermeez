import { all, call, fork, put } from 'redux-saga/effects';
import { differenceInSeconds } from 'date-fns';
import { uniq } from 'lodash';
import { auth, storage } from 'firebase';

import indexedDBClient from '../../lib/indexed-db-client';
import apiClient from '../../lib/api-client';
import {
  ADD_MESSAGES_REQUEST,
  ADD_MESSAGES_SUCCESS,
  ADD_MESSAGES_FAILURE,
  ADD_CONTACTS_REQUEST,
  ADD_CONTACTS_SUCCESS,
  ADD_CONTACTS_FAILURE,
  GET_CHECKPOINT_REQUEST,
  GET_CHECKPOINT_SUCCESS,
  GET_CHECKPOINT_FAILURE,
  GET_MESSAGES_REQUEST,
  GET_MESSAGES_SUCCESS,
  GET_MESSAGES_FAILURE,
  SEND_MESSAGE_SUCCESS,
  SEND_MESSAGE_FAILURE
} from '../../actions/actionTypes';
import { notify } from '../app/tasks';

export function* addContacts({ messages }) {
  let addresses = [];
  messages.forEach(m => {
    // For sms messages, the threadId is the person we're contacting.
    // For mms, the threadId is an opaque identifier and m.addresses
    // is an array of the phone numbers in the thread
    let messageAddresses = [m.threadId];
    if (m.addresses && m.addresses.length > 1) {
      messageAddresses = m.addresses;
    }
    addresses.push(...messageAddresses);
  });
  addresses = uniq(addresses);
  // console.debug(
  //   `Obtained ${addresses.length} unique addresses from messages payload`
  // );

  // Of the incoming messages, figure out which ones have existing
  // contacts in this local DB.
  const getExistingContacts = addresses.map(addr =>
    indexedDBClient().fetchContact(addr)
  );
  const existingContacts = yield call(() => Promise.all(getExistingContacts));
  // console.debug(`Got ${existingContacts.length} existing contacts`);

  const missingAddresses = addresses.filter((_, i) => {
    return !existingContacts[i];
  });

  // console.debug(`Missing contacts: ${missingAddresses}`);

  // For each address that did not return a result in the local DB,
  // perform a lookup to GCP for the associated contact. Store the
  // payload in the local DB for next time.
  if (missingAddresses.length > 0) {
    const user = auth().currentUser;
    if (!user) {
      console.warn('No user logged in, cannot fetch contacts');
      return;
    }

    const ref = storage().ref(`${user.uid}/contacts`);
    const getContactsFromGCP = missingAddresses.map(addr => {
      // console.debug(`Looking up ${addr}...`);
      return ref
        .child(addr.toString())
        .getDownloadURL()
        .then(downloadUrl => {
          return fetch(downloadUrl).then(async res => {
            // console.debug(`Got response from ${addr}:`);
            const contact = await res.json();
            if (contact.phoneNumber) {
              return indexedDBClient().addContact(contact);
            }
          });
        })
        .catch(err => {
          console.warn(`Error handling ${addr}:`, err);
        });
    });
    yield put({ type: ADD_CONTACTS_REQUEST });

    try {
      const payload = yield call(() => Promise.all(getContactsFromGCP));
      const meta = {
        analytics: {}
      };

      yield put({
        type: ADD_CONTACTS_SUCCESS,
        payload,
        meta
      });
    } catch (error) {
      yield put({
        type: ADD_CONTACTS_FAILURE,
        payload: error,
        error: true
      });
    }
  }
}

export function* addMessagesToIndexedDb({ messages, lastId }) {
  const db = async () => indexedDBClient().addMessages(messages, lastId);
  // TODO: Don't store these enormouse base64 images in Indexed DB, but fetch when the thread is loaded?
  // const getAttachmentsAndAddMessages = async () => {
  //   const user = auth().currentUser;
  //   if (!user) {
  //     console.warn('No user logged in, cannot fetch attachment');
  //     return messages;
  //   }

  // messages = await Promise.all(
  //   messages.map(async m => {
  //     if (!m.attachment) {
  //       return m;
  //     }

  //     const ref = storage().ref(`${user.uid}/images`);
  //     // const start = Date.now();
  //     try {
  //       const downloadUrl = await ref.child(m.attachment).getDownloadURL();
  //       const res = await fetch(downloadUrl);
  //       const blob = await res.blob();
  //       if (blob) {
  //         const reader = new FileReader();
  //         return new Promise(resolve => {
  //           reader.readAsDataURL(blob);
  //           reader.onloadend = () => {
  //             const base64data = reader.result;
  //             // console.debug(`Took ${(Date.now() - start)/1000} seconds to download photo`);
  //             resolve({
  //               ...m,
  //               attachmentUrl: base64data
  //             });
  //           };
  //         });
  //       }
  //     } catch (ignored) {
  //       return m;
  //     }
  //   })
  // );

  //   return indexedDBClient().addMessages(messages, lastId);
  // };

  yield put({ type: ADD_MESSAGES_REQUEST });

  try {
    const payload = yield call(db);
    const meta = {
      analytics: {}
    };

    yield put({
      type: ADD_MESSAGES_SUCCESS,
      payload,
      meta
    });
  } catch (error) {
    yield put({
      type: ADD_MESSAGES_FAILURE,
      payload: error,
      error: true
    });
  }
}

export function* notifyForNewMessages({ messages }) {
  const now = new Date();
  console.debug('checking for new messages in payload', messages);
  const messagesToNotify = messages.filter(m => {
    return m.type !== 2 && differenceInSeconds(now, m.date) <= 20;
  });

  console.debug('found messages to notify for', messagesToNotify);

  const notifications = yield call(() =>
    Promise.all(
      messagesToNotify.map(async m => {
        const contact = await indexedDBClient().fetchContact(m.sender);
        const title =
          contact && contact.displayName
            ? `New message from ${contact.displayName}`
            : `New message from ${m.sender}`;
        // TODO: make this a hermes icon by default
        const icon =
          contact && contact.photo
            ? `data:image/jpeg;base64,${contact.photo}`
            : null;

        return {
          title,
          options: {
            body: m.body,
            icon
          }
        };
      })
    )
  );

  yield all(notifications.map(n => notify(n.title, n.options)));
}

export function* fetchMessagesCheckpoint() {
  const db = () => indexedDBClient().fetchCheckpoint();

  yield put({ type: GET_CHECKPOINT_REQUEST });

  try {
    const checkpoint = yield call(db);
    const payload = { checkpoint };
    const meta = {
      analytics: {}
    };

    yield put({
      type: GET_CHECKPOINT_SUCCESS,
      payload,
      meta
    });
  } catch (error) {
    yield put({
      type: GET_CHECKPOINT_FAILURE,
      payload: error,
      error: true
    });
  }
}

export function* fetchMessagesFromIndexedDb({ threadId, limit, offset }) {
  const db = () => indexedDBClient().fetchMessages(threadId, limit, offset);
  const getAttachments = async messages => {
    const user = auth().currentUser;
    if (!user) {
      console.warn('No user logged in, cannot fetch attachment');
      return messages;
    }

    messages = await Promise.all(
      messages.map(async m => {
        if (!m.attachment) {
          return m;
        }

        const ref = storage().ref(`${user.uid}/images`);
        const start = Date.now();
        try {
          const downloadUrl = await ref.child(m.attachment).getDownloadURL();
          const res = await fetch(downloadUrl);
          const blob = await res.blob();
          if (blob) {
            const reader = new FileReader();
            return new Promise(resolve => {
              reader.readAsDataURL(blob);
              reader.onloadend = () => {
                const base64data = reader.result;
                console.debug(
                  `Took ${(Date.now() - start) /
                    1000} seconds to download photo`
                );
                resolve({
                  ...m,
                  attachmentUrl: base64data
                });
              };
            });
          }
        } catch (ignored) {
          return m;
        }
      })
    );

    return messages;
  };

  yield put({ type: GET_MESSAGES_REQUEST });

  try {
    let messages = yield call(db);
    // TODO: Make this an async load of attachments! Uncomment the next line to fetch attachments
    // messages = yield call(getAttachments, messages);
    const payload = { messages, threadId };
    const meta = {
      analytics: {}
    };

    yield put({
      type: GET_MESSAGES_SUCCESS,
      payload,
      meta
    });
  } catch (error) {
    yield put({
      type: GET_MESSAGES_FAILURE,
      payload: error,
      error: true
    });
  }
}

export function* sendMessage({ recipients, attachment, body }) {
  const user = auth().currentUser;
  if (!user) {
    console.warn('No user logged in, cannot fetch contacts');
    return;
  }

  const api = () =>
    apiClient.post('/fcm/send', {
      to: user.uid,
      content: body,
      attachment: attachment,
      attachmentContentType: '',
      // TODO: fix the request validation for this route
      recipientIds: recipients.join(',')
    });

  try {
    const res = yield call(api);
    const payload = { res };
    const meta = {
      analytics: {}
    };

    yield put({
      type: SEND_MESSAGE_SUCCESS,
      payload,
      meta
    });
  } catch (error) {
    yield put({
      type: SEND_MESSAGE_FAILURE,
      payload: error,
      error: true
    });
  }
}
