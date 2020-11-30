import { call, debounce, fork, put, select, take } from 'redux-saga/effects';

import {
  GET_MESSAGES,
  MESSAGES_ERROR,
  MESSAGES_OPEN,
  MESSAGES_SENT,
  SEND_MESSAGE_REQUEST,
  SELECT_THREAD
} from '../../actions/actionTypes';
import { addNotification } from '../../actions/app';
import {
  addContacts,
  addMessagesToIndexedDb,
  fetchMessagesFromIndexedDb,
  sendMessage,
  notifyForNewMessages
} from './tasks';
import { fetchThreads } from '../threads/tasks';
import { DEFAULT_MESSAGES_LIMIT } from '../../../config/constants';

export function* watchFetchMessages(action) {
  if (!action) return;

  // If the messages for this thread are already showing (state.messages.threadId is the threadId whose messages are displayed),
  // abort.
  const state = yield select();
  if (action.payload.threadId === state.messages.threadId) return;

  yield call(fetchMessagesFromIndexedDb, {
    threadId: action && action.payload.threadId,
    limit: DEFAULT_MESSAGES_LIMIT,
    offset: 0
  });
}

export function* debounceFetchMessages() {
  yield debounce(250, SELECT_THREAD, watchFetchMessages);
}

export function* watchMessagesSent() {
  while (true) {
    const { payload } = yield take(MESSAGES_SENT);

    // For incoming messages, obtain and add full contact payloads to local
    // DB if they don't already exist. Also retrieve and store attachments
    // (photos, videos) from GCP if necessary.
    yield call(addContacts, payload);
    yield call(addMessagesToIndexedDb, payload);
    yield call(notifyForNewMessages, payload);

    // Refresh the currently showing threads
    yield call(fetchThreads);
  }
}

export function* watchSendMessage() {
  while (true) {
    const { payload } = yield take(SEND_MESSAGE_REQUEST);
    yield fork(sendMessage, payload);
  }
}

export function* watchSubscribeMessages() {
  while (true) {
    const action = yield take([MESSAGES_OPEN, MESSAGES_ERROR]);
    switch (action.type) {
      case MESSAGES_OPEN:
        yield put(
          addNotification({ id: 1, message: 'EventStream subscribed' })
        );
        break;
      case MESSAGES_ERROR:
        yield put(
          addNotification({
            id: 2,
            message: 'EventStream error',
            type: 'error'
          })
        );
        break;
    }
  }
}
