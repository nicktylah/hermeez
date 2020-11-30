// @flow
import Fuse from 'fuse.js';
import {
  ADD_MESSAGES_SUCCESS,
  GET_MESSAGES_SUCCESS,
  GET_THREADS_SUCCESS,
  SEARCH_THREADS,
  SELECT_THREAD
} from '../actions/actionTypes';

import type { Action, Contact, Thread } from '../types';

export type State = {
  error: boolean,
  contacts: Contact[],
  contactsByPhoneNumber: { [phoneNumber: number]: Contact },
  threads: Thread[],
  threadId: number
};

let threadsFuse;
const fuseOptions = {
  includeMatches: true,
  shouldSort: false, // Use the default chronological ordering
  threshold: 0.3,
  location: 0,
  distance: 100,
  maxPatternLength: 12,
  minMatchCharLength: 3,
  keys: ['contacts.phoneNumber', 'contacts.displayName', 'name']
};

const initialState = {
  error: false,
  contacts: [],
  contactsByPhoneNumber: {},
  threads: [],
  threadId: 0
};

export default function(state: State = initialState, action: Action) {
  let payload = action.payload || {};

  switch (action.type) {
    case SEARCH_THREADS:
      if (!threadsFuse) {
        console.warn('Unsearchable state');
        return state;
      }
      return {
        ...state,
        threads: threadsFuse.search(payload.query).map(match => match.item)
      };

    case GET_THREADS_SUCCESS:
      if (!threadsFuse) {
        threadsFuse = new Fuse(payload.threads, fuseOptions);
      }
      return {
        ...state,
        threads: payload.threads
      };

    case SELECT_THREAD:
      return {
        ...state,
        threadId: payload.threadId
      };

    case GET_MESSAGES_SUCCESS:
      const selectedThread = state.threads.find(
        t => t.threadId === payload.threadId
      );
      let contacts = [];
      let contactsByPhoneNumber = {};
      if (selectedThread) {
        contacts = selectedThread.contacts;
        contacts.forEach(c => (contactsByPhoneNumber[c.phoneNumber] = c));
      }
      return {
        ...state,
        contacts,
        contactsByPhoneNumber
        // threadId: payload.threadId
      };

    // Update the displaying threads on new message arrival
    case ADD_MESSAGES_SUCCESS:
      const threads = state.threads.slice();
      payload.messages.forEach(message => {
        const thread = threads.find(
          thread => thread.threadId === message.threadId
        );
        if (thread && message.date > thread.date) {
          thread.body = message.body;
          thread.date = message.date;
          thread.sender = message.sender;
        }
      });
      return {
        ...state,
        threads
      };

    default:
      return state;
  }
}
